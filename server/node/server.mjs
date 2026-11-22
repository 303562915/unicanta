// Self-hosted telemetry backend for the Canta fork: no Supabase needed.
//
// Speaks the same wire contract as the app (composeApp util/Telemetry.kt):
//
//   POST /rest/v1/rpc/challenge          {}                       -> {challenge, sig}
//   POST /rest/v1/rpc/record             {payload, challenge, sig, x} -> {ok, accepted}
//   POST /rest/v1/rpc/package_scores     {}                       -> [rows]
//   POST /rest/v1/rpc/package_scores_for {p_rom, p_rom_version}   -> [rows]
//   POST /rest/v1/rpc/operation_failure  {payload}                -> {ok}
//   POST /rest/v1/rpc/failure_summary    {}                       -> [rows]
//
// Storage is a single SQLite file (node:sqlite, no native modules). Proof of
// work is the same one the client solves (util/Pow.kt):
//
//   prefix = challenge || ':' || hex(sha256(payload)) || ':' || x
//   accept when the first 8 bytes of sha256(prefix), big-endian, are below
//   floor(2^64 / (10000 * number_of_events))
//
// Run:  node server.mjs           (PORT, DB_PATH, RETENTION_DAYS, POW_K)
import { createServer } from 'node:http'
import { DatabaseSync } from 'node:sqlite'
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'

const PORT = Number(process.env.PORT ?? 8787)
const DB_PATH = process.env.DB_PATH ?? new URL('./telemetry.db', import.meta.url).pathname
const RETENTION_DAYS = Number(process.env.RETENTION_DAYS ?? 35)
const POW_K = Number(process.env.POW_K ?? 10000)
const MAX_EVENTS = 250
const CHALLENGE_TTL_MS = 10 * 60 * 1000

const KINDS = new Set(['uninstalled', 'survived_reboot', 'reinstalled', 'voided', 'restored'])
const BUCKETS = new Set(['1', '2-10', '11+'])
/** The operations a failure report can be about. */
const FAILURE_KINDS = new Set(['uninstall', 'reinstall', 'disable', 'enable'])

const db = new DatabaseSync(DB_PATH)
db.exec(`
  create table if not exists challenges (
    challenge text primary key,
    sig text not null,
    created_at integer not null,
    used_at integer
  );
  create table if not exists events (
    id integer primary key autoincrement,
    received_at integer not null,
    platform text not null,
    pkg text not null,
    kind text not null,
    cohort_week text not null,
    batch_bucket text not null,
    brand text not null,
    rom text not null,
    rom_version integer not null,
    sdk integer not null
  );
  create index if not exists events_pkg on events (pkg);
  create index if not exists events_pkg_rom on events (pkg, rom, rom_version);
  create index if not exists events_received on events (received_at);
  create table if not exists failures (
    id integer primary key autoincrement,
    received_at integer not null,
    platform text not null,
    kind text not null,
    pkg text not null,
    status integer not null,
    message text not null,
    reason text not null,
    advice text not null,
    system_app integer not null,
    batch_index integer not null,
    batch_size integer not null,
    android_user integer not null,
    app_version text not null,
    canta_version text not null,
    brand text not null,
    rom text not null,
    rom_version integer not null,
    sdk integer not null,
    model text not null,
    codename text not null
  );
  create index if not exists failures_pkg on failures (pkg);
  create index if not exists failures_received on failures (received_at);
`)

const sha256hex = (buf) => createHash('sha256').update(buf).digest('hex')
const head64 = (buf) => buf.readBigUInt64BE(0)

/** Verdict thresholds; see server/README.md. Deliberately conservative. */
function verdictOf(uninstalls, survived, reinstalled, restored) {
  const u = Math.max(uninstalls, 0)
  if (u < 5) return 'INSUFFICIENT'
  const back = ((reinstalled + restored) * 100) / u
  const rescue = (restored * 100) / u
  const kept = (survived * 100) / u
  if (rescue >= 15) return 'BOOTLOOP'
  if (back >= 30) return 'BOOTLOOP'
  if (back >= 10) return 'BREAKS'
  if (kept >= 50 && back <= 2) return 'SAFE'
  return 'UNCLEAR'
}

function newChallenge() {
  const challenge = randomBytes(32).toString('hex')
  const sig = randomBytes(16).toString('hex')
  const now = Date.now()
  db.prepare('delete from challenges where created_at < ?').run(now - 60 * 60 * 1000)
  db.prepare('insert into challenges (challenge, sig, created_at) values (?, ?, ?)').run(challenge, sig, now)
  return { challenge, sig }
}

function powTarget(nEvents) {
  return (2n ** 64n) / (BigInt(POW_K) * BigInt(nEvents))
}

/** Returns null when the batch is acceptable, or the reason it is not. */
function checkRecord({ payload, challenge, sig, x }) {
  if (typeof payload !== 'string' || typeof challenge !== 'string' || typeof sig !== 'string' || x === undefined) {
    return 'missing argument'
  }
  const row = db.prepare('select sig, created_at, used_at from challenges where challenge = ?').get(challenge)
  if (!row) return 'invalid challenge'
  const given = Buffer.from(String(sig))
  const expected = Buffer.from(row.sig)
  if (given.length !== expected.length || !timingSafeEqual(given, expected)) return 'invalid challenge'
  if (row.used_at) return 'invalid challenge'
  if (Date.now() - row.created_at > CHALLENGE_TTL_MS) return 'invalid challenge'

  let body
  try {
    body = JSON.parse(payload)
  } catch {
    return 'invalid payload'
  }
  if (body.v !== 1) return 'unsupported payload version'
  const events = Array.isArray(body.events) ? body.events : []
  if (events.length < 1 || events.length > MAX_EVENTS) return `bad event count: ${events.length}`

  const prefix = `${challenge}:${sha256hex(Buffer.from(payload, 'utf8'))}:${BigInt(x)}`
  const digest = createHash('sha256').update(prefix, 'utf8').digest()
  if (head64(digest) >= powTarget(events.length)) return 'proof of work failed'

  for (const e of events) {
    if (typeof e?.p !== 'string' || !e.p) return 'invalid event value'
    if (!KINDS.has(e.e)) return 'invalid event value'
    if (!BUCKETS.has(e.b)) return 'invalid event value'
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(e.w))) return 'invalid event value'
  }
  return null
}

function storeRecord({ payload, challenge }) {
  const body = JSON.parse(payload)
  const device = body.device ?? {}
  const insert = db.prepare(`
    insert into events (received_at, platform, pkg, kind, cohort_week, batch_bucket, brand, rom, rom_version, sdk)
    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `)
  const now = Date.now()
  let accepted = 0
  db.exec('begin')
  try {
    for (const e of body.events) {
      insert.run(
        now,
        String(body.platform ?? 'unknown'),
        e.p,
        e.e,
        String(e.w),
        e.b,
        String(device.brand ?? 'unknown'),
        String(device.rom ?? 'unknown'),
        Number(device.romVersion ?? 0),
        Number(device.sdk ?? 0),
      )
      accepted++
    }
    db.prepare('update challenges set used_at = ? where challenge = ?').run(now, challenge)
    db.exec('commit')
  } catch (err) {
    db.exec('rollback')
    throw err
  }
  return accepted
}

function aggregate(where = '', params = []) {
  const rows = db
    .prepare(`
      select pkg,
             sum(case when kind = 'uninstalled' then 1 else 0 end)                as uninstalls,
             sum(case when kind = 'survived_reboot' then 1 else 0 end)            as survived,
             sum(case when kind = 'reinstalled' then 1 else 0 end)                as reinstalled,
             sum(case when kind = 'restored' then 1 else 0 end)                   as restored,
             sum(case when kind = 'uninstalled' and batch_bucket != '11+' then 1 else 0 end) as small_batch,
             max(received_at)                                                     as updated_at
      from events ${where}
      group by pkg
    `)
    .all(...params)
  return rows
}

function packageScores() {
  const rows = aggregate()
  const mainRom = db.prepare(`
    select rom from events where pkg = ?
    group by rom order by sum(case when kind = 'uninstalled' then 1 else 0 end) desc, rom limit 1
  `)
  return rows.map((r) => ({
    pkg: r.pkg,
    uninstalls: r.uninstalls,
    small_batch: r.small_batch * 2 >= r.uninstalls ? 1 : 0,
    verdict: verdictOf(r.uninstalls, r.survived, r.reinstalled, r.restored),
    updated_at: new Date(Number(r.updated_at)).toISOString(),
    main_rom: mainRom.get(r.pkg)?.rom ?? '',
  }))
}

function packageScoresFor(rom, romVersion) {
  const all = Number(romVersion) === -1
  const rows = all
    ? aggregate('where rom = ?', [String(rom)])
    : aggregate('where rom = ? and rom_version = ?', [String(rom), Number(romVersion)])
  return rows.map((r) => ({
    pkg: r.pkg,
    uninstalls: r.uninstalls,
    small_batch: r.small_batch * 2 >= r.uninstalls ? 1 : 0,
    verdict: verdictOf(r.uninstalls, r.survived, r.reinstalled, r.restored),
    updated_at: new Date(Number(r.updated_at)).toISOString(),
  }))
}

/** Raw events are only kept for the retention window; the scores are computed from them. */
export function purge(keepDays = RETENTION_DAYS) {
  const cutoff = Date.now() - keepDays * 24 * 60 * 60 * 1000
  const events = db.prepare('delete from events where received_at < ?').run(cutoff).changes
  const challenges = db.prepare('delete from challenges where created_at < ?').run(cutoff).changes
  const failures = db.prepare('delete from failures where received_at < ?').run(cutoff).changes
  return { events: Number(events), challenges: Number(challenges), failures: Number(failures) }
}

/**
 * Why a device refused an operation (client: util/FailureReport.kt). One row per refused package;
 * the shape is checked the same way the Postgres RPC does it.
 */
function storeFailure({ payload }) {
  if (typeof payload !== 'string' || !payload) throw new Error('missing payload')
  if (payload.length > 20000) throw new Error('payload too large')
  const body = JSON.parse(payload)
  if (body.v !== 1) throw new Error('unsupported payload version')
  if (!FAILURE_KINDS.has(body.kind)) throw new Error(`bad kind: ${body.kind}`)
  if (typeof body.pkg !== 'string' || !body.pkg || body.pkg.length > 200) throw new Error('bad package name')
  const recent = db
    .prepare('select count(*) as n from failures where pkg = ? and received_at > ?')
    .get(body.pkg, Date.now() - 60 * 60 * 1000)
  if (Number(recent.n) >= 60) throw new Error('too many reports for this package')
  const text = (value, max) => String(value ?? '').slice(0, max)
  db.prepare(
    `insert into failures (
       received_at, platform, kind, pkg, status, message, reason, advice, system_app,
       batch_index, batch_size, android_user, app_version, canta_version,
       brand, rom, rom_version, sdk, model, codename
     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(
    Date.now(),
    text(body.platform, 40) || 'unknown',
    body.kind,
    body.pkg,
    Number(body.status ?? 0),
    text(body.message, 400),
    text(body.reason, 400),
    text(body.advice, 400),
    body.system ? 1 : 0,
    Number(body.batchIndex ?? 0),
    Number(body.batchSize ?? 0),
    Number(body.user ?? 0),
    text(body.appVersion, 80),
    text(body.cantaVersion, 40),
    text(body.device?.brand, 40) || 'unknown',
    text(body.device?.rom, 40) || 'unknown',
    Number(body.device?.romVersion ?? 0),
    Number(body.device?.sdk ?? 0),
    text(body.device?.model, 80),
    text(body.device?.codename, 80),
  )
  return { ok: true }
}

/** What fails where, for reading: `select * from failure_summary order by refusals desc;`. */
function failureSummary() {
  return db
    .prepare(
      `select kind, pkg, model, rom, rom_version, sdk,
              count(*) as refusals,
              min(received_at) as first_seen,
              max(received_at) as last_seen
         from failures
        group by kind, pkg, model, rom, rom_version, sdk
        order by refusals desc`
    )
    .all()
    .map((row) => ({
      ...row,
      first_seen: new Date(Number(row.first_seen)).toISOString(),
      last_seen: new Date(Number(row.last_seen)).toISOString(),
    }))
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = ''
    req.setEncoding('utf8')
    req.on('data', (chunk) => {
      data += chunk
      if (data.length > 4_000_000) reject(new Error('body too large'))
    })
    req.on('end', () => {
      if (!data.trim()) return resolve({})
      try {
        resolve(JSON.parse(data))
      } catch (e) {
        reject(e)
      }
    })
    req.on('error', reject)
  })
}

function send(res, status, body) {
  const text = JSON.stringify(body)
  res.writeHead(status, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(text) })
  res.end(text)
}

const routes = {
  challenge: () => newChallenge(),
  record: (body) => {
    const problem = checkRecord(body ?? {})
    if (problem) {
      const status = problem === 'invalid challenge' || problem === 'proof of work failed' ? 401 : 400
      return [status, { message: problem }]
    }
    return [200, { ok: true, accepted: storeRecord(body) }]
  },
  package_scores: () => packageScores(),
  package_scores_for: (body) => packageScoresFor(body?.p_rom ?? '', body?.p_rom_version ?? -1),
  operation_failure: (body) => storeFailure(body ?? {}),
  failure_summary: () => failureSummary(),
}

export const server = createServer(async (req, res) => {
  const match = /^\/rest\/v1\/rpc\/([a-z_]+)$/.exec(req.url ?? '')
  if (req.method !== 'POST' || !match || !(match[1] in routes)) return send(res, 404, { message: 'not found' })
  const name = match[1]
  try {
    const body = await readBody(req)
    const result = routes[name](body)
    if (Array.isArray(result) && typeof result[0] === 'number') return send(res, result[0], result[1])
    return send(res, 200, result)
  } catch (e) {
    return send(res, 400, { message: String(e?.message ?? e) })
  }
})

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop())) {
  purge()
  server.listen(PORT, () => console.log(`telemetry backend on http://127.0.0.1:${PORT}/rest/v1/rpc/ (db: ${DB_PATH})`))
}

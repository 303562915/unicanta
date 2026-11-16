// End-to-end check of server/node/server.mjs: real HTTP, real proof of work.
//
//   node test/e2e.test.mjs
//
// Uses a scratch database and port, so nothing of a running instance is touched.
import { createServer } from 'node:http'
import { createHash } from 'node:crypto'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { rmSync } from 'node:fs'

const PORT = Number(process.env.TEST_PORT ?? 8899)
const DB = '/tmp/canta-telemetry-e2e.db'
// The module is imported further down for purge(); make it use the scratch database too.
process.env.DB_PATH = DB
const BASE = `http://127.0.0.1:${PORT}/rest/v1/rpc/`
const sha256hex = (s) => createHash('sha256').update(s, 'utf8').digest('hex')

const child = spawn(process.execPath, [fileURLToPath(new URL('../server.mjs', import.meta.url))], {
  env: { ...process.env, PORT: String(PORT), DB_PATH: DB },
  stdio: ['ignore', 'pipe', 'inherit'],
})

/** Waits until the server accepts connections. */
async function waitReady() {
  for (let i = 0; i < 100; i++) {
    try {
      await fetch(BASE + 'challenge', { method: 'POST', body: '{}' })
      return
    } catch {
      await new Promise((r) => setTimeout(r, 100))
    }
  }
  throw new Error('server did not start')
}

const solve = (challenge, payload, n) => {
  const prefix = `${challenge}:${sha256hex(payload)}:`
  const target = (2n ** 64n) / (10000n * BigInt(n))
  for (let x = 0n; ; x++) {
    if (createHash('sha256').update(prefix + x, 'utf8').digest().readBigUInt64BE(0) < target) return x
  }
}

const device = { brand: 'xiaomi', rom: 'hyperos', romVersion: 2, sdk: 35 }
const ev = (p, e, b = '2-10') => ({ e, p, w: '2026-11-09', b })

async function rpc(name, body) {
  const res = await fetch(BASE + name, { method: 'POST', body: JSON.stringify(body ?? {}) })
  return { status: res.status, json: await res.json() }
}

async function send(events, { tamper = false, reuse = null } = {}) {
  const ch = reuse ?? (await rpc('challenge')).json
  const payload = JSON.stringify({ v: 1, platform: 'android', device, events })
  const x = solve(ch.challenge, payload, events.length)
  const body = tamper ? payload.replace('com.', 'com0') : payload
  const res = await rpc('record', { payload: body, challenge: ch.challenge, sig: ch.sig, x: Number(x) })
  return { ...res, challenge: ch }
}

const results = []
const check = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`)
}

try {
  await waitReady()

  // 1. happy path
  const first = await send([ev('com.safe', 'uninstalled')])
  check('batch accepted', first.status === 200 && first.json.accepted === 1, JSON.stringify(first.json))

  // 2. challenge reuse
  const again = await send([ev('com.safe', 'uninstalled')], { reuse: first.challenge })
  check('replayed challenge refused', again.status === 401, JSON.stringify(again.json))

  // 3. tampered payload
  const tampered = await send([ev('com.tampered', 'uninstalled')], { tamper: true })
  check('tampered payload refused', tampered.status === 401, JSON.stringify(tampered.json))

  // 4. wrong event kind
  const bad = await send([ev('com.bad', 'nonsense')])
  check('bad event kind refused', bad.status === 400, JSON.stringify(bad.json))

  // 5. replay via an old signature but a fresh challenge is refused too
  const fresh = (await rpc('challenge')).json
  const stale = await rpc('record', {
    payload: JSON.stringify({ v: 1, platform: 'android', device, events: [ev('com.x', 'uninstalled')] }),
    challenge: first.challenge.challenge,
    sig: fresh.sig,
    x: 0,
  })
  check('foreign sig refused', stale.status === 401, JSON.stringify(stale.json))

  // 6. verdicts for all five cases
  const feeds = {
    'com.safe': ['uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'survived_reboot', 'survived_reboot', 'survived_reboot', 'survived_reboot'],
    'com.bootloop': ['uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'restored', 'restored'],
    'com.breaks': ['uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'survived_reboot', 'reinstalled'],
    'com.unclear': ['uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled', 'uninstalled'],
    'com.few': ['uninstalled', 'survived_reboot'],
  }
  for (const [pkg, kinds] of Object.entries(feeds)) {
    const res = await send(kinds.map((k) => ev(pkg, k)))
    if (res.status !== 200) check(`feed ${pkg}`, false, JSON.stringify(res.json))
  }

  const scores = (await rpc('package_scores')).json
  const want = {
    'com.safe': 'SAFE', 'com.bootloop': 'BOOTLOOP', 'com.breaks': 'BREAKS',
    'com.unclear': 'UNCLEAR', 'com.few': 'INSUFFICIENT',
  }
  for (const [pkg, verdict] of Object.entries(want)) {
    const row = scores.find((r) => r.pkg === pkg)
    check(`verdict ${pkg} = ${verdict}`, row?.verdict === verdict, row ? `${row.verdict} (u=${row.uninstalls})` : 'missing')
  }

  // 7. ROM-scoped and family-scoped reads
  const byVersion = (await rpc('package_scores_for', { p_rom: 'hyperos', p_rom_version: 2 })).json
  check('rom version rows', byVersion.length === scores.length, `${byVersion.length} rows`)
  const family = (await rpc('package_scores_for', { p_rom: 'hyperos', p_rom_version: -1 })).json
  check('rom family rows', family.length === scores.length, `${family.length} rows`)
  const other = (await rpc('package_scores_for', { p_rom: 'oneui', p_rom_version: -1 })).json
  check('unknown rom is empty', Array.isArray(other) && other.length === 0, JSON.stringify(other))

  // 8. retention
  const { purge } = await import(fileURLToPath(new URL('../server.mjs', import.meta.url)))
  const removed = purge(0)
  check('purge drops raw events', removed.events > 0, JSON.stringify(removed))
} finally {
  child.kill()
  rmSync(DB, { force: true })
}

const failed = results.filter((ok) => !ok).length
console.log(failed === 0 ? '\nALL PASS' : `\n${failed} FAILURES`)
process.exit(failed === 0 ? 0 : 1)

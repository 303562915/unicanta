-- 赞助榜（贡献值那一栏）：谁赞助了、赞助了多少钱（或一份 AI 邀请）、什么时候。
--
-- 用途一句话：所有赞助都进同一本账，写清这笔钱去了哪儿——**服务器建设**和 **token 采购**，
-- 为的是让这个软件的开发能一直转下去。
--
-- 为什么是"登记 + 展示"而不是在线支付：钱是在群/私聊里收的（微信、支付宝、爱发电都有人用），
-- 客户端能做的只有两件事——把钱记成一笔（`sponsor_add`），把榜单画出来（`sponsor_wall`）。
-- 在线支付那一层要接商户号、要实名、要对账，不是这个文件该做的事。
--
--   * `sponsor_entries` 一行 = 一位赞助者的一笔记录。`amount_yuan` 是金额（元），
--     `kind` 是赞助形式（`money` 打赏金额 / `ai` AI 邀请 / `other` 其它）。
--     `ai` 这一种是"邀请一个人来用"，金额按 0 记，不计入总额。
--   * `sponsor_add` 幂等：同一个身份 + 同一种形式 + 同一个金额，24 小时内只记一次，
--     所以"补录同一笔"和"手滑连点两次"都不会把榜刷长。要改一笔金额：先 `sponsor_hide` 它。
--   * 身份只认 `private.identity_of(p_token)`；认不出来时退回客户端自报的号码（与 `airport_add` /
--     `plan_submit` 那一批写函数同一个写法）。认不出身份、也没有署名 → 不记。
--   * 头像不在库里：图床的对象名是固定的 `unicanta/头像/<身份号>.jpg`（覆盖式上传，见
--     `ImageBed.avatarId`），客户端拿 `owner` 就能推出地址，没传过的人落回名字首字。
--
-- 隐私：`sponsor_entries` 只经 RPC 读写（RLS 开启、不给任何 policy、也不给 anon/authenticated
-- 表权限），榜单接口只下发名字、身份号、金额、形式和时间——不下发备注里的付款信息，备注由
-- `sponsor_add` 在写入口就截断到 200 字符。
--
-- 由 .github/workflows/apply-sql.yml 整包发给 Management API（本机到 api.supabase.com 不通）。
-- 幂等：create table if not exists / create or replace function / 显式 grant。

create schema if not exists private;

-- ---------------------------------------------------------------- 表

create table if not exists public.sponsor_entries (
    id          bigserial primary key,
    -- 谁：身份码（认不出来时是自报号码）。
    owner       text not null default '',
    -- 提交这一刻的名字（榜上显示它；匿名的人不下发）。
    name        text not null default '',
    -- 金额（元）。0 表示"没有金额"——`ai` 这一种就是 0。
    amount_yuan integer not null default 0,
    -- money 打赏金额 / ai AI 邀请 / other 其它。
    kind        text not null default 'money',
    -- 一句话说明：这笔钱用在哪、为什么赞助。只给榜单作者看，不公开。
    note        text not null default '',
    anonymous   boolean not null default false,
    -- 由维护者补录时留下的记号：'manual'。自己提交的是 ''。
    source      text not null default '',
    hidden      boolean not null default false,
    created_at  timestamptz not null default now(),
    constraint sponsor_entries_amount_check check (amount_yuan >= 0),
    constraint sponsor_entries_amount_max check (amount_yuan <= 1000000)
);

-- 榜单按金额倒序、其次按时间倒序取，走这条。
create index if not exists sponsor_entries_order_idx
    on public.sponsor_entries (hidden, amount_yuan desc, created_at desc);

-- 幂等去重查的正是这三项，走这条。
create index if not exists sponsor_entries_dedup_idx
    on public.sponsor_entries (owner, kind, amount_yuan, created_at desc);

alter table public.sponsor_entries enable row level security;

-- 只经 RPC：不给 policy，也不给这两条角色任何表权限（Supabase 的默认权限会送 insert/select，
-- 所以要显式收回，否则拿到 anon key 的人能自己往榜上插一行"赞助了一万"）。
revoke all on table public.sponsor_entries from anon, authenticated;

-- ---------------------------------------------------------------- 规则

/**
 * 一笔赞助的金额上限：1,000,000 元。
 *
 * 不是"限制人往多了给"，是挡住一个手滑多打几个零（和一条恶意请求把榜单第一名钉死）。
 */
create or replace function private.sponsor_amount_max() returns integer
language sql immutable set search_path = ''
as $$ select 1000000 $$;

/** 榜单一次最多下发多少位。 */
create or replace function private.sponsor_wall_max() returns integer
language sql immutable set search_path = ''
as $$ select 200 $$;

-- ---------------------------------------------------------------- 写

/**
 * 记一笔赞助。返回 `{id, duplicate}`；认不出身份且没有署名时返回 null。
 *
 * 幂等：同一个身份、同一种形式、同一个金额，24 小时内只记一次（`duplicate` = true）。
 * 这样"手滑连点"、"补录一笔已经录过的"都不会把榜刷长；真有两笔一样金额的赞助，隔一天再录。
 *
 * [p_amount_yuan] 夹在 0（`ai` / `other`）到 [private.sponsor_amount_max] 之间；
 * `ai` 一律按 0 记——AI 邀请不涉及钱，混进总额会让"我们收了多少"变成一句假话。
 *
 * [p_name] 是榜上显示的名字：空就用身份号（`#12345` 那种），[p_anonymous] 为真时榜单下发「匿名」。
 */
create or replace function public.sponsor_add(
    p_amount_yuan integer default 0,
    p_kind text default 'money',
    p_name text default '',
    p_note text default '',
    p_anonymous boolean default false,
    p_owner text default '',
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who        text := coalesce(
        private.identity_of(p_token),
        nullif(left(btrim(coalesce(p_owner, '')), 60), '')
    );
    kind_text  text := lower(btrim(coalesce(p_kind, '')));
    name_text  text := left(btrim(coalesce(p_name, '')), 40);
    note_text  text := left(btrim(coalesce(p_note, '')), 200);
    amount     integer;
    existing   bigint;
    new_id     bigint;
begin
    perform private.throttle_write('sponsor_add', 6, 60);

    if who is null then
        return null;
    end if;

    if kind_text not in ('money', 'ai', 'other') then
        kind_text := 'money';
    end if;

    -- `ai` / `other` 不带钱：金额一律按 0 记。
    amount := case
        when kind_text = 'money' then greatest(0, least(private.sponsor_amount_max(), coalesce(p_amount_yuan, 0)))
        else 0
    end;

    if kind_text = 'money' and amount = 0 then
        return null;
    end if;

    -- 24 小时内的同一笔算重复：返回已经那一行，页面据此说"这笔已经在榜上了"。
    select e.id into existing
      from sponsor_entries e
     where e.owner = who
       and e.kind = kind_text
       and e.amount_yuan = amount
       -- 撤掉的不能算「已在榜上」：否则用户 sponsor_hide 之后 24 小时内想重新记同一笔，
       -- 会一直拿到 duplicate，永远记不回来。
       and not e.hidden
       and e.created_at > now() - interval '24 hours'
     order by e.created_at desc
     limit 1;

    if existing is not null then
        return jsonb_build_object('id', existing, 'duplicate', true);
    end if;

    insert into sponsor_entries (owner, name, amount_yuan, kind, note, anonymous)
    values (who, name_text, amount, kind_text, note_text, coalesce(p_anonymous, false))
    returning id into new_id;

    return jsonb_build_object('id', new_id, 'duplicate', false);
end;
$$;

/**
 * 把一笔从榜上摘掉（自己撤掉，或维护者撤掉）。返回 true = 这一笔现在不显示了。
 *
 * 不做 delete：榜是账，账只标"不显示"，不抹行。
 */
create or replace function public.sponsor_hide(
    p_id bigint,
    p_owner text default '',
    p_token text default ''
) returns boolean
language plpgsql security definer set search_path = public, private as $$
declare
    who text := coalesce(
        private.identity_of(p_token),
        nullif(left(btrim(coalesce(p_owner, '')), 60), '')
    );
begin
    perform private.throttle_write('sponsor_hide', 20, 60);

    if who is null or p_id is null then
        return false;
    end if;

    update sponsor_entries
       set hidden = true
     where id = p_id
       and owner = who;

    return found;
end;
$$;

-- ---------------------------------------------------------------- 读

/**
 * 赞助榜。返回：
 *
 * ```json
 * {
 *   "total_yuan": 1280,      // 所有未隐藏、kind = money 的金额之和
 *   "count": 12,             // 榜上一共几位（不按金额算，`ai` 也算一位）
 *   "money_count": 5,        // 其中出钱的几位
 *   "ai_count": 7,           // 其中邀请 AI 的几位
 *   "entries": [ { id, owner, name, amount_yuan, kind, anonymous, created_at } ]
 * }
 * ```
 *
 * 排序：金额高的在前，其次时间新的在前。金额相同的人之间不插队——`created_at` 是最后一档，
 * 它至少是确定的。
 *
 * 不下发备注：那是"这笔钱用在哪"的私人说明，只留在库里。
 */
create or replace function public.sponsor_wall()
returns jsonb
language plpgsql stable security definer set search_path = public, private as $$
declare
    result jsonb;
begin
    select coalesce(jsonb_agg(jsonb_build_object(
               'id', r.id,
               'owner', r.owner,
               'name', r.shown_name,
               'amount_yuan', r.amount_yuan,
               'kind', r.kind,
               'anonymous', r.anonymous,
               'created_at', r.created_at
           ) order by r.amount_yuan desc, r.created_at desc), '[]'::jsonb)
      into result
      from (
          select e.id,
                 e.owner,
                 -- 匿名的人名字一律空串：榜上画「匿名」，照片也不给（见客户端 SponsorRow）。
                 case
                     when e.anonymous then ''
                     when btrim(e.name) <> '' then btrim(e.name)
                     when btrim(e.owner) <> '' then '#' || btrim(e.owner)
                     else ''
                 end as shown_name,
                 e.amount_yuan,
                 e.kind,
                 e.anonymous,
                 e.created_at
            from sponsor_entries e
           where not e.hidden
           order by e.amount_yuan desc, e.created_at desc
           limit private.sponsor_wall_max()
      ) r;

    return jsonb_build_object(
        'total_yuan', coalesce((
            select sum(e.amount_yuan)::bigint
              from sponsor_entries e
             where not e.hidden and e.kind = 'money'
        ), 0),
        'count', coalesce((select count(*) from sponsor_entries where not hidden), 0),
        'money_count', coalesce((select count(*) from sponsor_entries where not hidden and kind = 'money'), 0),
        'ai_count', coalesce((select count(*) from sponsor_entries where not hidden and kind = 'ai'), 0),
        'entries', result
    );
end;
$$;

/**
 * 我提交过的赞助（含已经撤掉的），最近的在前，最多 50 条。页面用它回答"我到底记上了没有"。
 *
 * 只认 token：认不出身份时是空表。
 */
create or replace function public.sponsor_mine(p_token text default '')
returns jsonb
language plpgsql stable security definer set search_path = public, private as $$
declare
    who text := private.identity_of(p_token);
begin
    if who is null then
        return '[]'::jsonb;
    end if;

    return coalesce((
        select jsonb_agg(jsonb_build_object(
                   'id', t.id,
                   'amount_yuan', t.amount_yuan,
                   'kind', t.kind,
                   'hidden', t.hidden,
                   'created_at', t.created_at
               ) order by t.created_at desc)
        from (
            select e.id, e.amount_yuan, e.kind, e.hidden, e.created_at
              from sponsor_entries e
             where e.owner = who
             order by e.created_at desc
             limit 50
        ) t
    ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------- grants

grant execute on function public.sponsor_add(integer, text, text, text, boolean, text, text)
    to anon, authenticated;
grant execute on function public.sponsor_hide(bigint, text, text) to anon, authenticated;
grant execute on function public.sponsor_wall() to anon, authenticated;
grant execute on function public.sponsor_mine(text) to anon, authenticated;

notify pgrst, 'reload schema';

-- 落库自检（只读）
select 'table' as kind, (select count(*)::text from information_schema.tables
                          where table_schema = 'public' and table_name = 'sponsor_entries') as found
union all
select 'functions', string_agg(proname, ', ' order by proname)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and proname in ('sponsor_add', 'sponsor_hide', 'sponsor_wall', 'sponsor_mine');
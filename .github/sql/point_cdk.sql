-- CDK 码池：point_offer_codes + point_offer_publish_codes / point_my_codes，并同签名替换 point_offer_redeem。
-- 幂等：create table if not exists / create or replace function / alter ... add column if not exists。
-- 由 .github/workflows/apply-sql.yml 整包发给 Management API（本机到 api.supabase.com 不通）。
-- 推荐页的 CDK 兑换中心：卖家一次贴一批码，买家花积分换走其中一个。
--
-- 与 `point_offers` 那一套的关系：一行商品（标题、价格、库存、卖家）仍然在 `point_offers` 里，
-- 但它的交付内容只有 `deliverable` 一栏，一份商品只能写一段话。真实的情况常常不是这样——机场
-- 订阅链接、邀请码、账号，卖家手里是一串**互不相同**的码，卖一份就该从池子里拿走一个，而且一个
-- 码只能给一个人。这个文件补的是那一层：
--
--   * 码池（`point_offer_codes`）——`taken_by = ''` 表示还没被取走；取走时写上买家身份码和订单
--     id。`for update skip locked` 让两个同时点兑换的买家拿到不同的行，`unique (offer_id, code)`
--     让同一批里重复贴的码只存一次。
--   * `point_offer_publish_codes`——贴一批码上架。库存 = 真正入库的条数，`deliverable` 留空：
--     交付内容这一刻在池子里，不在商品行上。
--   * `point_offer_redeem`——**同签名替换**。商品有码池就从池子里取一个码，没有码池就走老路径
--     （`offer.deliverable` 那段"网站交付内容"），所以老客户端一行不用改、行为也不变。
--   * `point_my_codes`——我取走的码，按取走时间倒序。
--
-- 身份：与 20261123110000 / 20261124120000 一致，只认 `private.identity_of(p_token)`。积分是钱，
-- 自报号码（`#12345` 这种）在社区里是公开的，拿它当身份等于谁都能花别人的分。
--
-- 表只经 RPC 读写：RLS 开启、不给任何 policy、也不给 anon / authenticated 表权限。
--
-- Run after 20261124120000_airport_and_points.sql. 推法见仓库 AGENTS.md 第 10 节（Management API，
-- 整包发一次；仓库里没有自动 push 迁移的 workflow）。

-- ---------------------------------------------------------------- 码池

create table if not exists public.point_offer_codes (
    id       bigserial primary key,
    -- 属于货架上的哪一行商品；商品删了码一起删。
    offer_id bigint not null references public.point_offers (id) on delete cascade,
    -- 单条码 / 单条订阅链接。600 字符够放下一个带 token 的订阅地址。
    code     text not null,
    -- 取走它的买家身份码；空串 = 还没发出去。
    taken_by text not null default '',
    taken_at timestamptz,
    -- 取走它的那笔订单，便于事后对账（0 = 还没取走）。
    order_id bigint not null default 0,
    -- 同一件商品下同一个码只存一次：卖家把同一批贴两遍不该变成两个买家拿到同一个码。
    constraint point_offer_codes_offer_code_key unique (offer_id, code),
    constraint point_offer_codes_code_len check (char_length(code) <= 600)
);

-- 兑换时要找的永远是"这件商品还没被取走的第一个码"，所以索引用部分索引，只盖住那一小堆行。
create index if not exists point_offer_codes_free_idx
    on public.point_offer_codes (offer_id)
    where taken_by = '';

-- 「我的码」按买家身份倒序取，走这条。
create index if not exists point_offer_codes_taken_idx
    on public.point_offer_codes (taken_by, taken_at desc);

alter table public.point_offer_codes enable row level security;

-- 只经 RPC：不给 policy，也不给这两条角色任何表权限（Supabase 的默认权限会送 insert/select，
-- 所以要显式收回，否则拿到 anon key 的人可以自己把别人的码改成"已取走"）。
revoke all on table public.point_offer_codes from anon, authenticated;

-- ---------------------------------------------------------------- 商城：贴一批码上架

/**
 * 贴一批码上架：一行一个（也认逗号和空白，从表格里复制出来常常是逗号分隔的）。
 *
 * 去空、去重、每条截到 600 字符、最多 200 条，顺序按贴进来的先后——买家拿到的码的顺序与卖家贴上
 * 来的顺序一致，看得懂。`stock` 写的是**真正入库**的条数，`deliverable` 留空（交付内容在池子里）。
 *
 * 一条有效码都解析不出来时 `raise`：那不是"上架成功但没货"，是表单没粘上。
 *
 * [p_owner] 是署名（挂东西的人写的名字），不当身份用；身份只认 [p_token]。`p_owner_id` 为与
 * `point_offer_publish` 保持同一副参数表而留着，这里不参与任何判断。
 *
 * 返回 `{id, codes}`；`codes` 是实际入库条数。认不出身份、或者标题为空时返回 null。
 */
create or replace function public.point_offer_publish_codes(
    p_title text,
    p_detail text,
    p_price integer,
    p_codes text,
    p_kind text default 'airport',
    p_owner text default '',
    p_owner_id text default '',
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who         text := private.identity_of(p_token);
    title_text  text := left(btrim(coalesce(p_title, '')), 80);
    detail_text text := left(btrim(coalesce(p_detail, '')), 400);
    kind_text   text := lower(btrim(coalesce(p_kind, '')));
    price_value integer := greatest(private.point_price_min(),
                                    least(private.point_price_max(), coalesce(p_price, 0)));
    codes_arr   text[];
    new_id      bigint;
    stored      integer := 0;
begin
    perform private.throttle_write('point_offer_publish_codes', 6, 60);

    if who is null or title_text = '' then
        return null;
    end if;
    if kind_text not in ('airport', 'invite', 'account', 'other') then
        kind_text := 'other';
    end if;

    with parsed as (
        select left(btrim(t.code), 600) as code, min(t.ord) as ord
          from regexp_split_to_table(coalesce(p_codes, ''), '[\s,]+') with ordinality as t (code, ord)
         where btrim(t.code) <> ''
         group by 1
         order by ord
         limit 200
    )
    select coalesce(array_agg(p.code order by p.ord), '{}'::text[])
      into codes_arr
      from parsed p;

    if coalesce(array_length(codes_arr, 1), 0) = 0 then
        raise exception '没解析出可兑换的码：一行贴一个，别只发标题' using errcode = '22023';
    end if;

    insert into point_offers (title, detail, price, stock, kind, deliverable, owner, owner_id)
    values (title_text, detail_text, price_value, array_length(codes_arr, 1), kind_text, '', who,
            left(btrim(coalesce(p_owner, '')), 60))
    returning id into new_id;

    insert into point_offer_codes (offer_id, code)
    select new_id, c
      from unnest(codes_arr) as c
    on conflict (offer_id, code) do nothing;
    get diagnostics stored = row_count;

    -- 库存以真正入库的条数为准：上面已经去重过，这里再兜一次，免得库存和码池对不上（库存多于
    -- 码数会让买家点了兑换才看见"码已经发完了"）。
    update point_offers
       set stock = stored,
           updated_at = now()
     where id = new_id;

    return jsonb_build_object('id', new_id, 'codes', stored);
end;
$$;

-- ---------------------------------------------------------------- 商城：兑换（同签名替换）

/**
 * 用积分换一件商品。
 *
 * 顺序是刻意的：先锁住商品行（`for update`），再看有没有码池、有没有码，再检查余额，再动钱。
 * 两个买家同时点兑换的时候，第二个会等第一个把库存减完，然后拿到 `卖完了` 而不是负库存。
 *
 * 有码池的商品：从池子里取一个 `taken_by = ''` 的码（`for update skip locked`，并发时各拿各的
 * 行），把它写进订单的交付内容快照、标上买家和订单 id；没有码池的商品保持原样，交付 `offer.deliverable`。
 *
 * 拒绝的情形都返回 `{ok: false, error: ...}` 而不是抛异常：这是页面上要说给人听的一句话，不是
 * 程序错误。成功时返回 `{ok: true, balance, deliverable, code, title, price, order_id}`；`code`
 * 与 `deliverable` 同值，老客户端读 `deliverable`、新页面读 `code`，两边都不用改。
 */
create or replace function public.point_offer_redeem(
    p_offer_id bigint,
    p_owner text default '',
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    -- 买家只认 token：拿自报号码当身份，等于用别人余额买单。
    who            text := private.identity_of(p_token);
    offer          record;
    my_balance     bigint;
    new_order_id   bigint;
    new_bal        bigint;
    has_pool       boolean := false;
    pick_id        bigint;
    pick_code      text;
    giveaway       text;
begin
    perform private.throttle_write('point_offer_redeem', 30, 60);

    if who is null then
        return jsonb_build_object('ok', false, 'error', '没认出身份，登录一下再来');
    end if;
    if p_offer_id is null then
        return jsonb_build_object('ok', false, 'error', '这件商品不在了');
    end if;

    select * into offer from point_offers where id = p_offer_id for update;
    if not found then
        return jsonb_build_object('ok', false, 'error', '这件商品不在了');
    end if;
    if not offer.active then
        return jsonb_build_object('ok', false, 'error', '这件商品已经下架');
    end if;
    if offer.owner = who then
        return jsonb_build_object('ok', false, 'error', '自己挂的东西不用兑');
    end if;
    if offer.stock <= 0 then
        return jsonb_build_object('ok', false, 'error', '卖完了');
    end if;

    -- 有码池的商品：交付内容是一个"还没人拿走的码"，取一个并当场锁住它。`skip locked` 让第二个
    -- 同时点兑换的人跳过这一行去拿下一个，两个人不会拿到同一个码；锁到本语句结束就释放。
    select exists(select 1 from point_offer_codes where offer_id = offer.id) into has_pool;

    if has_pool then
        select c.id, c.code into pick_id, pick_code
          from point_offer_codes c
         where c.offer_id = offer.id
           and c.taken_by = ''
         order by c.id
         for update skip locked
         limit 1;

        if pick_id is null then
            return jsonb_build_object('ok', false, 'error', '码已经发完了');
        end if;
        giveaway := pick_code;
    else
        giveaway := offer.deliverable;
    end if;

    -- 锁住自己的钱包行再算余额：两个人同时各买一件不同的东西时，不加锁会各自看到同一个旧
    -- 余额、都通过检查，然后第二笔撞上 `balance >= 0` 的约束，把一句人话变成约束报错。
    select balance into my_balance from point_accounts where owner = who for update;
    if coalesce(my_balance, 0) < offer.price then
        return jsonb_build_object(
            'ok', false,
            'error', '积分不够：还差 ' || (offer.price - coalesce(my_balance, 0)) || ' 分',
            'balance', coalesce(my_balance, 0)
        );
    end if;

    -- 只减库存，不动 `active`：卖完的东西留在货架上显示「卖完」，比无声消失更诚实；
    -- 下架是卖家的事，不是库存归零的副作用。
    update point_offers
       set stock = stock - 1,
           sold = sold + 1,
           updated_at = now()
     where id = offer.id;

    insert into point_orders (offer_id, title, seller, buyer, price, deliverable)
    values (offer.id, offer.title, offer.owner, who, offer.price, coalesce(giveaway, ''))
    returning id into new_order_id;

    if has_pool then
        update point_offer_codes
           set taken_by = who,
               taken_at = now(),
               order_id = new_order_id
         where id = pick_id;
    end if;

    new_bal := private.point_apply(who, -offer.price, 'redeem', new_order_id::text);
    -- 卖家拿全款：这一套积分只在用户之间转，平台不抽成，否则"发东西赚分"要打折才划算。
    if coalesce(offer.owner, '') <> '' then
        perform private.point_apply(offer.owner, offer.price, 'sold', new_order_id::text);
    end if;

    return jsonb_build_object(
        'ok', true,
        'balance', coalesce(new_bal, 0),
        'deliverable', coalesce(giveaway, ''),
        'code', coalesce(giveaway, ''),
        'title', offer.title,
        'price', offer.price,
        'order_id', new_order_id
    );
end;
$$;

-- ---------------------------------------------------------------- 商城：我取走的码

/**
 * 我取走的码：`[{id, offer_id, title, code, taken_at}]`，最近的在前，最多 100 条。
 *
 * 只认 token；认不出身份时是空表——码是真东西，不能按一串随时能改的自报号码发。
 */
create or replace function public.point_my_codes(p_token text default '')
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
                   'offer_id', t.offer_id,
                   'title', t.title,
                   'code', t.code,
                   'taken_at', t.taken_at
               ) order by t.taken_at desc)
        from (
            select c.id, c.offer_id, coalesce(o.title, '') as title, c.code, c.taken_at
              from point_offer_codes c
              left join point_offers o on o.id = c.offer_id
             where c.taken_by = who
             order by c.taken_at desc nulls last
             limit 100
        ) t
    ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------- grants

grant execute on function public.point_offer_publish_codes(text, text, integer, text, text, text, text, text)
    to anon, authenticated;
grant execute on function public.point_offer_redeem(bigint, text, text) to anon, authenticated;
grant execute on function public.point_my_codes(text) to anon, authenticated;

notify pgrst, 'reload schema';

-- 落库自检（只读）
select 'tables' as kind, string_agg(table_name, ', ' order by table_name) as names
  from information_schema.tables
 where table_schema = 'public' and table_name = 'point_offer_codes'
union all
select 'functions', string_agg(proname, ', ' order by proname)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and proname in ('point_offer_publish_codes', 'point_my_codes')
union all
select 'point_offer_redeem_overloads', count(*)::text
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and proname = 'point_offer_redeem';

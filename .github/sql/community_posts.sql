-- 社区帖子：community_posts / post_comments / post_likes 三张表 + 六个 RPC（含分类 category 与标签 tags）。
-- 幂等：create table if not exists、alter ... add column if not exists、drop 旧签名后重建 post_add。
-- 由 .github/workflows/apply-sql.yml 整包发给 Management API。
-- 社区帖子：发帖、看帖、回帖、点赞。
--
-- 社区里原来有三种写下来的东西：方案（`plans`）、评论（`community_comments`）、机场（`airport_entries`）。
-- 少了最普通的那一种——一个标题加一段正文的帖子：不是要卸载什么、不是在评某个包，只是有话要说。
-- 这一块补的就是它，三张表：
--
--   * `community_posts`  一条帖子：标题、正文、署名、身份码、置顶、锁定。
--   * `post_comments`    回帖：一条帖子下面按时间正序读，最多读 200 条。
--   * `post_likes`       点赞：(帖子, 身份) 是主键，所以同一个人按两次还是一个赞，取消就是删行。
--
-- 身份（硬性）：六个函数里所有"我是谁"只认 `private.identity_of(p_token)`。认不出来就是 null /
-- `{ok:false,error}`，**不退回客户端自报的号码** —— 帖子能删、赞能记，自报号码等于谁都能冒充作者。
-- 读函数签名里的 `p_viewer` 只是线上的历史形状（客户端一直带），本文件不拿它当身份。
--
-- 限速：每个写函数开头一行 `private.throttle_write('<bucket>', <max>, 60)`（按来源 IP，见
-- 20261122120000_write_throttle.sql）。`post_add` 另有一条按身份的闸：同一身份 60 秒内只收一帖
-- ——按 IP 的闸挡不住"换代理连发"，按身份的闸挡不住"一个 IP 上十个人各发一帖"，两条都要。
--
-- 表都开 RLS、不给 policy、不给 anon/authenticated 表权限：读写只经下面这几个函数
-- （`security definer`），与 `public.plans` / `community_comments` 的做法一致。
--
-- Run after 20261124140000_update_reports.sql. 推法见仓库 AGENTS.md 第 10 节（Management API，
-- 整包发一次；仓库里没有自动 push 迁移的 workflow）。

create schema if not exists private;

-- ---------------------------------------------------------------- 表

create table if not exists public.community_posts (
    id         bigserial primary key,
    -- 列表上那一行字。空标题没有意义，所以 not null，长度在函数里截到 120。
    title      text not null,
    -- 正文全文。列表只发前 200 个字符（`excerpt`），详情才发全文。
    body       text not null default '',
    -- 署名（人自己写的名字）与它背后的身份码：能删帖的是身份码，不是署名。
    author     text not null default '',
    author_id  text not null default '',
    -- 置顶：只有运维直接改库才会置顶，RPC 没有置顶入口。
    pinned     boolean not null default false,
    -- 锁定：锁住的帖子还能看、能点赞，但收不了新回帖。
    locked     boolean not null default false,
    -- 分类：页面上是五个候选（闲聊/求助/分享/资源/其它），空串表示没选。后端只存字符串，
    -- 不认候选集：候选是界面的事，数据库多存一个没见过的分类不会出事。
    category   text not null default '',
    -- 标签：最多 5 个、每个最多 20 字，去空去重在 post_add 里做。列表与详情都按 JSON 数组发出去
    -- （`to_jsonb(tags)`），客户端拿到的就是一个字符串数组。
    tags       text[] not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint community_posts_title_len check (char_length(title) <= 120),
    constraint community_posts_body_len check (char_length(body) <= 8000),
    constraint community_posts_category_len check (char_length(category) <= 20),
    constraint community_posts_tags_len check (cardinality(tags) <= 5)
);

-- 分类与标签是「分类 + 标签」这一版才有的两列。上面那个 create table 建新库时一次建好；
-- 开发库上要是推过前一稿（没有这两列），这两条补上。`add column if not exists` 让本文件能重复跑。
-- 补齐路径上不会补那两个 check：写入口只有 post_add，它在函数里已经截好长度、砍掉多余的标签。
alter table public.community_posts add column if not exists category text not null default '';
alter table public.community_posts add column if not exists tags text[] not null default '{}';

-- 列表的排序就是这一个索引：置顶在前，其余按时间倒序（分页也是这个顺序）。
create index if not exists community_posts_order_idx
    on public.community_posts (pinned desc, created_at desc);

create table if not exists public.post_comments (
    id         bigserial primary key,
    post_id    bigint not null references public.community_posts (id) on delete cascade,
    body       text not null,
    author     text not null default '',
    author_id  text not null default '',
    created_at timestamptz not null default now(),
    constraint post_comments_body_len check (char_length(body) <= 4000)
);

-- 一条帖子的回帖按时间正序读，(post_id, created_at) 正好是那个扫法。
create index if not exists post_comments_post_idx
    on public.post_comments (post_id, created_at);

create table if not exists public.post_likes (
    post_id    bigint not null references public.community_posts (id) on delete cascade,
    -- 点赞的身份码：认出来的身份，一个帖子一个赞。
    actor      text not null,
    created_at timestamptz not null default now(),
    primary key (post_id, actor)
);

create index if not exists post_likes_post_idx on public.post_likes (post_id);

alter table public.community_posts enable row level security;
alter table public.post_comments enable row level security;
alter table public.post_likes enable row level security;

-- 与 20261122120000_operation_failures.sql / 20261124140000_update_reports.sql 同一条线：
-- 表本身对 API 角色什么都不给，读和写都只有下面这几个函数。
revoke all on table public.community_posts from anon, authenticated;
revoke all on table public.post_comments from anon, authenticated;
revoke all on table public.post_likes from anon, authenticated;

-- ---------------------------------------------------------------- 读：列表

/**
 * 帖子列表：置顶在前，其余按时间倒序，翻页用 `p_offset` / `p_limit`。
 *
 * `p_limit` 夹在 1..50：一页五十条已经比一屏多得多，再大就是把整张表塞进一次回答。
 * 每条带自己的回帖数、点赞数，以及"我赞过没有"（`mine_like`，认不出身份时是 false）。
 *
 * `excerpt` 是正文前 200 个字符，全文只在 [post_get] 里发 —— 列表上没人读整篇，但
 * 一屏二十篇全文就是几百 KB。
 *
 * `category` 原样发（空串表示没选），`tags` 用 `to_jsonb` 发成 JSON 字符串数组 —— PostgREST 把
 * `jsonb` 里的数组原样交给客户端，客户端那边就是一个 `List<String>`，不必再解 PostgreSQL 的
 * `{a,b}` 数组字面量。
 *
 * `p_viewer` 是签名里的历史形状，**不参与身份判断**：认人只认 `p_token`。
 */
create or replace function public.posts_list(
    p_viewer text default '',
    p_token text default '',
    p_offset integer default 0,
    p_limit integer default 20
) returns jsonb
language plpgsql stable security definer set search_path = public, private as $$
declare
    who    text := private.identity_of(p_token);
    take_n integer := greatest(1, least(50, coalesce(p_limit, 20)));
    skip_n integer := greatest(0, coalesce(p_offset, 0));
    result jsonb;
begin
    select coalesce(jsonb_agg(
        jsonb_build_object(
            'id', r.id,
            'title', r.title,
            'excerpt', r.excerpt,
            'author', r.author,
            'author_id', r.author_id,
            'created_at', r.created_at,
            'pinned', r.pinned,
            'locked', r.locked,
            'comments', r.comments,
            'likes', r.likes,
            'mine_like', r.mine_like,
            'category', r.category,
            'tags', to_jsonb(r.tags)
        )
        order by r.pinned desc, r.created_at desc
    ), '[]'::jsonb)
    into result
    from (
        select p.id,
               p.title,
               left(p.body, 200) as excerpt,
               p.author,
               p.author_id,
               p.created_at,
               p.pinned,
               p.locked,
               p.category,
               p.tags,
               (select count(*) from post_comments c where c.post_id = p.id) as comments,
               (select count(*) from post_likes l where l.post_id = p.id) as likes,
               exists (
                   select 1 from post_likes m
                   where m.post_id = p.id
                     and m.actor = who
               ) as mine_like
        from community_posts p
        order by p.pinned desc, p.created_at desc
        offset skip_n
        limit take_n
    ) r;

    return result;
end;
$$;

-- ---------------------------------------------------------------- 读：一条帖子

/**
 * 一条帖子的全文、它的回帖（最多 200 条，按时间正序）和点赞数。
 *
 * `mine` 是"这条是不是我发的"，只对能认出来的身份有意义；页面拿它决定显不显示删除。
 * 帖子不在就返回 null —— 这与"帖子在但一条回帖也没有"（`comments` 是空数组）是两件事。
 */
create or replace function public.post_get(
    p_id bigint,
    p_viewer text default '',
    p_token text default ''
) returns jsonb
language plpgsql stable security definer set search_path = public, private as $$
declare
    who      text := private.identity_of(p_token);
    post_row record;
    likes    bigint;
    mine_like boolean;
    replies  jsonb;
begin
    if p_id is null then
        return null;
    end if;

    select * into post_row from community_posts where id = p_id;
    if not found then
        return null;
    end if;

    select count(*) into likes from post_likes where post_id = p_id;
    select exists (
        select 1 from post_likes where post_id = p_id and actor = who
    ) into mine_like;

    select coalesce(jsonb_agg(
        jsonb_build_object(
            'id', c.id,
            'body', c.body,
            'author', c.author,
            'author_id', c.author_id,
            'created_at', c.created_at
        )
        order by c.created_at asc, c.id asc
    ), '[]'::jsonb)
    into replies
    from (
        select * from post_comments
        where post_id = p_id
        order by created_at asc, id asc
        limit 200
    ) c;

    return jsonb_build_object(
        'post', jsonb_build_object(
            'id', post_row.id,
            'title', post_row.title,
            'excerpt', left(post_row.body, 200),
            'body', post_row.body,
            'author', post_row.author,
            'author_id', post_row.author_id,
            'created_at', post_row.created_at,
            'pinned', post_row.pinned,
            'locked', post_row.locked,
            'comments', (select count(*) from post_comments where post_id = p_id),
            'likes', likes,
            'mine_like', coalesce(mine_like, false),
            'mine', coalesce(post_row.author_id = who, false),
            'category', post_row.category,
            'tags', to_jsonb(post_row.tags)
        ),
        'comments', replies,
        'likes', likes
    );
end;
$$;

-- ---------------------------------------------------------------- 写：发帖

/**
 * 发一帖。
 *
 * 限速两条，都是刻意的：
 *   * `throttle_write('post_add', 3, 60)` — 按来源 IP，一个出口一分钟最多三次，挡住脚本连发。
 *   * 同一身份 60 秒内只收一帖 — 按 IP 的闸挡不住"换代理连发"，而身份码换不掉。
 *
 * 分类：调用方给什么归整什么（trim、截到 20 字），**不校验候选集**——候选是页面上的五个按钮；
 * 后端多存一个陌生分类不会出事，而在这里白名单化会把客户端的一举一动都锁进数据库版本里。
 *
 * 标签：去空、去重、每个截到 20 字、最多 5 个。多出来的直接丢，不报错：多写一行标签不该让
 * 整篇帖子发不出去。
 *
 * 标题空、认不出身份 → null（"没存下来"）；一分钟内发过 → `{ok:false,error}`，页面能把话说清楚。
 * 正文截到 8000、标题截到 120，超过的部分直接截，不报错：手里的字比一句"标题太长"更值钱。
 *
 * 签名在「分类 + 标签」这一版变长了，所以要先 drop 旧的四参签名：`p_token` 有默认值，留着旧
 * 重载会让 PostgREST 在"少传分类和标签"的调用上挑不出函数（Could not choose the best candidate）。
 * 本文件还没落过库，这条 drop 在干净库上是空操作；drop 会带走 grant，末尾统一重发。
 */
drop function if exists public.post_add(text, text, text, text);

create or replace function public.post_add(
    p_title text,
    p_body text,
    p_author text default '',
    p_category text default '',
    p_tags text[] default '{}',
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who           text := private.identity_of(p_token);
    title_text    text := left(btrim(coalesce(p_title, '')), 120);
    body_text     text := left(coalesce(p_body, ''), 8000);
    author_text   text := left(btrim(coalesce(p_author, '')), 60);
    category_text text := left(btrim(coalesce(p_category, '')), 20);
    tag_list      text[];
    last_post     timestamptz;
    new_id        bigint;
begin
    perform private.throttle_write('post_add', 3, 60);

    if who is null or title_text = '' then
        return null;
    end if;

    -- 标签在这里归整，不写在 declare 的初值里：array(...) 包一个带 limit 的子查询放在变量
    -- 初始化里是能省两行，但真出错时错误位置会指向 declare，不如写成一条看得见的语句。
    select array(
        select distinct left(btrim(t), 20)
          from unnest(coalesce(p_tags, '{}'::text[])) as t
         where btrim(t) <> ''
         limit 5
    ) into tag_list;

    select max(created_at) into last_post from community_posts where author_id = who;
    if last_post is not null and last_post > now() - interval '60 seconds' then
        return jsonb_build_object('ok', false, 'error', '刚发过一帖，等一分钟再发');
    end if;

    insert into community_posts (title, body, author, author_id, category, tags)
    values (title_text, body_text, author_text, who, category_text, coalesce(tag_list, '{}'::text[]))
    returning id into new_id;

    return jsonb_build_object('id', new_id);
end;
$$;

-- ---------------------------------------------------------------- 写：回帖

/**
 * 回一帖。回答 `{id, comments}`，`comments` 是回完之后那条帖子的回帖总数 —— 列表上的数字
 * 不必再问一次。
 *
 * 拒绝的情形都返回 `{ok:false,error}` 而不是抛异常：这是页面上要说给人听的一句话，不是程序错误。
 * 锁住的帖子不能再回（`locked`），删掉的帖子（不存在）也不能回。
 */
create or replace function public.post_comment_add(
    p_post_id bigint,
    p_body text,
    p_author text default '',
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who         text := private.identity_of(p_token);
    body_text   text := left(btrim(coalesce(p_body, '')), 4000);
    author_text text := left(btrim(coalesce(p_author, '')), 60);
    target      record;
    new_id      bigint;
    total       bigint;
begin
    perform private.throttle_write('post_comment_add', 20, 60);

    if who is null then
        return jsonb_build_object('ok', false, 'error', '没认出身份，回帖记不上');
    end if;
    if p_post_id is null then
        return jsonb_build_object('ok', false, 'error', '这个帖子不在了');
    end if;

    select id, locked into target from community_posts where id = p_post_id;
    if not found then
        return jsonb_build_object('ok', false, 'error', '这个帖子不在了');
    end if;
    if target.locked then
        return jsonb_build_object('ok', false, 'error', '这个帖子已经锁了，回不了');
    end if;
    if body_text = '' then
        return jsonb_build_object('ok', false, 'error', '回帖不能是空的');
    end if;

    insert into post_comments (post_id, body, author, author_id)
    values (p_post_id, body_text, author_text, who)
    returning id into new_id;

    select count(*) into total from post_comments where post_id = p_post_id;

    return jsonb_build_object('id', new_id, 'comments', total);
end;
$$;

-- ---------------------------------------------------------------- 写：点赞

/**
 * 点赞 / 取消点赞。`p_like` 是 true 就记一个赞（重复按还是那一个），false 就把这一行删掉。
 * 回答 `{likes, mine_like}`：赞完之后的数与"我现在赞没赞"，页面不必自己猜。
 *
 * 认不出身份 → `{ok:false,error}`：赞是挂在这个身份上的一个行，没有身份就没有行。
 */
create or replace function public.post_like(
    p_post_id bigint,
    p_like boolean default true,
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who   text := private.identity_of(p_token);
    total bigint;
    mine  boolean := false;
begin
    perform private.throttle_write('post_like', 40, 60);

    if who is null then
        return jsonb_build_object('ok', false, 'error', '没认出身份，赞记不上');
    end if;
    if p_post_id is null or not exists (select 1 from community_posts where id = p_post_id) then
        return jsonb_build_object('ok', false, 'error', '这个帖子不在了');
    end if;

    if coalesce(p_like, true) then
        insert into post_likes (post_id, actor)
        values (p_post_id, who)
        on conflict (post_id, actor) do nothing;
    else
        delete from post_likes where post_id = p_post_id and actor = who;
    end if;

    select count(*) into total from post_likes where post_id = p_post_id;
    select exists (
        select 1 from post_likes where post_id = p_post_id and actor = who
    ) into mine;

    return jsonb_build_object('likes', total, 'mine_like', coalesce(mine, false));
end;
$$;

-- ---------------------------------------------------------------- 写：删帖

/**
 * 删帖：只有作者本人（`author_id` 认出来的那个身份）能删，回帖与赞跟着级联删掉。
 *
 * 认不出身份、帖子不在、不是作者 —— 都是 `{ok:false,error}`，页面照着一句话说清楚。
 */
create or replace function public.post_delete(
    p_id bigint,
    p_token text default ''
) returns jsonb
language plpgsql security definer set search_path = public, private as $$
declare
    who      text := private.identity_of(p_token);
    owner_id text;
begin
    perform private.throttle_write('post_delete', 10, 60);

    if who is null then
        return jsonb_build_object('ok', false, 'error', '没认出身份，删不了');
    end if;
    if p_id is null then
        return jsonb_build_object('ok', false, 'error', '这个帖子不在了');
    end if;

    select author_id into owner_id from community_posts where id = p_id;
    if not found then
        return jsonb_build_object('ok', false, 'error', '这个帖子不在了');
    end if;
    if coalesce(owner_id, '') <> who then
        return jsonb_build_object('ok', false, 'error', '只有作者本人能删');
    end if;

    delete from community_posts where id = p_id;

    return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------- grants

grant execute on function public.posts_list(text, text, integer, integer) to anon, authenticated;
grant execute on function public.post_get(bigint, text, text) to anon, authenticated;
grant execute on function public.post_add(text, text, text, text, text[], text) to anon, authenticated;
grant execute on function public.post_comment_add(bigint, text, text, text) to anon, authenticated;
grant execute on function public.post_like(bigint, boolean, text) to anon, authenticated;
grant execute on function public.post_delete(bigint, text) to anon, authenticated;

notify pgrst, 'reload schema';

-- 落库自检（只读）
select 'tables' as kind, string_agg(table_name, ', ' order by table_name) as names
  from information_schema.tables
 where table_schema = 'public'
   and table_name in ('community_posts', 'post_comments', 'post_likes')
union all
select 'functions', string_agg(proname, ', ' order by proname)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and proname in ('posts_list', 'post_get', 'post_add', 'post_comment_add', 'post_like', 'post_delete')
union all
select 'post_add_args', pg_get_function_identity_arguments(p.oid)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and p.proname = 'post_add';

-- 印章：社区给系统应用盖的两字定罪章（毒瘤 / 病毒 / 远控 / 木马 / 广告 / 弹窗）。
--
-- 与 level_fix_ballots 分开两张表：那三个维度回答「该不该删、删了会怎样」，一次只能投一个值；
-- 章回答「它犯了什么罪」，是一人一枚、可改投的独立一票。混进同一张表会把那三个维度的约束和
-- 结算逻辑一起弄乱，而它们正在线上跑。
--
-- 客户端那一侧：LevelFixRepository.stamps() / voteStamp()，页面是「级别修正」页的第四组，
-- 盖到 AppDetailScreen 头部。门槛 12 票 + 55%（与客户端 Stamps.NEEDED / SHARE_NEEDED 一致）。

-- 一人对一包一枚章，换一枚就是改票。
create table if not exists public.package_stamp_votes (
  pkg        text        not null,
  title      text        not null,
  voter      text        not null,
  voter_name text        not null default '',
  created_at timestamptz not null default now(),
  primary key (pkg, voter)
);

create index if not exists package_stamp_votes_pkg_idx
  on public.package_stamp_votes (pkg);

-- 首发六个词。要加新章：改这里 + 客户端 Stamps.TITLES，两边必须同步，
-- 否则新词投上去会被 stamp_vote 静默拒绝（返回 null，客户端显示"印章不在名单里"）。
alter table public.package_stamp_votes
  drop constraint if exists package_stamp_votes_title_check;
alter table public.package_stamp_votes
  add constraint package_stamp_votes_title_check
  check (title in ('毒瘤', '病毒', '远控', '木马', '广告', '弹窗'));

-- 已经盖上去的那一枚。一个包只盖一枚主章；章不经投票撤销（认定过的事不因为票数回落就撤回）。
create table if not exists public.package_stamps (
  pkg        text        primary key,
  title      text        not null,
  votes      int         not null default 0,
  applied_at timestamptz not null default now()
);

-- 每枚章的票数，含 0 票的（顺序固定，页面直接按这个顺序画）。
create or replace function private.stamp_counts(p_pkg text)
returns table (title text, votes int, ord int)
language sql
stable
security definer
set search_path = public, private
as $$
  select t.title,
         coalesce(v.n, 0)::int as votes,
         t.ord::int
    from unnest(array['毒瘤', '病毒', '远控', '木马', '广告', '弹窗'])
         with ordinality as t(title, ord)
    left join (
      select s.title, count(*) as n
        from public.package_stamp_votes s
       where s.pkg = p_pkg
       group by s.title
    ) v on v.title = t.title
   order by t.ord;
$$;

-- 结算：票最多的那一枚够 12 票且占全部章票 55% 就盖上。
create or replace function private.stamp_settle(p_pkg text)
returns void
language plpgsql
security definer
set search_path = public, private
as $$
declare
  v_total  int;
  v_needed int     := 12;
  v_share  numeric := 0.55;
  v_top    record;
begin
  select coalesce(sum(n), 0) into v_total
    from (select count(*) as n from public.package_stamp_votes where pkg = p_pkg group by title) s;

  select c.title, c.n into v_top
    from (select title, count(*) as n
            from public.package_stamp_votes
           where pkg = p_pkg
           group by title) c
   order by c.n desc, c.title
   limit 1;

  if v_top is null then
    return;
  end if;

  if v_top.n >= v_needed and v_total > 0
     and (v_top.n::numeric / v_total) >= v_share then
    insert into public.package_stamps (pkg, title, votes, applied_at)
    values (p_pkg, v_top.title, v_top.n, now())
    on conflict (pkg) do update
      set title      = excluded.title,
          votes      = excluded.votes,
          applied_at = excluded.applied_at;
  end if;
end;
$$;

-- 读：每枚几票、我投了哪一枚、现在盖的是哪一枚。
-- 答复形状（客户端 parseStamps 按这个读，字段名不能改）：
--   {"counts":[{"title","votes","share","mine"}],"applied":"","applied_votes":0,
--    "needed":12,"share_needed":0.55,"total":0}
create or replace function public.stamp_votes_for(p_pkg text, p_voter text default '')
returns jsonb
language plpgsql
stable
security definer
set search_path = public, private
as $$
declare
  v_total         int;
  v_needed        int     := 12;
  v_share         numeric := 0.55;
  v_counts        jsonb   := '[]'::jsonb;
  v_applied       text    := '';
  v_applied_votes int     := 0;
begin
  if coalesce(p_pkg, '') = '' then
    return null;
  end if;

  select coalesce(sum(c.votes), 0) into v_total from private.stamp_counts(p_pkg) c;

  select coalesce(jsonb_agg(jsonb_build_object(
           'title', c.title,
           'votes', c.votes,
           'share', case when v_total = 0 then 0
                         else round(c.votes::numeric / v_total, 4) end,
           'mine',  exists (
             select 1 from public.package_stamp_votes v
              where v.pkg = p_pkg and v.title = c.title and v.voter = p_voter
           )
         ) order by c.ord), '[]'::jsonb)
    into v_counts
    from private.stamp_counts(p_pkg) c;

  select s.title, s.votes into v_applied, v_applied_votes
    from public.package_stamps s
   where s.pkg = p_pkg;

  return jsonb_build_object(
    'counts',        v_counts,
    'applied',       coalesce(v_applied, ''),
    'applied_votes', coalesce(v_applied_votes, 0),
    'needed',        v_needed,
    'share',         v_share,
    'share_needed',  v_share,
    'total',         v_total
  );
end;
$$;

-- 写：盖一枚（改票就是 upsert），写完当场结算，返回盖完之后的整份计数。
create or replace function public.stamp_vote(
  p_pkg   text,
  p_title text,
  p_voter text,
  p_name  text default '',
  p_token text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = public, private
as $$
declare
  v_voter text := coalesce(private.identity_of(p_token), p_voter);
begin
  if coalesce(p_pkg, '') = '' then
    return null;
  end if;
  if p_title is null or p_title not in ('毒瘤', '病毒', '远控', '木马', '广告', '弹窗') then
    return null;
  end if;
  if coalesce(v_voter, '') = '' then
    return null;
  end if;

  -- 与九个写函数同一套按来源 IP 的限速（见 private.write_rate）；章是轻操作，给得比投票宽一点。
  perform private.throttle_write('stamp_vote', 20, 60);

  insert into public.package_stamp_votes (pkg, title, voter, voter_name)
  values (p_pkg, p_title, v_voter, coalesce(p_name, ''))
  on conflict (pkg, voter) do update
    set title      = excluded.title,
        voter_name = excluded.voter_name,
        created_at = now();

  perform private.stamp_settle(p_pkg);

  return public.stamp_votes_for(p_pkg, v_voter);
end;
$$;

-- 只经 RPC 读写：两张表都不给 anon / authenticated 直接碰。
revoke all on public.package_stamp_votes from anon, authenticated;
revoke all on public.package_stamps from anon, authenticated;

grant execute on function public.stamp_votes_for(text, text) to anon, authenticated;
grant execute on function public.stamp_vote(text, text, text, text, text) to anon, authenticated;

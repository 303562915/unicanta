-- 读方案时，空着的具体版本用「同机型 + 同 ROM 族 + 同主版本下唯一已知的那个版本」补上显示。
--
-- 为什么要补：`plans.rom_build` 是 20261124160000 才加的列，比这早的行都是空。读的时候一律
-- 回落到主版本号，界面就只能显示「HyperOS 3」，看不出它是在 3.0.0355 还是别的小版本上验证的。
-- 而一个机型的一个主版本通常只出现过一种具体版本，那这一种就是答案。
--
-- 为什么不直接写回数据库：写库等于替不知道的行编一个版本。这里只在「只有一种已知版本」时显示，
-- 一旦出现两种（16.0.3 与 16.0.501）就照旧留空——那说明这台机器升级过，谁是哪一版已经说不清。
-- 真正的写库仍然只有两条路：上传时自带，以及 identity_bind 的登录自愈。

create or replace function private.only_build(p_model text, p_rom text, p_rom_version integer)
returns text
language sql
stable
as $$
    select case when count(distinct q.rom_build) = 1 then min(q.rom_build) else '' end
      from public.plans q
     where coalesce(q.rom_build, '') <> ''
       and lower(coalesce(q.model, '')) = lower(coalesce(p_model, ''))
       and q.rom = p_rom
       and q.rom_version = p_rom_version
$$;

-- ---------------------------------------------------------------------------------------------
-- 返回类型没变（仍然有 rom_build 这一列），所以都走 create or replace。
-- ---------------------------------------------------------------------------------------------

create or replace function public.plans_for(
    p_model text, p_codename text, p_rom text, p_rom_version integer,
    p_rom_build text default '', p_limit integer default 50
) returns table (
    id bigint, title text, note text, packages text[], votes bigint, comments bigint,
    created_at timestamptz, rebooted_at timestamptz, owner text, is_test boolean, owner_name text,
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text, sdk integer
) language sql stable security definer set search_path = public as $$
    select p.id, p.title, p.note, p.packages,
           (select count(*) from plan_votes v where v.plan_id = p.id)::bigint,
           (select count(*) from community_comments c
             where c.kind = 'plan' and c.target = p.id::text)::bigint,
           p.created_at, p.rebooted_at, p.owner, p.is_test,
           coalesce(nullif(u.name, ''), '') as owner_name,
           p.brand, p.model, coalesce(p.market_name, ''), p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk
    from plans p
    left join canta_users u on u.id::text = p.owner
    where p.reboot_verified
      and lower(p.model) = lower(p_model)
      and (p_codename = '' or lower(p.codename) = lower(p_codename))
      and p.rom = p_rom
      and p.rom_version = p_rom_version
    order by
        (coalesce(p_rom_build, '') <> '' and p.rom_build = p_rom_build) desc,
        p.is_test asc,
        (select count(*) from plan_votes v where v.plan_id = p.id) desc,
        p.created_at desc
    limit greatest(1, least(p_limit, 100))
$$;

create or replace function public.plan_get(p_id bigint) returns table (
    id bigint, title text, note text, packages text[], votes bigint, comments bigint,
    created_at timestamptz, rebooted_at timestamptz, owner text, is_test boolean, owner_name text,
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text, sdk integer
) language sql stable security definer set search_path = public as $$
    select p.id, p.title, p.note, p.packages,
           (select count(*) from plan_votes v where v.plan_id = p.id)::bigint,
           (select count(*) from community_comments c
             where c.kind = 'plan' and c.target = p.id::text)::bigint,
           p.created_at, p.rebooted_at, p.owner, p.is_test,
           coalesce(nullif(u.name, ''), '') as owner_name,
           p.brand, p.model, coalesce(p.market_name, ''), p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk
      from plans p
      left join canta_users u on u.id::text = p.owner
     where p.id = p_id
     limit 1
$$;

create or replace function public.plans_by_owner(p_owner text) returns table (
    id bigint, title text, note text, packages text[], votes bigint, comments bigint,
    created_at timestamptz, rebooted_at timestamptz, owner text, is_test boolean, owner_name text,
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text, sdk integer
) language sql stable security definer set search_path = public as $$
    select p.id, p.title, p.note, p.packages,
           (select count(*) from plan_votes v where v.plan_id = p.id)::bigint,
           (select count(*) from community_comments c
             where c.kind = 'plan' and c.target = p.id::text)::bigint,
           p.created_at, p.rebooted_at, p.owner, p.is_test,
           coalesce(nullif(u.name, ''), '') as owner_name,
           p.brand, p.model, coalesce(p.market_name, ''), p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk
      from plans p
      left join canta_users u on u.id::text = p.owner
     where coalesce(p_owner, '') <> ''
       and p.owner = p_owner
     order by p.created_at desc
     limit 200
$$;

create or replace function public.plans_for_model(
    p_model text, p_brand text default '', p_rom text default '', p_rom_version integer default 0,
    p_rom_build text default '', p_limit integer default 100
) returns table (
    id bigint, title text, note text, packages text[], votes bigint, comments bigint,
    created_at timestamptz, rebooted_at timestamptz, owner text, is_test boolean, owner_name text,
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text, sdk integer
) language sql stable security definer set search_path = public as $$
    select p.id, p.title, p.note, p.packages,
           (select count(*) from plan_votes v where v.plan_id = p.id)::bigint,
           (select count(*) from community_comments c
             where c.kind = 'plan' and c.target = p.id::text)::bigint,
           p.created_at, p.rebooted_at, p.owner, p.is_test,
           coalesce(nullif(u.name, ''), '') as owner_name,
           p.brand, p.model, coalesce(p.market_name, ''), p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk
      from plans p
      left join canta_users u on u.id::text = p.owner
     where p.reboot_verified
       and coalesce(p_model, '') <> ''
       and lower(p.model) = lower(p_model)
       and (coalesce(p_brand, '') = '' or lower(p.brand) = lower(p_brand))
       and (
             coalesce(p_rom, '') = ''
             or (p.rom = p_rom and (coalesce(p_rom_version, 0) = 0 or p.rom_version = p_rom_version))
           )
       and (
             coalesce(p_rom_build, '') = ''
             or coalesce(p.rom_build, '') = ''
             or p.rom_build = p_rom_build
           )
     order by
        p.is_test asc,
        (select count(*) from plan_votes v where v.plan_id = p.id) desc,
        p.created_at desc
     limit greatest(1, least(coalesce(p_limit, 100), 200))
$$;

create or replace function public.plans_all(p_limit integer default 200)
returns table (
    id bigint, title text, note text, packages text[], votes bigint, comments bigint,
    created_at timestamptz, rebooted_at timestamptz, owner text, is_test boolean, owner_name text,
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text, sdk integer
) language sql stable security definer set search_path = public as $$
    select p.id, p.title, p.note, p.packages,
           (select count(*) from plan_votes v where v.plan_id = p.id)::bigint,
           (select count(*) from community_comments c
             where c.kind = 'plan' and c.target = p.id::text)::bigint,
           p.created_at, p.rebooted_at, p.owner, p.is_test,
           coalesce(nullif(u.name, ''), '') as owner_name,
           p.brand, p.model, coalesce(p.market_name, ''), p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk
      from plans p
      left join canta_users u on u.id::text = p.owner
     where p.reboot_verified
     order by
        p.is_test asc,
        (select count(*) from plan_votes v where v.plan_id = p.id) desc,
        p.created_at desc
     limit greatest(1, least(coalesce(p_limit, 200), 200))
$$;

create or replace function public.plan_device_coverage(p_packages text[], p_limit integer default 5)
returns table (
    brand text,
    model text,
    market_name text,
    rom text,
    rom_version integer,
    rom_build text,
    sdk integer,
    verified bigint
)
language sql stable security definer set search_path = public as $$
    with mine as (
        select array_agg(distinct x order by x) as pkgs from unnest(p_packages) x
    )
    select p.brand, p.model, p.market_name, p.rom, p.rom_version,
           coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
           p.sdk, count(*) as verified
      from plans p, mine
     where p.is_test = false
       and p.packages is not null
       and (select array_agg(distinct y order by y) from unnest(p.packages) y) = mine.pkgs
     group by p.brand, p.model, p.market_name, p.rom, p.rom_version,
              coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)),
              p.sdk
     order by verified desc, p.model asc
     limit greatest(1, least(coalesce(p_limit, 5), 20));
$$;

create or replace function public.plan_catalog() returns table (
    brand text, model text, market_name text, rom text, rom_version integer, rom_build text,
    plans bigint, people bigint, model_plans bigint, model_people bigint
) language sql stable security definer set search_path = public, private as $$
    with builds as (
        select coalesce(nullif(p.brand, ''), 'unknown') as brand,
               p.model,
               min(coalesce(p.codename, '')) as codename,
               p.rom,
               p.rom_version,
               coalesce(nullif(p.rom_build, ''), private.only_build(p.model, p.rom, p.rom_version)) as rom_build,
               count(*)::bigint as plans,
               count(distinct case
                        when coalesce(p.owner, '') <> '' then 'o:' || p.owner
                        else 'r:' || p.reporter
                      end)::bigint as people
          from plans p
         where coalesce(p.model, '') <> ''
         group by 1, 2, 4, 5, 6
    ), codenames as (
        select b.brand, b.model, min(nullif(b.codename, '')) as codename
          from builds b
         group by 1, 2
    ), models as (
        select b.brand,
               b.model,
               coalesce(
                   (select coalesce(p2.market_name, '')
                      from plans p2
                     where lower(p2.model) = lower(b.model)
                       and coalesce(p2.market_name, '') <> ''
                     order by p2.created_at desc
                     limit 1),
                   (select m.market_name
                      from private.model_names m
                     where m.model = lower(b.model)
                     order by m.votes desc
                     limit 1),
                   (select d.market_name
                      from private.device_names d
                     where d.model = lower(b.model)
                     limit 1),
                   (select coalesce(p4.market_name, '')
                      from plans p4
                     where coalesce(cn.codename, '') <> ''
                       and lower(p4.codename) = lower(cn.codename)
                       and coalesce(p4.market_name, '') <> ''
                     order by p4.created_at desc
                     limit 1),
                   ''
               ) as market_name,
               sum(b.plans)::bigint as model_plans,
               (select count(distinct ident)::bigint from (
                    select case
                             when coalesce(p3.owner, '') <> '' then 'o:' || p3.owner
                             else 'r:' || p3.reporter
                           end as ident
                      from plans p3
                     where lower(p3.model) = lower(b.model)
                    union all
                    select 'o:' || u.id::text as ident
                      from canta_users u
                     where lower(u.model) = lower(b.model)
                ) ids) as model_people
          from builds b
          join codenames cn on cn.brand = b.brand and cn.model = b.model
         group by b.brand, b.model, cn.codename
    )
    select b.brand, b.model, coalesce(m.market_name, ''), b.rom, b.rom_version, b.rom_build,
           b.plans, b.people, m.model_plans, m.model_people
      from builds b
      join models m on m.brand = b.brand and m.model = b.model
     order by m.model_people desc, b.brand, b.model, b.rom_version desc, b.rom_build desc, b.rom
$$;

revoke all on function private.only_build(text, text, integer) from public;

notify pgrst, 'reload schema';

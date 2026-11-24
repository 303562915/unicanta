-- 具体版本「取最精确的那个」：HyperOS 3 的 ro.mi.os.version.name 只有 "OS3.0"，完整版本在
-- 增量串里（"OS3.0.0355.0.WNCCNXM"）。于是同一个机型同一个主版本下会同时存在 "OS3.0" 这种粗的值
-- 和 "OS3.0.0355" 这种准的值——读的时候必须选准的那个，而不是第一个非空的。
--
-- 规则：把这一组已知的具体版本按「数字段数」排，段数最多的那个若唯一，就用它；两个并列就是
-- 说不清，回到本行自己的值，或者留空。
--
-- 顺带把 identity_bind 的自愈护栏改成同一把尺子：只有本机报的版本比组内已知的更精确时才补。

create or replace function private.only_build(p_model text, p_rom text, p_rom_version integer)
returns text
language sql
stable
as $$
    with known as (
        select distinct q.rom_build as b
          from public.plans q
         where coalesce(q.rom_build, '') <> ''
           and lower(coalesce(q.model, '')) = lower(coalesce(p_model, ''))
           and q.rom = p_rom
           and q.rom_version = p_rom_version
    ), scored as (
        select b,
               (select count(*) from regexp_matches(b, '[0-9]+', 'g')) as segs
          from known
    )
    select case
             when (select count(*) from scored sc
                    where sc.segs = (select max(segs) from scored)) = 1
               then (select b from scored sc where sc.segs = (select max(segs) from scored))
             else ''
           end
$$;

-- ---------------------------------------------------------------------------------------------
-- 读取：本行的值不空但比组内已知的粗（"OS3.0" 对 "OS3.0.0355"）时，用准的那个。
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
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
           coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                    nullif(p.rom_build, ''), '') as rom_build,
           p.sdk, count(*) as verified
      from plans p, mine
     where p.is_test = false
       and p.packages is not null
       and (select array_agg(distinct y order by y) from unnest(p.packages) y) = mine.pkgs
     group by p.brand, p.model, p.market_name, p.rom, p.rom_version,
              coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                       nullif(p.rom_build, ''), ''),
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
               coalesce(nullif(private.only_build(p.model, p.rom, p.rom_version), ''),
                        nullif(p.rom_build, ''), '') as rom_build,
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

-- ---------------------------------------------------------------------------------------------
-- 写入自愈：本机报的版本要是比组内已知的更精确（或组内还没有），才补空行。
-- ---------------------------------------------------------------------------------------------

create or replace function public.identity_bind(
    p_token text, p_device_key text default '', p_model text default '',
    p_codename text default '', p_privilege text default '',
    p_rom text default '', p_rom_version integer default 0, p_rom_build text default ''
)
returns jsonb
language plpgsql security definer
set search_path = public, private
as $function$
declare
    uid       bigint;
    unam      text;
    key_token text;
    key_hash  text;
    use_token text;
    use_model text;
    use_code  text;
    use_priv  text;
    use_rom   text;
    use_ver   integer;
    use_build text;
    known_build text;
    client_ip text;
    need_geo  boolean;
begin
    if length(coalesce(p_token, '')) < 8 then
        raise exception 'bad token' using errcode = '22023';
    end if;

    use_model := nullif(left(btrim(coalesce(p_model, '')), 120), '');
    use_code  := nullif(left(btrim(coalesce(p_codename, '')), 120), '');
    use_priv  := case
        when btrim(coalesce(p_privilege, '')) in ('root', 'shizuku', 'dhizuku', 'none')
            then btrim(p_privilege)
        else null
    end;
    use_rom   := nullif(left(btrim(coalesce(p_rom, '')), 40), '');
    use_ver   := case when coalesce(p_rom_version, 0) between 1 and 99 then p_rom_version else 0 end;
    use_build := nullif(left(btrim(coalesce(p_rom_build, '')), 60), '');

    key_hash := nullif(left(btrim(coalesce(p_device_key, '')), 128), '');
    if key_hash is not null then
        select token into key_token from canta_device_keys where device_key = key_hash;
        if key_token is not null then
            update canta_device_keys set last_seen_at = now() where device_key = key_hash;
            use_token := key_token;
        else
            insert into canta_device_keys (device_key, token)
            values (key_hash, left(p_token, 120))
            on conflict (device_key) do nothing;
            select token into key_token from canta_device_keys where device_key = key_hash;
            use_token := coalesce(key_token, left(p_token, 120));
        end if;
    else
        use_token := left(p_token, 120);
    end if;

    select id, name into uid, unam from canta_users where token = use_token;

    if uid is null then
        insert into canta_users (token, model, codename, privilege, rom, rom_version, rom_build)
        values (use_token, coalesce(use_model, ''), coalesce(use_code, ''), coalesce(use_priv, ''),
                coalesce(use_rom, ''), coalesce(use_ver, 0), coalesce(use_build, ''))
        on conflict (token) do nothing
        returning id into uid;
        if uid is null then
            select id into uid from canta_users where token = use_token;
        end if;
        unam := '';
    end if;

    update canta_users
       set last_seen_at = now(),
           model       = coalesce(use_model, model),
           codename    = coalesce(use_code, codename),
           privilege   = coalesce(use_priv, privilege),
           rom         = coalesce(use_rom, rom),
           rom_version = case when use_ver > 0 then use_ver else rom_version end,
           rom_build   = coalesce(use_build, rom_build)
     where id = uid;

    -- 自愈：自己名下、具体版本空着的行，用本机报的版本补上——但只在它不比如今已知的更粗时。
    -- "OS3.0" 补到 "OS3.0.0355" 是有收益的；反过来把已知的精确版本覆盖成粗值就是坏事。
    if use_build is not null
       and use_model is not null
       and use_rom is not null then
        begin
            select private.only_build(use_model, use_rom, use_ver) into known_build;
            if coalesce(known_build, '') = ''
               or (select count(*) from regexp_matches(use_build, '[0-9]+', 'g'))
                  >= (select count(*) from regexp_matches(known_build, '[0-9]+', 'g')) then
                update plans p
                   set rom_build = use_build
                 where p.rom_build = ''
                   and p.owner = uid::text
                   and lower(p.rom) = lower(use_rom)
                   and (use_ver = 0 or p.rom_version = use_ver)
                   and lower(p.model) = lower(use_model);
            end if;
        exception when others then
            null;
        end;
    end if;

    begin
        select coalesce(u.ip_region, '') = '' into need_geo from canta_users u where u.id = uid;
        if coalesce(need_geo, false) then
            client_ip := btrim(coalesce(
                nullif(split_part(coalesce(current_setting('request.headers', true)::json ->> 'x-forwarded-for', ''), ',', 1), ''),
                nullif(coalesce(current_setting('request.headers', true)::json ->> 'cf-connecting-ip', ''), '')
            ));
            if client_ip is not null and client_ip <> '' then
                perform private.refresh_region(uid::text, client_ip);
            end if;
        end if;
    exception when others then
        null;
    end;

    return jsonb_build_object('token', use_token, 'id', uid, 'name', coalesce(unam, ''));
end;
$function$;

revoke all on function private.only_build(text, text, integer) from public;

notify pgrst, 'reload schema';

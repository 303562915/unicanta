-- 自愈放宽：每次登录都补自己名下那些只有主版本的旧方案，而不是只补「这台机器第一次报版本」那一次。
--
-- 为什么放宽：190000 把自愈收成一次性 + 60 天，本意是不替旧行编版本。但代价是——已经装过 App 的
-- 老用户，哪怕今天升级到带 p_rom_build 的版本上线，只要那台机器之前报过一次，旧方案就再也没机会
-- 补上，界面永远显示「HyperOS 3」。这正是「已有用户也要能更新线上」要解决的事。
--
-- 换成什么护栏：只有当「同机型 + 同 ROM 族 + 同主版本」下已经知道的具体版本**不超过一种**时才补；
-- 一旦出现两种（16.0.3 与 16.0.501），说明这台机器升级/降级过、谁属于哪一版已经说不清，那时宁可
-- 留空也不贴当前版本。于是老用户一上线就能补全，而不会被标成错的版本。
--
-- 签名不变（8 参），create or replace 即可，权限不用重发。

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
    client_ip text;
    need_geo  boolean;
begin
    if length(coalesce(p_token, '')) < 8 then
        raise exception 'bad token' using errcode = '22023';
    end if;

    use_model := nullif(left(btrim(coalesce(p_model, '')), 120), '');
    use_code  := nullif(left(btrim(coalesce(p_codename, '')), 120), '');
    -- Only the four routes Canta knows; anything else is dropped rather than stored, so a typo in a
    -- future client cannot invent a category on the reporting side.
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
            -- Two installs racing on one phone: the loser reads the winner's binding.
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

    -- Keeps the last value the phone reported: an empty argument (a web build, a read that failed)
    -- must not wipe a model an earlier call knew.
    update canta_users
       set last_seen_at = now(),
           model       = coalesce(use_model, model),
           codename    = coalesce(use_code, codename),
           privilege   = coalesce(use_priv, privilege),
           rom         = coalesce(use_rom, rom),
           rom_version = case when use_ver > 0 then use_ver else rom_version end,
           rom_build   = coalesce(use_build, rom_build)
     where id = uid;

    -- 自愈：每次登录都补自己名下、具体版本还空着的行。护栏是「这一机型这一主版本只认得一种
    -- 具体版本」——多一种就说明机器换过版本，谁属于哪一版说不清，宁可留空。
    if use_build is not null then
        begin
            update plans p
               set rom_build = use_build
             where p.rom_build = ''
               and p.owner = uid::text
               and (use_rom is null or lower(p.rom) = lower(use_rom))
               and (use_ver = 0 or p.rom_version = use_ver)
               and (use_model is null or lower(p.model) = lower(use_model))
               and (
                     not exists (
                         select 1 from plans q
                          where coalesce(q.rom_build, '') <> ''
                            and lower(q.model) = lower(p.model)
                            and q.rom = p.rom
                            and q.rom_version = p.rom_version)
                     or use_build in (
                         select q.rom_build from plans q
                          where coalesce(q.rom_build, '') <> ''
                            and lower(q.model) = lower(p.model)
                            and q.rom = p.rom
                            and q.rom_version = p.rom_version)
                   );
        exception when others then
            -- 补版本号是顺手的事，不能因为它让一次登录失败。
            null;
        end;
    end if;

    -- 属地：只在还没有值的时候问一次。这一段整块包在 exception 里——查属地是锦上添花，
    -- 它出任何问题都不该让一次装机登录失败。
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

notify pgrst, 'reload schema';

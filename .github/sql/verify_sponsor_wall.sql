-- 赞助榜上线自检（只读 + 一次无副作用的空跑）。
select 'table' as check, count(*)::text as value
  from information_schema.tables
 where table_schema = 'public' and table_name = 'sponsor_entries'
union all
select 'functions', string_agg(p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')', ' | ' order by p.proname)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public' and p.proname like 'sponsor%'
union all
select 'private_helpers', string_agg(proname, ', ' order by proname)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'private' and proname like 'sponsor%'
union all
select 'table_grants_anon', coalesce(string_agg(privilege_type, ','), 'none')
  from information_schema.role_table_grants
 where table_schema = 'public' and table_name = 'sponsor_entries' and grantee = 'anon'
union all
select 'policies', coalesce(string_agg(policyname, ','), 'none')
  from pg_policies where schemaname = 'public' and tablename = 'sponsor_entries'
union all
select 'wall_now', public.sponsor_wall()::text
union all
select 'add_anon_rejected', coalesce(public.sponsor_add(20, 'money', 'probe', '', false, '', '')::text, 'NULL(ok)')
-- 空跑：认不出身份必须走拒绝分支、不抛异常。
union all
select 'mine_anon', public.sponsor_mine('')::text
union all
select 'hide_anon', public.sponsor_hide(0, '', '')::text
union all
select 'rows', (select count(*)::text from public.sponsor_entries);
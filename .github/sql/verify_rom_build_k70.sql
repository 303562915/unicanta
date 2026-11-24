-- 只读核对：Redmi K70 系列在 plans / canta_users 里的具体系统版本分布。
-- 目的：解释为什么有些行仍然回落成「HyperOS 3」——是整组都没有具体版本，还是同一主版本下
-- 已经出现了两种以上具体版本（唯一性护栏不成立，故意留空）。

select section, model, name, rom, ver, build, n
  from (
        select 'plans' as section,
               model::text as model,
               coalesce(market_name, '') as name,
               rom::text as rom,
               rom_version::text as ver,
               coalesce(nullif(rom_build, ''), '(EMPTY)') as build,
               count(*)::text as n
          from public.plans
         where market_name ilike '%K70%'
            or lower(model) in ('23113rkc6c', '23013rk75c', '23117rk66c', '2407frk8ec')
         group by 1, 2, 3, 4, 5, 6

        union all

        select 'users' as section,
               model::text as model,
               '' as name,
               rom::text as rom,
               rom_version::text as ver,
               coalesce(nullif(rom_build, ''), '(EMPTY)') as build,
               count(*)::text as n
          from public.canta_users
         where model ilike '%K70%'
            or lower(model) in ('23113rkc6c', '23013rk75c', '23117rk66c', '2407frk8ec')
         group by 1, 2, 3, 4, 5, 6
       ) t
 order by section, model, rom, ver, build

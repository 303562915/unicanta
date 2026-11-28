-- 「不知道」的行为验证（造一行探针，读出来看一眼；清理见 verify_level_ballot_unknown_cleanup.sql）。
--
-- Management API 对多语句只返回**最后一个**结果集，所以这里最后一条就是报告本身。
-- 探针包名固定 `__probe_unknown__`，跑完必须用 cleanup 那份删掉。

select public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'Expert', '依据文字', '__probe_unknown__', 'probe', ''
       ) is not null as expert_accepted;

-- 改成「不知道」：choice 换掉，旧档位留下的依据必须被清空
select public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'unknown', '依据文字', '__probe_unknown__', 'probe', ''
       ) is not null as unknown_accepted;

-- 非法值仍然被拒（null），别把白名单改松了
select public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'bogus', '', '__probe_unknown__', 'probe', ''
       ) is null as bogus_rejected;

select jsonb_build_object(
    'choice', (select choice from public.level_fix_ballots where pkg = '__probe_unknown__'),
    'note', (select note from public.level_fix_ballots where pkg = '__probe_unknown__'),
    'state', private.level_fix_ballot_state('__probe_unknown__', '__probe_unknown__')
) as report;

-- 清掉 verify_level_ballot_unknown_state.sql 造的探针行（`level_fix_ballots` 里不该留这个包名）。
delete from public.level_fix_ballots where pkg = '__probe_unknown__' returning 'cleaned' as step;

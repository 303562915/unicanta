-- 核对「不知道」（choice = unknown）在线上真的通了（配合 level_ballot_unknown.sql）。
--
-- 只读，外加一个自造的探针包 `__probe_unknown__`：跑完最后一步会把它删掉，
-- 不留任何行（`level_fix_ballots` 里不该出现这个包名，最后一条 select 应当给出 0）。

-- 0) 约束长什么样：三个维度都要能看到 'unknown'
select 'constraint' as step, pg_get_constraintdef(oid) as detail
  from pg_constraint
 where conname = 'level_fix_ballots_choice_check';

-- 1) 先正常选一档，并且带上依据（确认 note 存得进）
select 'vote_expert' as step,
       public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'Expert', '依据文字', '__probe_unknown__', 'probe', ''
       ) is not null as accepted;

select 'row_expert' as step, choice, note
  from public.level_fix_ballots
 where pkg = '__probe_unknown__';

-- 2) 改成「不知道」：choice 换掉，旧档位留下的依据必须被清空（否则会被结算当成下发依据）
select 'vote_unknown' as step,
       public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'unknown', '依据文字', '__probe_unknown__', 'probe', ''
       ) is not null as accepted;

select 'row_unknown' as step, choice, note
  from public.level_fix_ballots
 where pkg = '__probe_unknown__';

-- 3) 状态：votes 里能看见 unknown 这一项，mine 也是 unknown
select 'state' as step,
       private.level_fix_ballot_state('__probe_unknown__', '__probe_unknown__') as detail;

-- 4) 非法值仍然被拒（返回 null），别把白名单改松了
select 'vote_bogus' as step,
       public.level_fix_ballot_vote_v2(
           '__probe_unknown__', 'level', 'bogus', '', '__probe_unknown__', 'probe', ''
       ) is null as rejected;

-- 5) 探针包清掉，留下 0 行
delete from public.level_fix_ballots where pkg = '__probe_unknown__';

select 'probe_cleaned' as step, count(*) as left_rows
  from public.level_fix_ballots
 where pkg = '__probe_unknown__';

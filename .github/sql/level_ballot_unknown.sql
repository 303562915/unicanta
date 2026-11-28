-- 三个维度都加一项「不知道」（choice = `unknown`）。
--
-- 为什么加：级别修正页的每一组都是"要么选这个、要么选那个"，而绝大多数用户对一个没见过的包
-- 其实分不清该标哪一档。被逼着选一个的后果不是"多一票"，而是**票箱里多一票噪声**，还会作为
-- 一条记录永久留在「修正历史」里。所以每组最后一项是「不知道」：我看了、但我不表态。
--
-- 三条规则（客户端与服务端必须一致）：
--   1. `unknown` 是合法 choice，三个维度都接受，一人一维度仍然只有一票（改主意就改那一票）；
--   2. 它**不进任何百分比的分母**，也不参与结算：`private.level_fix_ballot_settle` 里
--      级别总数、胜出档位、后果、系统应用四处全部 `choice <> 'unknown'`；
--   3. 它**不下发**：`level_fixes.removal` 的约束只认四个档位，漏过滤就会把 removal='unknown'
--      写进 `site/bloat/level_fixes.json`，所有客户端都读不懂那一行。
--
-- 客户端那一半在 `LevelBallotChoices.UNKNOWN` / `LevelBallots.percent` / `LevelFixScreen`。
--
-- Run after 20261128170000_level_ballot_history.sql.

-- ---------------------------------------------------------------- 约束

alter table public.level_fix_ballots
    drop constraint if exists level_fix_ballots_choice_check;

alter table public.level_fix_ballots
    add constraint level_fix_ballots_choice_check
        check (
            (dimension = 'level'  and choice in ('Recommended', 'Advanced', 'Expert', 'Unsafe', 'unknown')) or
            (dimension = 'impact' and choice in ('breaks', 'bootloop', 'unknown')) or
            (dimension = 'system' and choice in ('yes', 'no', 'unknown'))
        );

-- ---------------------------------------------------------------- 写入

/** 投票（旧签名，留给还在用旧版 APK 的手机）：白名单多一个 `unknown`。 */
create or replace function public.level_fix_ballot_vote(
    p_pkg text,
    p_dimension text,
    p_choice text,
    p_note text default '',
    p_voter text default '',
    p_token text default ''
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
    pkg_text text := left(btrim(coalesce(p_pkg, '')), 200);
    dim_text text := btrim(coalesce(p_dimension, ''));
    choice_text text := btrim(coalesce(p_choice, ''));
    note_text text := left(btrim(coalesce(p_note, '')), 300);
    voter_text text := left(
        coalesce(nullif(btrim(private.identity_of(coalesce(p_token, ''))), ''), btrim(coalesce(p_voter, ''))),
        60
    );
begin
    perform private.throttle_write('level_fix_ballot', 30, 60);

    if pkg_text = '' or voter_text = '' then
        return null;
    end if;

    if dim_text = 'level' then
        if choice_text not in ('Recommended', 'Advanced', 'Expert', 'Unsafe', 'unknown') then
            return null;
        end if;
    elsif dim_text = 'impact' then
        if choice_text not in ('breaks', 'bootloop', 'unknown') then
            return null;
        end if;
    elsif dim_text = 'system' then
        if choice_text not in ('yes', 'no', 'unknown') then
            return null;
        end if;
    else
        return null;
    end if;

    -- 「不知道」没有依据可挂：级别那一票的 note 只对真实档位有意义，混进来会被结算当成下发依据。
    if choice_text = 'unknown' then
        note_text := '';
    end if;

    insert into level_fix_ballots (pkg, dimension, choice, voter, note)
    values (pkg_text, dim_text, choice_text, voter_text, note_text)
    on conflict (pkg, dimension, voter) do update
        set choice = excluded.choice,
            note = case when excluded.dimension = 'level' and excluded.note <> ''
                        then excluded.note
                        else level_fix_ballots.note
                   end,
            updated_at = now();

    perform private.level_fix_ballot_settle(pkg_text);

    return private.level_fix_ballot_state(pkg_text, voter_text);
end;
$$;

/** 投票（带显示名版，当前客户端走这个）：同样的白名单。 */
create or replace function public.level_fix_ballot_vote_v2(
    p_pkg text,
    p_dimension text,
    p_choice text,
    p_note text default '',
    p_voter text default '',
    p_name text default '',
    p_token text default ''
)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
    pkg_text text := left(btrim(coalesce(p_pkg, '')), 200);
    dim_text text := btrim(coalesce(p_dimension, ''));
    choice_text text := btrim(coalesce(p_choice, ''));
    note_text text := left(btrim(coalesce(p_note, '')), 300);
    name_text text := left(btrim(coalesce(p_name, '')), 40);
    voter_text text := left(
        coalesce(nullif(btrim(private.identity_of(coalesce(p_token, ''))), ''), btrim(coalesce(p_voter, ''))),
        60
    );
begin
    perform private.throttle_write('level_fix_ballot', 30, 60);

    if pkg_text = '' or voter_text = '' then
        return null;
    end if;

    if dim_text = 'level' then
        if choice_text not in ('Recommended', 'Advanced', 'Expert', 'Unsafe', 'unknown') then
            return null;
        end if;
    elsif dim_text = 'impact' then
        if choice_text not in ('breaks', 'bootloop', 'unknown') then
            return null;
        end if;
    elsif dim_text = 'system' then
        if choice_text not in ('yes', 'no', 'unknown') then
            return null;
        end if;
    else
        return null;
    end if;

    if choice_text = 'unknown' then
        note_text := '';
    end if;

    insert into level_fix_ballots (pkg, dimension, choice, voter, voter_name, note)
    values (pkg_text, dim_text, choice_text, voter_text, name_text, note_text)
    on conflict (pkg, dimension, voter) do update
        set choice = excluded.choice,
            voter_name = coalesce(nullif(excluded.voter_name, ''), level_fix_ballots.voter_name),
            -- 改成「不知道」时把旧档位留下的依据一并清掉，否则结算会取到一条不属于任何档位的说明。
            note = case
                       when excluded.choice = 'unknown' then ''
                       when excluded.dimension = 'level' and excluded.note <> ''
                           then excluded.note
                       else level_fix_ballots.note
                   end,
            updated_at = now();

    perform private.level_fix_ballot_settle(pkg_text);

    return private.level_fix_ballot_state(pkg_text, voter_text);
end;
$$;

-- ---------------------------------------------------------------- 结算

/**
 * 级别维度过门槛就写进下发通道；后果与系统应用取票最多的一项一起带出去。
 *
 * 与上一版唯一的差别：四处统计全部排除 `unknown`。它不是一档、也不是一个后果——
 * 让它进分母就能用一句"我不知道"把 70% 的占比门槛稀释掉；让它进胜出候选，就会把
 * `removal = 'unknown'` / `impact = 'unknown'` 写进 `level_fixes`，客户端与下发文件都读不懂。
 */
create or replace function private.level_fix_ballot_settle(p_pkg text)
returns void language plpgsql security definer set search_path = public as $$
declare
    pkg_text text := left(coalesce(p_pkg, ''), 200);
    needed_level int := private.level_fix_needed(left(coalesce(p_pkg, ''), 200));
    share_level numeric := private.level_fix_share_needed();
    total_level bigint;
    winner text;
    winner_votes bigint;
    top_impact text;
    top_system text;
    note_text text;
    row_id bigint;
begin
    select count(*) into total_level
      from level_fix_ballots
     where pkg = pkg_text and dimension = 'level' and choice <> 'unknown';

    if coalesce(total_level, 0) = 0 then
        return;
    end if;

    select t.choice, t.votes into winner, winner_votes
      from (
          select choice, count(*) as votes
            from level_fix_ballots
           where pkg = pkg_text and dimension = 'level' and choice <> 'unknown'
           group by choice
      ) t
     where t.votes >= needed_level
       and t.votes >= share_level * total_level
     order by t.votes desc, t.choice asc
     limit 1;

    if winner is null then
        return;
    end if;

    select t.choice into top_impact
      from (
          select choice, count(*) as votes
            from level_fix_ballots
           where pkg = pkg_text and dimension = 'impact' and choice <> 'unknown'
           group by choice
           order by count(*) desc, (choice = 'breaks') desc
           limit 1
      ) t;

    select t.choice into top_system
      from (
          select choice, count(*) as votes
            from level_fix_ballots
           where pkg = pkg_text and dimension = 'system' and choice <> 'unknown'
           group by choice
           order by count(*) desc, (choice = 'yes') desc
           limit 1
      ) t;

    -- 依据：这一档最近一条非空备注（选填，没写就是空串）；「不知道」那几行不算。
    select b.note into note_text
      from level_fix_ballots b
     where b.pkg = pkg_text
       and b.dimension = 'level'
       and b.choice <> 'unknown'
       and b.note <> ''
     order by b.updated_at desc
     limit 1;

    -- 同一包、同一档、已经 applied 的那行就地更新（后果/系统应用可能变了）。
    update level_fixes
       set impact = top_impact,
           system_app = case when top_system is null then null else (top_system = 'yes') end,
           updated_at = now()
     where pkg = pkg_text
       and removal = winner
       and applied = true
       and superseded_at is null
    returning id into row_id;

    if row_id is null then
        insert into level_fixes (pkg, removal, notes, author, author_id, applied, applied_at, impact, system_app)
        values (
            pkg_text,
            winner,
            jsonb_build_object('zh', coalesce(note_text, ''), 'en', coalesce(note_text, '')),
            '',
            '',
            true,
            now(),
            top_impact,
            case when top_system is null then null else (top_system = 'yes') end
        )
        returning id into row_id;
    end if;

    -- 同一个包只认一个结论：其它还没下发、或旧的 applied 行全部作废（保留行便于追溯）。
    update level_fixes
       set superseded_at = coalesce(superseded_at, now()),
           updated_at = now()
     where pkg = pkg_text
       and id <> row_id
       and superseded_at is null;
end;
$$;

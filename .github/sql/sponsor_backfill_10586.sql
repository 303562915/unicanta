-- 补录一笔赞助（维护者手工登记）：用户 id 10586 → AI 邀请。
--
-- 为什么需要这个脚本：`public.sponsor_add` 只认 `private.identity_of(p_token)`，认不出来就退回自报号码，
-- 维护者手里只有"用户 id"，没有对方的 token，所以按 id 直接写 `sponsor_entries`。
--
-- 名字用 `canta_users.name`（榜上显示的名字），取不到就写 `#10586`；身份列写 id 的数字串，
-- 客户端按它拼头像地址 `unicanta/头像/10586.jpg` 并点进主页（`Screen.Uploader.routeFor("10586")`）。
--
-- `source = 'manual'` 是这类行的记号：日后要区分"自己提交的"和"维护者补录的"，看这一列。
--
-- 幂等：同一个人同一形式已经有一笔未隐藏的 `manual` 记录时不再重复写。

do $$
declare
    uid      text := '10586';
    uname    text;
    existing bigint;
begin
    select nullif(btrim(u.name), '') into uname
      from public.canta_users u
     where u.id = uid::bigint;

    if not found then
        raise exception '用户 % 不存在（canta_users 里没有这一行），先确认 id', uid
            using errcode = '22023';
    end if;

    if uname is null then
        uname := '#' || uid;
    end if;

    select e.id into existing
      from public.sponsor_entries e
     where e.owner = uid
       and e.kind = 'ai'
       and not e.hidden
       and e.source = 'manual'
     limit 1;

    if existing is not null then
        raise notice '用户 % 已经有一笔 manual 的 AI 邀请记录（id=%），这次跳过', uid, existing;
    else
        insert into public.sponsor_entries (owner, name, amount_yuan, kind, note, anonymous, source)
        values (uid, left(uname, 40), 0, 'ai', '维护者补录', false, 'manual');
    end if;
end $$;

-- 落库回读（只读）：这一位现在在榜上是什么样，以及整张榜的汇总。
select 'entry' as kind,
       (select jsonb_build_object(
                   'id', e.id, 'owner', e.owner, 'name', e.name,
                   'amount_yuan', e.amount_yuan, 'kind', e.kind,
                   'source', e.source, 'hidden', e.hidden)
          from public.sponsor_entries e
         where e.owner = '10586'
         order by e.created_at desc
         limit 1) as detail
union all
select 'wall', public.sponsor_wall();
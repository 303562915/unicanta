-- 两件事：
--   1. 让「群聊AI」成为两个官方群（12 灰度测试群、18 Bug 反馈）的成员，群主在成员卡里看得到它；
--   2. `conversation_detail` 里的人数与成员表**只给群主**看：这两个群是同一个人的，
--      普通成员不该从接口里读到"群里一共多少人 / 都有谁"。
--
-- 注意只对群聊生效（`kind = 'group'` 且 `me <> created_by`）。私聊没有群主这一说，
-- `members` 里那一个对面的人正是聊天页拿对方身份的来源，清空它会连带把私聊的主页入口弄坏。

insert into public.conversation_members (conversation_id, member, member_name, pinned_at)
select g.cid, 'ai', '群聊AI', now()
  from (values (12::bigint), (18::bigint)) as g (cid)
 where exists (select 1 from public.conversations c where c.id = g.cid and c.kind = 'group')
on conflict (conversation_id, member) do nothing;

CREATE OR REPLACE FUNCTION public.conversation_detail(p_owner text, p_conversation bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'private'
AS $function$
declare
    me  text := nullif(left(btrim(coalesce(p_owner, '')), 40), '');
    bag jsonb;
begin
    perform private.throttle_write('conv_detail', 240, 60);

    if me is null or p_conversation is null
       or not exists (
           select 1 from public.conversation_members k
            where k.conversation_id = p_conversation and k.member = me
       ) then
        return '{}'::jsonb;
    end if;

    select jsonb_build_object(
               'id', c.id,
               'kind', c.kind,
               'title', c.title,
               'created_by', coalesce(c.created_by, ''),
               'announcement', coalesce(c.announcement, ''),
               'intro', coalesce(c.intro, ''),
               -- 群主打开的「允许发图片」：聊天页的图片入口按它显示，后端也按它裁决。
               'allow_image', coalesce(c.allow_image, false),
               'muted', coalesce(mine.muted, false),
               'my_nickname', coalesce(mine.nickname, ''),
               'my_folded', coalesce(mine.folded, false),
               -- 人数只给群主：官方群几千人，成员数不该让每个人都能读出来。旧后端没有这个键。
               'member_count', case
                   when c.kind = 'group' and coalesce(c.created_by, '') <> me then 0
                   else (
                       select count(*)
                         from public.conversation_members m2
                        where m2.conversation_id = c.id
                   )
               end,
               -- 成员表同理：群聊非群主返回空数组（私聊照旧，它是聊天页认人的来源）。
               'members', case
                   when c.kind = 'group' and coalesce(c.created_by, '') <> me then '[]'::jsonb
                   else coalesce((
                       select jsonb_agg(jsonb_build_object(
                                  'owner', k.member,
                                  'name', coalesce(nullif(private.social_name(k.member), ''), k.member_name, '')
                              ) order by k.joined_at asc, k.member asc)
                         from (
                             select m.member, m.member_name, m.joined_at
                               from public.conversation_members m
                              where m.conversation_id = p_conversation
                              order by m.joined_at asc, m.member asc
                              limit 200
                         ) k
                   ), '[]'::jsonb)
               end,
               'created_at', to_char(c.created_at at time zone 'utc', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
           )
      into bag
      from public.conversations c
      join public.conversation_members mine
        on mine.conversation_id = c.id and mine.member = me
     where c.id = p_conversation;

    return coalesce(bag, '{}'::jsonb);
end;
$function$;

notify pgrst, 'reload schema';

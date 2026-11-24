-- conversation_detail：多回一个 member_count（群里一共多少人）。
--
-- 为什么需要：`members` 只回前 200 个（头像墙够用，几千人的群不该把整张成员表搬给客户端），
-- 于是客户端把 `members.size` 人数读成了 200——官方群现在注册即全员加入，2328 人显示成
-- 「200/200」，看起来像"群满了"。人数要单独数，和成员列表分开。
--
-- 其余字段、排序、limit 200、签名、throttle 一个都没动；在线上 `pg_get_functiondef` 原文上
-- 只插入 member_count 这一项（含中文注释），所以是 create or replace，权限不用重发。

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
               -- 群里一共多少人：`members` 只有前 200 个，人数不能拿它数。
               'member_count', (
                   select count(*)
                     from public.conversation_members m2
                    where m2.conversation_id = c.id
               ),
               'members', coalesce((
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
               ), '[]'::jsonb),
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

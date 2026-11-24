-- 只读核对三件事：
--   1. 「群聊AI」(member = 'ai') 是不是两个官方群的成员；
--   2. 群主看 conversation_detail 拿得到人数与成员表；
--   3. 普通成员看同一个群，人数是 0、成员表是空（他们不该读到这两个群的规模）。

select conversation_id, member, member_name
  from public.conversation_members
 where member = 'ai'
 order by conversation_id;

select (public.conversation_detail('10003', 18) ->> 'member_count') as owner_sees_count,
       jsonb_array_length(public.conversation_detail('10003', 18) -> 'members') as owner_sees_members,
       (public.conversation_detail('10003', 18) ->> 'allow_image') as owner_sees_allow_image;

with other as (
    select member
      from public.conversation_members
     where conversation_id = 18
       and member not in ('10003', 'ai')
     order by joined_at asc
     limit 1
)
select (select member from other) as sampled_member,
       (public.conversation_detail((select member from other), 18) ->> 'member_count') as other_sees_count,
       jsonb_array_length(public.conversation_detail((select member from other), 18) -> 'members') as other_sees_members;

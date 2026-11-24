-- 只读核对：两个官方群各多少人、多少人置顶；以及 conversation_detail 有没有回 member_count。
select c.id, c.title,
       (select count(*) from public.conversation_members m where m.conversation_id = c.id) as members,
       (select count(*) from public.conversation_members m where m.conversation_id = c.id and m.pinned_at is not null) as pinned
  from public.conversations c
 where c.id in (12, 18)
 order by c.id;

select (public.conversation_detail('10003', 18) ->> 'member_count') as detail_member_count,
       jsonb_array_length(public.conversation_detail('10003', 18) -> 'members') as detail_members_len;

-- 两个官方群允许成员发图片。
--
-- 症状：群里的人说"发不了图片"。原因不在图床，在群开关：`conversations.allow_image`
-- 默认 false，客户端 `ChatScreen` 的 `imagesAllowed = !grouped || detail.allowImage || isOwner`
-- 因此对普通成员恒为 false，图片入口直接不给；后端 `message_send_image` 那一侧同样会拒。
-- 12（灰度测试群）和 18（Bug 反馈）都是自己的群，成员当然要能发图。
--
-- 顺带核对：两份人数、allow_image 现状。

update public.conversations
   set allow_image = true
 where id in (12, 18);

select c.id, c.title, c.allow_image,
       (select count(*) from public.conversation_members m where m.conversation_id = c.id) as members
  from public.conversations c
 where c.id in (12, 18)
 order by c.id;

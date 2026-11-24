-- 帖子 + 码池的端到端自测：整段在一个事务里跑完再 rollback，不留数据。
begin;

-- 两个假身份（token 至少 8 位才会被 identity_of 认出来）
insert into public.canta_users (id, token, name) values (990101, '__selftest_posts_a', '测试甲');
insert into public.canta_users (id, token, name) values (990102, '__selftest_posts_b', '测试乙');

-- 甲发一帖（带分类和标签）
select public.post_add('自测帖', '正文一句话', '甲', '分享', array['测试','标签'], '__selftest_posts_a');

-- 乙回一句、点个赞
select public.post_comment_add(
    (select id from public.community_posts where title = '自测帖' order by id desc limit 1),
    '第一条回复', '乙', '', '{}', '__selftest_posts_b');
select public.post_like(
    (select id from public.community_posts where title = '自测帖' order by id desc limit 1),
    true, '__selftest_posts_b');

-- 甲挂一件 CDK 商品（两个码），乙用积分兑换其中一个
select public.point_offer_publish_codes(
    '自测码商品', '说明', 1, E'CODE-A\nCODE-B', 'invite', '甲', '990101', '__selftest_posts_a');
select private.point_apply('990102', 10, 'selftest', 'seed');
select public.point_offer_redeem(
    (select id from public.point_offers where title = '自测码商品' order by id desc limit 1),
    '', '__selftest_posts_b');

-- 汇总：只看最终状态
select jsonb_build_object(
    'posts', (select count(*) from public.community_posts where title = '自测帖'),
    'comments', (select count(*) from public.post_comments pc
                   join public.community_posts p on p.id = pc.post_id where p.title = '自测帖'),
    'likes', (select count(*) from public.post_likes pl
                join public.community_posts p on p.id = pl.post_id where p.title = '自测帖'),
    'category', (select category from public.community_posts where title = '自测帖'),
    'tags', (select to_jsonb(tags) from public.community_posts where title = '自测帖'),
    'posts_list_rows', jsonb_array_length(public.posts_list('', '')),
    'codes_total', (select count(*) from public.point_offer_codes),
    'codes_taken', (select count(*) from public.point_offer_codes where taken_by <> ''),
    'buyer_balance', (select balance from public.point_accounts where owner = '990102'),
    'seller_balance', (select balance from public.point_accounts where owner = '990101'),
    'delivered_code', (select deliverable from public.point_orders order by id desc limit 1),
    'my_codes', jsonb_array_length(public.point_my_codes('__selftest_posts_b'))
) as selftest;

rollback;

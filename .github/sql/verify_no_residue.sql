-- 确认端到端自测没有在真实库里留下任何东西（期望全部为 0）。
select jsonb_build_object(
  'posts', (select count(*) from public.community_posts),
  'comments', (select count(*) from public.post_comments),
  'likes', (select count(*) from public.post_likes),
  'cdk_codes', (select count(*) from public.point_offer_codes),
  'offers', (select count(*) from public.point_offers),
  'test_users', (select count(*) from public.canta_users where id >= 990101),
  'test_accounts', (select count(*) from public.point_accounts where owner in ('990101','990102'))
) as residue;

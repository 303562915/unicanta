for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /odm/etc/audio/trackefx-config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml /system_ext/etc/audio/trackefx_config.xml; do
[ -f "$f" ] && { echo "__yes__"; exit 0; }
done
CFG=$(find /odm/etc /vendor/etc /system/etc /system_ext/etc /product/etc /my_product/etc -maxdepth 4 \( -iname '*trackefx*' -o -iname '*track_efx*' -o -iname '*scene*partition*' \) 2>/dev/null | head -n 1)
[ -n "$CFG" ] && { echo "__yes__"; exit 0; }
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /system_ext/etc/audio /product/etc/audio /my_product/etc/audio /odm/etc /vendor/etc; do
for f in "$d"/*.xml "$d"/*.conf "$d"/*.cfg "$d"/*.bin; do
[ -f "$f" ] || continue
grep -ailE 'trackefx|TrackCustomEfx|scene\.partition' "$f" 2>/dev/null && { echo "__yes__"; exit 0; }
done
done
echo "__no__"
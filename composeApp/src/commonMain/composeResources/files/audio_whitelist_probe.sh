for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml; do if [ -f "$f" ]; then echo "__yes__"; exit 0; fi; done
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /odm/etc /vendor/etc; do
for f in "$d"/*.xml "$d"/*.conf "$d"/*.cfg; do
[ -f "$f" ] || continue
if grep -qilE 'trackefx|TrackCustomEfx|scene\.partition' "$f" 2>/dev/null; then echo "__yes__"; exit 0; fi
done
done
echo "__no__"

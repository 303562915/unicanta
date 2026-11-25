P=persist.sys.scene.partition.level
echo "##LEVEL##$(getprop "$P")"
CFG=
for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /odm/etc/audio/trackefx-config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml /system_ext/etc/audio/trackefx_config.xml; do
[ -f "$f" ] && { CFG="$f"; break; }
done
# 换名也算：澎湃OS 3 有的机型把这份名单换了目录、换了后缀（甚至塞进 .bin），按内容找比按名字找稳。
if [ -z "$CFG" ]; then
CFG=$(find /odm/etc /vendor/etc /system/etc /system_ext/etc /product/etc /my_product/etc -maxdepth 4 \( -iname '*trackefx*' -o -iname '*track_efx*' -o -iname '*scene*partition*' \) 2>/dev/null | head -n 1)
fi
if [ -z "$CFG" ]; then
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /system_ext/etc/audio /product/etc/audio /my_product/etc/audio /odm/etc /vendor/etc; do
for f in "$d"/*.xml "$d"/*.conf "$d"/*.cfg "$d"/*.bin; do
[ -f "$f" ] || continue
if grep -ailE 'trackefx|TrackCustomEfx|scene\.partition' "$f" 2>/dev/null; then CFG="$f"; break; fi
done
[ -n "$CFG" ] && break
done
fi
echo "##CFG##$CFG"
if [ -n "$CFG" ]; then
{ grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$CFG"; grep -aoE '>[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+<' "$CFG"; } 2>/dev/null | tr -d '"><\000' | grep -v '\.xml$' | sort -u | sed 's|^|##PKG##|'
echo "##XMLHEAD##$(head -c 400 "$CFG" 2>/dev/null | tr -d '\000' | tr '\n' ' ')"
fi
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /system_ext/etc/audio /product/etc/audio /my_product/etc/audio; do echo "##DIR##$d: $(ls -1 "$d" 2>/dev/null | tr '\n' ' ')"; done
find /odm/etc /vendor/etc /system/etc /product/etc -maxdepth 4 \( -iname '*track*' -o -iname '*efx*' -o -iname '*scene*' \) 2>/dev/null | head -n 24 | sed 's|^|##FILE##|'
pm list packages 2>/dev/null | sed 's|^package:|##INST##|'
getprop | grep -iE 'scene|trackefx|track_efx|whitelist|audio.*(white|bypass|scene)' | head -n 40 | sed 's|^|##PROP##|'
echo "##SETTING##debug_audio_cloud=$(settings get global debug_audio_cloud 2>/dev/null)"
echo "##SETTING##test_audio_cloud=$(settings get global test_audio_cloud 2>/dev/null)"
echo "##SETTING##debug_audio=$(settings get global debug_audio 2>/dev/null)"
echo "##END##"
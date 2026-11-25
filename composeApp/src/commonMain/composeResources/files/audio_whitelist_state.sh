P=persist.sys.scene.partition.level
echo "##LEVEL##$(getprop "$P")"
CFG=
for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml; do if [ -f "$f" ]; then CFG="$f"; break; fi; done
if [ -z "$CFG" ]; then
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /odm/etc /vendor/etc; do
for f in "$d"/*.xml "$d"/*.conf "$d"/*.cfg; do
[ -f "$f" ] || continue
if grep -qilE 'trackefx|TrackCustomEfx|scene\.partition' "$f" 2>/dev/null; then CFG="$f"; break; fi
done
[ -n "$CFG" ] && break
done
fi
echo "##CFG##$CFG"
if [ -n "$CFG" ]; then
{ grep -oE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$CFG"; grep -oE '>[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+<' "$CFG"; } 2>/dev/null | tr -d '"><' | grep -v '\.xml$' | sort -u | sed 's|^|##PKG##|'
echo "##XMLHEAD##$(head -c 400 "$CFG" | tr '\n' ' ')"
fi
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio; do echo "##DIR##$d: $(ls -1 "$d" 2>/dev/null | tr '\n' ' ')"; done
find /odm/etc /vendor/etc -maxdepth 3 \( -iname '*track*' -o -iname '*efx*' \) 2>/dev/null | head -n 20 | sed 's|^|##FILE##|'
pm list packages 2>/dev/null | sed 's|^package:|##INST##|'
getprop | grep -iE 'scene|trackefx|track_efx|whitelist|audio.*(white|bypass|scene)' | head -n 40 | sed 's|^|##PROP##|'
echo "##SETTING##debug_audio_cloud=$(settings get global debug_audio_cloud 2>/dev/null)"
echo "##SETTING##test_audio_cloud=$(settings get global test_audio_cloud 2>/dev/null)"
echo "##SETTING##debug_audio=$(settings get global debug_audio 2>/dev/null)"
echo "##END##"

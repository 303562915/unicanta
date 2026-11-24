P=persist.sys.scene.partition.level
echo "##LEVEL##$(getprop "$P")"
CFG=
for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml; do if [ -f "$f" ]; then CFG="$f"; break; fi; done
echo "##CFG##$CFG"
if [ -n "$CFG" ]; then
{ grep -oE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$CFG"; grep -oE '>[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+<' "$CFG"; } 2>/dev/null | tr -d '"><' | grep -v '\.xml$' | sort -u | sed 's|^|##PKG##|'
fi
pm list packages 2>/dev/null | sed 's|^package:|##INST##|'
echo "##END##"

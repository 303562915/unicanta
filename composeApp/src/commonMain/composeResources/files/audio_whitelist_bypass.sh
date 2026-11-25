CFG=
for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /odm/etc/audio/trackefx-config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml /system_ext/etc/audio/trackefx_config.xml; do
[ -f "$f" ] && { CFG="$f"; break; }
done
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
S=/data/local/tmp/canta_audio_whitelist.state
P=persist.sys.scene.partition.level
# 没找到名单文件也照写：名单可能由云控下发，本机没有文件不代表开关无效。写下去有没有用，得听一遍才知道。
if [ -n "$CFG" ]; then echo "白名单配置：$CFG"; else echo "白名单配置：这台机器上没找到名单文件（名单可能由云控下发），先把开关写下去试"; fi
CUR=$(getprop "$P")
if [ "$CUR" = "-1" ]; then echo "已经是解除状态：$P = -1，不用再改"; exit 0; fi
if printf '%s\n%s\n' "$P" "$CUR" > "$S"; then echo "目标属性：$P（当前值 ${CUR:-未设置}，原值已记到 $S）"; else echo "原值记不下来 STATE_FAIL：$S 写不进去，为了还能恢复，这次不动属性"; exit 1; fi
PL='{"propertySet":[{"name":"speaker_scene_bypass","versionCode":"1","secret":false,"key":"'"$P"'","value":"-1"}]}'
settings put global debug_audio_cloud "$PL"
case "$(settings get global debug_audio_cloud)" in *speaker_scene_bypass*) echo "调试项已写入：云控会把它当成一条属性下发";; *) echo "调试项回读不符 SET_FAIL"; exit 1;; esac
settings put global test_audio_cloud "-1_$(date +%s)"
echo "已触发云控刷新，等属性变更（最多 12 秒）…"
i=0
while [ "$i" -lt 12 ]; do sleep 1; if [ "$(getprop "$P")" = "-1" ]; then break; fi; i=$((i+1)); done
settings delete global debug_audio_cloud >/dev/null 2>&1
settings delete global test_audio_cloud >/dev/null 2>&1
if [ "$(getprop "$P")" = "-1" ]; then
echo "已解除：$P = -1（原值 ${CUR:-未设置}）"
if [ -z "$CFG" ]; then echo "注意：这台机器没找到名单文件，开关写成功了但效果要自己听一遍；没有变化就点「恢复」写回原值。"; else echo "抖音这类应用的外放会变大、低频回来，其他应用不受影响。点「恢复」写回原值。"; fi
exit 0
fi
if [ "$(id -u)" != "0" ] && command -v su >/dev/null 2>&1; then echo "云控没接上，改用 root 直接写属性…"; su -c "setprop $P -1" </dev/null >/dev/null 2>&1; if [ "$(getprop "$P")" = "-1" ]; then echo "已解除（root 直写）：$P = -1（原值 ${CUR:-未设置}）"; exit 0; fi; fi
echo "没生效 TIMEOUT：$P 仍然是 $(getprop "$P")"
echo "这台机器可能关掉了云控调试入口（debug_audio_cloud）。用 root 身份再跑一次，或把这段输出发给我们。"
exit 1
S=/data/local/tmp/canta_audio_whitelist.state
P=persist.sys.scene.partition.level
CFG=
for f in /odm/etc/audio/trackefx_config.xml /odm/etc/audio/track_efx_config.xml /odm/etc/audio/trackefx-config.xml /vendor/etc/audio/trackefx_config.xml /vendor/etc/audio/track_efx_config.xml /system/etc/audio/trackefx_config.xml /system_ext/etc/audio/trackefx_config.xml; do
[ -f "$f" ] && { CFG="$f"; break; }
done
if [ -z "$CFG" ]; then
CFG=$(find /odm/etc /vendor/etc /system/etc /system_ext/etc /product/etc /my_product/etc -maxdepth 4 \( -iname '*trackefx*' -o -iname '*track_efx*' -o -iname '*scene*partition*' \) 2>/dev/null | head -n 1)
fi
# 原值分三种，恢复的动作也不同：
#   OLD=1      ROM 出厂值，写回 1
#   OLD=空     这台机器**本来没有这条属性**（K90 Pro Max 这类），恢复 = 把属性删回「不存在」，
#              写 1 是凭空造一条别人没见过的属性，可能反而把别的场景处理带歪
#   记录文件没了  /data/local/tmp 会被清（重启、清缓存、换包），可属性是 persist 的，还留着 -1。
#              这时按有名单文件与否判断出厂值：有名单 = 1，没有名单 = 删掉
OLD=
HAVE_STATE=0
if [ -f "$S" ]; then
HAVE_STATE=1
OLD=$(sed -n '2p' "$S")
echo "记录文件：$S（原值 ${OLD:-不存在}）"
else
CUR0=$(getprop "$P")
echo "没有记录文件：$S 不在（大概是被清过）。当前 $P = ${CUR0:-不存在}"
if [ -z "$CUR0" ]; then echo "属性本来就不存在，没有任何要恢复的东西"; exit 0; fi
if [ -n "$CFG" ]; then echo "这台机器有名单文件，出厂值按 1 恢复"; else echo "这台机器没有名单文件：这条开关本来就不该存在，恢复 = 把属性删掉"; fi
fi
CUR=$(getprop "$P")
if [ "$HAVE_STATE" = "1" ] && [ -z "$OLD" ] && [ -z "$CUR" ]; then echo "已经回到原样：$P 不存在"; exit 0; fi
if [ "$HAVE_STATE" = "1" ] && [ -n "$OLD" ] && [ "$CUR" = "$OLD" ]; then echo "已经是原值 $OLD，白名单处理本来就生效"; exit 0; fi
if [ "$HAVE_STATE" = "0" ] && [ -z "$CFG" ]; then
echo "目标属性：$P（当前 $CUR，原值 不存在 → 删掉）"
else
echo "目标属性：$P（当前 ${CUR:-不存在}，原值 ${OLD:-$([ -n "$CFG" ] && echo 1)}）"
fi
VAL="$OLD"
if [ "$HAVE_STATE" = "0" ] && [ -n "$CFG" ]; then VAL=1; fi
# 空值 = 删属性。云控调试项只能下发 propertySet，所以先试 setprop 1 之外的路径：直接删。
if [ -z "$VAL" ]; then
echo "按「删掉这条属性」恢复…"
settings put global debug_audio_cloud '{"propertyDelete":[{"name":"speaker_scene_restore","key":"'"$P"'"}]}' 2>/dev/null
settings put global test_audio_cloud "restore_del_$(date +%s)"
echo "已触发云控刷新，等属性消失（最多 12 秒）…"
i=0
while [ "$i" -lt 12 ]; do sleep 1; if [ -z "$(getprop "$P")" ]; then break; fi; i=$((i+1)); done
settings delete global debug_audio_cloud >/dev/null 2>&1
settings delete global test_audio_cloud >/dev/null 2>&1
if [ -z "$(getprop "$P")" ]; then echo "已恢复：$P 已删掉，回到这台机器出厂的样子"; exit 0; fi
if [ "$(id -u)" != "0" ] && command -v su >/dev/null 2>&1; then
echo "云控没接上，改用 root 删属性…"
su -c "settings delete global $P; setprop $P ''" </dev/null >/dev/null 2>&1
if [ -z "$(getprop "$P")" ]; then echo "已恢复（root 删属性）：$P 不存在"; exit 0; fi
fi
echo "删不掉 RESTORE_DELETE_FAIL：$P 仍然是 $(getprop "$P")"
echo "这种情况写回 1 也能让名单重新生效，正在改用 1…"
VAL=1
fi
PL='{"propertySet":[{"name":"speaker_scene_restore","versionCode":"1","secret":false,"key":"'"$P"'","value":"'"$VAL"'"}]}'
settings put global debug_audio_cloud "$PL"
case "$(settings get global debug_audio_cloud)" in *speaker_scene_restore*) echo "调试项已写入：云控会把它当成一条属性下发";; *) echo "调试项回读不符 RESTORE_SET_FAIL"; exit 1;; esac
settings put global test_audio_cloud "${VAL}_$(date +%s)"
echo "已触发云控刷新，等属性变更（最多 12 秒）…"
i=0
while [ "$i" -lt 12 ]; do sleep 1; if [ "$(getprop "$P")" = "$VAL" ]; then break; fi; i=$((i+1)); done
settings delete global debug_audio_cloud >/dev/null 2>&1
settings delete global test_audio_cloud >/dev/null 2>&1
if [ "$(getprop "$P")" = "$VAL" ]; then echo "已恢复：$P = $VAL"; if [ -z "$CFG" ]; then echo "注意：这台机器没有名单文件，写回 1 只是让这条属性不生效的猜测值；要彻底回到出厂，得靠 root 把属性删掉。"; else echo "白名单处理重新生效。"; fi; exit 0; fi
if [ "$(id -u)" != "0" ] && command -v su >/dev/null 2>&1; then
echo "云控没接上，改用 root 直接写属性…"
su -c "setprop $P $VAL" </dev/null >/dev/null 2>&1
if [ "$(getprop "$P")" = "$VAL" ]; then echo "已恢复（root 直写）：$P = $VAL"; exit 0; fi
fi
echo "恢复没生效 RESTORE_FAIL：$P 仍然是 $(getprop "$P")，用 root 身份再跑一次"
exit 1
true
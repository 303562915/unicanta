S=/data/local/tmp/canta_audio_whitelist.state
if [ ! -f "$S" ]; then echo "没有记录：先点一次「解除」，原值才会被记下来"; exit 1; fi
P=$(sed -n '1p' "$S")
OLD=$(sed -n '2p' "$S")
if [ -z "$P" ]; then echo "记录里没有属性名 RESTORE_NO_KEY"; exit 1; fi
[ -n "$OLD" ] || OLD=1
CUR=$(getprop "$P")
if [ "$CUR" = "$OLD" ]; then echo "已经是原值 $OLD，白名单处理本来就生效"; exit 0; fi
echo "目标属性：$P（当前 ${CUR:-未设置}，原值 $OLD）"
PL='{"propertySet":[{"name":"speaker_scene_restore","versionCode":"1","secret":false,"key":"'"$P"'","value":"'"$OLD"'"}]}'
settings put global debug_audio_cloud "$PL"
case "$(settings get global debug_audio_cloud)" in *speaker_scene_restore*) echo "调试项已写入：云控会把它当成一条属性下发";; *) echo "调试项回读不符 RESTORE_SET_FAIL"; exit 1;; esac
settings put global test_audio_cloud "${OLD}_$(date +%s)"
echo "已触发云控刷新，等属性变更（最多 12 秒）…"
i=0
while [ "$i" -lt 12 ]; do sleep 1; if [ "$(getprop "$P")" = "$OLD" ]; then break; fi; i=$((i+1)); done
settings delete global debug_audio_cloud >/dev/null 2>&1
settings delete global test_audio_cloud >/dev/null 2>&1
if [ "$(getprop "$P")" = "$OLD" ]; then echo "已恢复：$P = $OLD，白名单处理重新生效"; exit 0; fi
if [ "$(id -u)" != "0" ] && command -v su >/dev/null 2>&1; then echo "云控没接上，改用 root 直接写属性…"; su -c "setprop $P $OLD" </dev/null >/dev/null 2>&1; if [ "$(getprop "$P")" = "$OLD" ]; then echo "已恢复（root 直写）：$P = $OLD"; exit 0; fi; fi
echo "恢复没生效 RESTORE_FAIL：$P 仍然是 $(getprop "$P")，用 root 身份再跑一次"
exit 1

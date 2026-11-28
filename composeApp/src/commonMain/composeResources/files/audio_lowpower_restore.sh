# 把低功耗外放清单还原成出厂那份（K90 Pro Max 这一类）。
LP=
for f in /odm/etc/audio/audio_lowpower_app_list.xml /vendor/etc/audio/audio_lowpower_app_list.xml; do
[ -f "$f" ] && { LP="$f"; break; }
done
if [ -z "$LP" ]; then echo "没找到低功耗清单，没有要还原的东西"; exit 0; fi
BAK=/data/local/tmp/canta_lowpower_app_list.bak
if [ ! -f "$BAK" ]; then
echo "没有备份：$BAK 不在（可能没点过「解除清单」，或 /data/local/tmp 被清过）。"
echo "没有备份就还原不回去——出厂那份只能从 ROM 包里重新解出来。"
exit 1
fi
NOW=$(grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$LP" 2>/dev/null | wc -l)
echo "清单文件：$LP（当前 ${NOW:-0} 个应用）"
if [ "$(id -u)" = "0" ]; then R=""
elif command -v su >/dev/null 2>&1 || [ -x /system/bin/su ] || [ -x /debug_ramdisk/su ] || [ -x /data/adb/magisk/su ]; then R="su -c"
elif pm list packages 2>/dev/null | grep -qiE 'magisk|kernelsu|apatch|supersu'; then R="su -c"
else
echo "这台机器没有 root：/odm 只读，还原不了。"
exit 1
fi
if [ -n "$R" ]; then
U=$(timeout 20 su -c 'id -u' </dev/null 2>/dev/null | tr -d '\r')
if [ "$U" != "0" ]; then
echo "su 拿不到 root（没授权 / 授权被拒）：还原不了 /odm。"
exit 1
fi
fi
if [ -n "$R" ]; then su -c "mount -o rw,remount /odm" </dev/null >/dev/null 2>&1; fi
if [ -n "$R" ]; then cp "$BAK" /data/local/tmp/lp_restore.tmp 2>/dev/null; su -c "cp /data/local/tmp/lp_restore.tmp '$LP'" </dev/null >/dev/null 2>&1
else cp /data/local/tmp/lp_restore.tmp "$LP" 2>/dev/null; fi
BACK=$(grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$LP" 2>/dev/null | wc -l)
if [ "${BACK:-0}" -gt 0 ]; then
echo "已还原：清单里又有 $BACK 个应用，出厂行为回来了"
if [ -n "$R" ]; then su -c "pkill -f audioserver" </dev/null >/dev/null 2>&1; else pkill -f audioserver >/dev/null 2>&1; fi
echo "已让 audioserver 重启。把播放器划掉再打开。"
exit 0
fi
echo "还原没成 ODM_RESTORE_BLOCKED：清单里还是 ${BACK:-0} 个应用，/odm 写不进去"
echo "备份文件还在：$BAK。用模块 overlay 时把它当成原始文件覆盖回去。"
exit 1
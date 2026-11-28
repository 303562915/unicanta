# 把低功耗外放清单里的应用全部放行（K90 Pro Max 这一类）。
#
# 做法：把 audio_lowpower_app_list.xml 里的 <app> 节点清空，留一份原始备份。
# 只改这一份清单，不动别的音频配置；失败时说清是「没有 root」「/odm 只读」还是「写不进去」。
LP=
for f in /odm/etc/audio/audio_lowpower_app_list.xml /vendor/etc/audio/audio_lowpower_app_list.xml; do
[ -f "$f" ] && { LP="$f"; break; }
done
if [ -z "$LP" ]; then
echo "没找到低功耗清单（audio_lowpower_app_list.xml），这台机器没有这套机制，不需要改"
exit 1
fi
echo "清单文件：$LP"
BAK=/data/local/tmp/canta_lowpower_app_list.bak
if [ -f "$BAK" ]; then
echo "原始备份已存在：$BAK（保留第一次的备份，不覆盖）"
else
if cp "$LP" "$BAK" 2>/dev/null; then echo "已备份原始清单到 $BAK"
else echo "备份失败 BACKUP_FAIL：$BAK 写不进去，为了还能恢复，这次不动清单"; exit 1; fi
fi
SIZE=$(wc -c < "$LP" 2>/dev/null)
echo "原始大小：${SIZE:-?} 字节"
# 有 root 才能写 /odm。**不能只看 command -v su**：Magisk/KernelSU 的 su 在文件系统上常常看不见
# （AGENTS 12.4.1 记过这个坑），只看它会把这台机器误报成「没有 root」。
R=
if [ "$(id -u)" = "0" ]; then R=""
elif command -v su >/dev/null 2>&1 || [ -x /system/bin/su ] || [ -x /debug_ramdisk/su ] || [ -x /data/adb/magisk/su ]; then R="su -c"
elif pm list packages 2>/dev/null | grep -qiE 'magisk|kernelsu|apatch|supersu'; then R="su -c"
else
echo "这台机器没有 root：/odm 是只读分区，没有 root 改不了这份清单。"
echo "免 root 做不到——如果你想改，只能 root 后用模块 overlay 或在 Termux 里跑这段脚本。"
exit 1
fi
# R 非空时先真跑一次，授权没过就别往下走（Magisk 会在这时弹授权框）。
if [ -n "$R" ]; then
U=$(timeout 20 su -c 'id -u' </dev/null 2>/dev/null | tr -d '\r')
if [ "$U" != "0" ]; then
echo "su 拿不到 root（没授权 / 授权被拒 / 管理器拦了）：这台机器改不了 /odm。"
echo "先在 Magisk / KernelSU 里给 Canta 授权 root，再点一次。"
exit 1
fi
echo "su 可用（uid=$U）"
fi
# 先试 remount：HyperOS 上常被 AVB/dm-verity 挡住，挡不住才有下一步。
if [ -n "$R" ]; then su -c "mount -o rw,remount /odm" </dev/null >/dev/null 2>&1; fi
# 只删 <app>…</app> 之间的包名内容，保留 XML 骨架：直接清空文件可能让音频服务起不来。
if [ -n "$R" ]; then
su -c "sed -i -E 's#(<app>)[^<]*(</app>)#\\1\\2#g' '$LP'" </dev/null >/dev/null 2>&1
else
sed -i -E 's#(<app>)[^<]*(</app>)#\1\2#g' "$LP" 2>/dev/null
fi
LEFT=$(grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$LP" 2>/dev/null | wc -l)
if [ "${LEFT:-1}" = "0" ]; then
echo "清单已清空（还剩 0 个应用）：这批应用下次播放不会再被压低功率"
# audioserver 会自己重启；重启后新音轨才读新清单。
if [ -n "$R" ]; then su -c "pkill -f audioserver" </dev/null >/dev/null 2>&1; else pkill -f audioserver >/dev/null 2>&1; fi
echo "已让 audioserver 重启，新音轨会读新清单。把播放器从最近任务里划掉再打开听一遍。"
echo "要还原：点「恢复清单」（原始备份在 $BAK）。系统升级会覆盖这次修改。"
exit 0
fi
echo "没改成 ODM_WRITE_BLOCKED：清单里还剩 $LEFT 个应用，/odm 写不进去。"
echo "HyperOS 的 /odm 受 AVB 保护，remount 一般会被拒。这种情况只能："
echo "  1) 用 Magisk / KernelSU 模块做 overlay（把改过的清单覆盖到 /odm/etc/audio/）；"
echo "  2) 或把 $BAK 拿给模块作者，让他照着出一份。"
echo "原始清单没被破坏，$LP 内容与备份一致。"
exit 1
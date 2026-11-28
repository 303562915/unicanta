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
# 低功耗外放清单：K90 Pro Max 这一代把 B 站、抖音的外放压小的是**这一份**，不是 trackefx 那套场景处理。
# 它跟上面那份是两个机制，所以单独读、单独输出；找不到也照实报空。
LP=
for f in /odm/etc/audio/audio_lowpower_app_list.xml /vendor/etc/audio/audio_lowpower_app_list.xml /odm/etc/audio_lowpower_app_list.xml; do
[ -f "$f" ] && { LP="$f"; break; }
done
if [ -z "$LP" ]; then
LP=$(find /odm/etc /vendor/etc /system_ext/etc /product/etc -maxdepth 4 -iname '*lowpower*app*list*' 2>/dev/null | head -n 1)
fi
echo "##LOWPOWER##$LP"
# 能不能提权。**不能只看 id -u，也不能只看 command -v su**：Canta 平时跑在 Shizuku 的 shell 里（uid 2000），
# 而 Magisk / KernelSU 的 su 在文件系统上经常看不见（见 AGENTS 12.4.1 记的那个坑）——
# 只看这两样会把「装了 Magisk 但用 Shizuku 跑」的机器误判成「没有 root」，然后把按钮白白置灰。
# 所以三段判断：本来就是 root → 找得到 su 或装了 root 管理器 → 真跑一次 su -c id -u（授权过才会返回 0）。
ROOT=no
if [ "$(id -u 2>/dev/null)" = "0" ]; then ROOT=yes
else
for s in su /system/bin/su /system/xbin/su /sbin/su /debug_ramdisk/su /data/adb/magisk/su; do
if command -v "$s" >/dev/null 2>&1 || [ -x "$s" ]; then ROOT=maybe; break; fi
done
if [ "$ROOT" = "no" ]; then
if pm list packages 2>/dev/null | grep -qiE 'magisk|kernelsu|apatch|supersu|superuser'; then ROOT=maybe; fi
fi
if [ "$ROOT" = "maybe" ]; then
U=$(timeout 8 su -c 'id -u' </dev/null 2>/dev/null | tr -d '\r')
[ "$U" = "0" ] && ROOT=yes
fi
fi
echo "##ROOT##$ROOT"
if [ -n "$LP" ]; then
# 清单头：里面可能有 enable 之类的开关字段，决定「是不是只能整份删」这件事。
echo "##LOWPOWERHEAD##$(head -c 600 "$LP" 2>/dev/null | tr -d '\000' | tr '\n' ' ')"
grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$LP" 2>/dev/null | tr -d '"' | sort -u | sed 's|^|##LOWPOWER##'"$LP"'	|'
grep -aoE '>[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+<' "$LP" 2>/dev/null | tr -d '><' | sort -u | sed 's|^|##LOWPOWER##'"$LP"'	|'
fi
if [ -n "$CFG" ]; then
{ grep -aoE '"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"' "$CFG"; grep -aoE '>[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+<' "$CFG"; } 2>/dev/null | tr -d '"><\000' | grep -v '\.xml$' | sort -u | sed 's|^|##PKG##|'
echo "##XMLHEAD##$(head -c 400 "$CFG" 2>/dev/null | tr -d '\000' | tr '\n' ' ')"
fi
for d in /odm/etc/audio /vendor/etc/audio /system/etc/audio /system_ext/etc/audio /product/etc/audio /my_product/etc/audio; do echo "##DIR##$d: $(ls -1 "$d" 2>/dev/null | tr '\n' ' ')"; done
find /odm/etc /vendor/etc /system/etc /product/etc -maxdepth 4 \( -iname '*track*' -o -iname '*efx*' -o -iname '*scene*' \) 2>/dev/null | head -n 24 | sed 's|^|##FILE##|'
pm list packages 2>/dev/null | sed 's|^package:|##INST##|'
# 属性面要够宽：K90pm 那台机器压外放的是低功耗清单，能不能免 root 关掉，取决于有没有
# 一条 lowpower / powersave / misound 的属性由音频云控接管——窄过滤（只看 scene/trackefx/whitelist）
# 把这类属性全漏掉了，问题就一直只能猜。
getprop | grep -iE 'scene|trackefx|track_efx|whitelist|lowpower|low_power|power\.save|powersave|misound|mis_|audio.*(white|bypass|scene|power)' | head -n 60 | sed 's|^|##PROP##|'
echo "##SETTING##debug_audio_cloud=$(settings get global debug_audio_cloud 2>/dev/null)"
echo "##SETTING##test_audio_cloud=$(settings get global test_audio_cloud 2>/dev/null)"
echo "##SETTING##debug_audio=$(settings get global debug_audio 2>/dev/null)"
echo "##END##"
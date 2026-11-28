package io.github.samolego.canta.data.audio

import io.github.samolego.canta.generated.resources.Res

/**
 * HyperOS / MIUI 的音频白名单：谁被系统按场景切低频、压音量，以及那一个全局开关现在是谁。
 *
 * The list itself lives on the phone (`/odm/etc/audio/trackefx_config.xml`) and cannot be edited
 * without root, so this page reads it rather than shipping a copy: a bundled list goes stale the
 * moment Xiaomi ships a new ROM, and a reader has no way to tell.
 *
 * 名单是「被压制」的应用，不是「受益」的应用。开关 `persist.sys.scene.partition.level` 为 1 时按名单
 * 处理，为 -1 时整张名单一起跳过——名单外的应用本来就不处理，所以把应用往名单里加，得到的是相反的结果。
 */
object AudioWhitelist {

    /** The read-only script every refresh runs through the privileged shell. */
    const val RESOURCE_PATH = "files/audio_whitelist_state.sh"

    /**
     * 「这台机器有没有这套配置」的一次性判定：不列包、不读属性，只找文件。
     *
     * 单独一条轻量脚本，是因为工具页要在**渲染入口那一行之前**知道答案——把整个
     * [RESOURCE_PATH] 跑一遍只为问一句有没有，代价是几百行 `pm list packages`。
     */
    const val PROBE_PATH = "files/audio_whitelist_probe.sh"

    /** What [PROBE_PATH] prints when it found something that looks like the config. */
    const val PROBE_YES = "__yes__"

    /** The script that writes the property to `-1`: the one action this feature ships. */
    const val BYPASS_PATH = "files/audio_whitelist_bypass.sh"

    /** The script that puts the recorded original value back. */
    const val RESTORE_PATH = "files/audio_whitelist_restore.sh"

    /**
     * 低功耗外放清单的开关（K90 Pro Max 这一类）。
     *
     * 和上面那条属性是两回事：这条清单在 `/odm` 只读分区里，写它必须 root，而且系统升级会覆盖，
     * 所以两个脚本都只做「改一份、留备份」，失败时明说原因，不假装成功。
     */
    const val LOWPOWER_DISABLE_PATH = "files/audio_lowpower_disable.sh"
    const val LOWPOWER_RESTORE_PATH = "files/audio_lowpower_restore.sh"

    /** The one property the whole feature hangs on. */
    const val PROP = "persist.sys.scene.partition.level"

    /**
     * 上报里这个工具的 id。
     *
     * 工具页那张同名卡已经删掉了（音频收敛成这一页），但这个串仍是**数据库里区分工具**的键，
     * 换掉会让 `tweak_reports` 的历史和新的行变成两个工具。所以它留在这里，不跟着卡一起走。
     */
    const val TWEAK_ID = "xiaomi-audio-whitelist"

    /** `-1`: the audio engine skips the whitelist entirely. */
    const val BYPASS = "-1"

    /** The value the ROM ships with: the list is applied. */
    const val DEFAULT_LEVEL = "1"

    internal const val TAG_LEVEL = "##LEVEL##"
    internal const val TAG_CONFIG = "##CFG##"
    internal const val TAG_PACKAGE = "##PKG##"
    internal const val TAG_INSTALLED = "##INST##"

    /** The lines a bug report needs: what the ROM actually has, next to what this build expects. */
    internal const val TAG_PROPERTY = "##PROP##"
    internal const val TAG_SETTING = "##SETTING##"
    internal const val TAG_XML = "##XMLHEAD##"

    /** Where the config would have been: the directories themselves, and anything named like it. */
    internal const val TAG_DIRECTORY = "##DIR##"
    internal const val TAG_FILE = "##FILE##"

    /**
     * 低功耗外放的清单（K90 Pro Max 这一类）：`/odm/etc/audio/audio_lowpower_app_list.xml` 里点名一批应用，
     * 它们播放时系统把声音压小、压低频。和 trackefx 那套是**两条不同的机制**，K90pm 上只有这一条。
     */
    internal const val TAG_LOWPOWER = "##LOWPOWER##"

    /** 清单文件的开头：里面可能有 enable 之类的开关字段。 */
    internal const val TAG_LOWPOWER_HEAD = "##LOWPOWERHEAD##"

    /** 能不能提权：脚本给的三态（`yes` / `maybe` / `no`），见 `audio_whitelist_state.sh` 里那段判断。 */
    internal const val TAG_ROOT = "##ROOT##"

    /** 除 `/odm` 以外可能可写的音频目录（厂商常留一个做覆盖层）。 */
    internal const val TAG_ALT_DIR = "##ALTDIR##"

    /** 音效配置的头：里面有没有按应用的 effect，决定「能不能免 root 关掉」。 */
    internal const val TAG_FX = "##FX##"

    /** Reads the script out of the bundled resources; the shell text never lands in Kotlin. */
    suspend fun script(): String = Res.readBytes(RESOURCE_PATH).decodeToString()

    /** Reads the one-line probe out of the bundled resources. */
    suspend fun probeScript(): String = Res.readBytes(PROBE_PATH).decodeToString()

    /** The two write scripts, read the same way: one source of shell text for the whole feature. */
    suspend fun bypassScript(): String = Res.readBytes(BYPASS_PATH).decodeToString()

    suspend fun restoreScript(): String = Res.readBytes(RESTORE_PATH).decodeToString()

    /** 低功耗清单的两个脚本：同样从资源里读，shell 文本不进 Kotlin。 */
    suspend fun lowPowerDisableScript(): String = Res.readBytes(LOWPOWER_DISABLE_PATH).decodeToString()

    suspend fun lowPowerRestoreScript(): String = Res.readBytes(LOWPOWER_RESTORE_PATH).decodeToString()

    /**
     * 这台机型（K90 Pro Max / myron 这一类）清单里的四个分组，以及每组的音量倍数。
     *
     * **这些是 ROM 里那份 `audio_lowpower_app_list.xml` 的事实数值**，不是我们编的：短视频类被压到
     * 44.7%，游戏 63.1%，导航 53.1%，长视频不压（100%）。有了它，即使没有权限读 `/odm`，
     * 页面也能直接告诉用户「你的抖音只有 44.7% 的音量」——这比让他猜「为什么声音小」有用得多。
     *
     * 值取自清单里的 `<template type="…" volume="…"/>`；系统升级后若改了数值，页面读到 /odm 时
     * 会以实际文件为准（见 [State.lowPowerPath]）。
     */
    val LOWPOWER_TEMPLATES: List<Pair<String, String>> = listOf(
        "lowPowerShortVideo" to "短视频（抖音 / B站 / 淘宝 / 拼多多 / 小红书…）0.447",
        "lowPowerGame" to "游戏（王者荣耀 / QQ飞车）0.631",
        "lowPowerSva" to "导航（高德 / 百度 / 腾讯 / Google 地图）0.531",
        "lowPowerLongVideo" to "长视频（YouTube / YouTube Kids）1.0（不压）",
    )

    /**
     * One app the phone's config names, with the words a user recognises it by.
     *
     * @param label the Chinese name when this build knows the package, the package name otherwise.
     * @param installed whether the phone has it now — the list is shipped per ROM, not per phone,
     *   so most rows on any one device are packages that are not there.
     */
    data class Entry(val packageName: String, val label: String, val installed: Boolean)

    /**
     * What one refresh saw.
     *
     * @param level the raw property value; empty when the property does not exist on this ROM.
     * @param configPath the config the list was read from, empty when the phone has none.
     * @param diagnostics the lines a bug report needs — the audio/scene properties this ROM has, the
     *   two global settings the bypass writes, and the head of the config. This feature was lifted
     *   from another tool's bytecode and has no public documentation, so a phone that says "no
     *   effect" can only be diagnosed from these lines.
     */
    data class State(
        val level: String,
        val configPath: String,
        val entries: List<Entry>,
        val diagnostics: List<String> = emptyList(),
        /**
         * 低功耗外放清单（`audio_lowpower_app_list.xml`）里点名的应用，以及它的路径。
         *
         * 只有部分机型（K90 Pro Max 这一代）有这份清单；为空表示这台机器没有这条机制。
         */
        val lowPowerPath: String = "",
        val lowPowerPackages: List<Entry> = emptyList(),
        /**
         * 能不能提权改 /odm：`yes`（已经是 root 或 su 可用）、`maybe`（装了 root 管理器但没授权过）、
         * `no`（确实没有 root）。
         *
         * 只用 `id -u == 0` 判是不够的：Canta 平时跑在 Shizuku 的 shell 里，本来就不是 root，
         * 而 Magisk 的 su 在文件系统上常常看不见——那样会把「有 root」的机器误判成「没有」。
         */
        val rootAccess: String = "no",
    ) {
        /** 有 root（或至少可能是 root，点了会弹授权）。`no` 时改 /odm 这条路走不通。 */
        val canElevate: Boolean get() = rootAccess == "yes" || rootAccess == "maybe"
        /** The audio engine is skipping the whitelist right now. */
        val bypassed: Boolean get() = level == BYPASS

        /** The list is being applied: the shipped state of the ROM. */
        val active: Boolean get() = level.isNotEmpty() && !bypassed

        /** 这台机器有低功耗外放清单：真正把 B 站/抖音外放压小的那条机制。 */
        val hasLowPower: Boolean get() = lowPowerPath.isNotEmpty() || lowPowerPackages.isNotEmpty()

        /** 清单里本机装着的应用数（多数行是别的机型才有的包）。 */
        val lowPowerInstalled: Int get() = lowPowerPackages.count { it.installed }

        /**
         * 这台机器上有没有这份名单文件。
         *
         * **它不再是「能不能解除」的门槛**：名单文件找不到时，属性照样写得进去（K90 Pro Max 这类
         * 澎湃OS 3 / Android 16 机器就是这样——写成功了，但引擎不读这条属性，也没有 odm 名单文件，
         * 所以点了不会有变化）。这个结论写进诊断行就够了，不该替用户把按钮置灰：那让「到底行不行」
         * 变成我们猜的，而不是他自己听一遍就知道的。
         */
        val supported: Boolean get() = configPath.isNotEmpty()
    }

    /** Turns the script's tagged lines into [State]; unknown lines are ignored, not fatal. */
    fun parse(output: String): State {
        var level = ""
        var config = ""
        var lowPowerPath = ""
        var lowPowerHead = ""
        var rootAccess = "no"
        var rootReported = false
        val packages = LinkedHashSet<String>()
        val lowPowerPackages = LinkedHashSet<String>()
        val installed = HashSet<String>()
        val diagnostics = ArrayList<String>()

        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith(TAG_LEVEL) -> level = line.removePrefix(TAG_LEVEL).trim()
                line.startsWith(TAG_CONFIG) -> config = line.removePrefix(TAG_CONFIG).trim()
                // 低功耗清单的行：`<路径>\t<包名>`，路径为空表示这台机器没有这份清单。
                line.startsWith(TAG_LOWPOWER) -> {
                    val rest = line.removePrefix(TAG_LOWPOWER).trim()
                    // 脚本用 tab 分隔「路径 \t 包名」；没有 tab 就是只有路径、清单里没有包。
                    val tab = rest.indexOf('\t')
                    if (tab >= 0) {
                        lowPowerPath = rest.substring(0, tab).trim()
                        rest.substring(tab + 1).trim().takeIf { it.isNotEmpty() }?.let { lowPowerPackages += it }
                    } else if (rest.contains('/')) {
                        lowPowerPath = rest
                    }
                }
                line.startsWith(TAG_LOWPOWER_HEAD) -> lowPowerHead = line.removePrefix(TAG_LOWPOWER_HEAD).trim()
                line.startsWith(TAG_ROOT) -> {
                    rootReported = true
                    rootAccess = line.removePrefix(TAG_ROOT).trim().lowercase().ifEmpty { "no" }
                }
                line.startsWith(TAG_PACKAGE) -> line.removePrefix(TAG_PACKAGE).trim().let {
                    if (it.isNotEmpty()) packages += it
                }
                line.startsWith(TAG_INSTALLED) -> line.removePrefix(TAG_INSTALLED).trim().let {
                    if (it.isNotEmpty()) installed += it
                }
                line.startsWith(TAG_PROPERTY) ->
                    diagnostics += line.removePrefix(TAG_PROPERTY).trim().replace(Regex("\\s+"), " ")
                line.startsWith(TAG_SETTING) -> diagnostics += "setting " + line.removePrefix(TAG_SETTING).trim()
                line.startsWith(TAG_XML) -> diagnostics += "xml: " + line.removePrefix(TAG_XML).trim()
                line.startsWith(TAG_DIRECTORY) -> diagnostics += "dir " + line.removePrefix(TAG_DIRECTORY).trim()
                line.startsWith(TAG_FILE) -> diagnostics += "file " + line.removePrefix(TAG_FILE).trim()
                line.startsWith(TAG_ALT_DIR) -> diagnostics += "altdir " + line.removePrefix(TAG_ALT_DIR).trim()
                line.startsWith(TAG_FX) -> diagnostics += "fx " + line.removePrefix(TAG_FX).trim().take(300)
                else -> Unit
            }
        }

        // Installed first, then by name: the rows a reader can actually act on are on top.
        val entries = packages
            .map { Entry(packageName = it, label = labelOf(it), installed = it in installed) }
            .sortedWith(compareBy({ !it.installed }, { it.label.lowercase() }, { it.packageName }))

        val lowPowerEntries = lowPowerPackages
            .map { Entry(packageName = it, label = labelOf(it), installed = it in installed) }
            .sortedWith(compareBy({ !it.installed }, { it.label.lowercase() }, { it.packageName }))

        // 清单也要进诊断行，否则页面看到了、数据库永远是空的——「本机有没有低功耗清单、拦了哪些应用」
        // 这类问题只能靠这些行回答，问一次用户拿一次结果，下次才不用再问。
        // 包名最多带 30 个：诊断行在服务端会被截到 8000 字符，前面还有属性与目录列表。
        if (lowPowerPath.isNotEmpty()) {
            diagnostics += "lowpower: $lowPowerPath（${lowPowerEntries.size} 个应用，本机装了 ${lowPowerEntries.count { it.installed }} 个）"
        }
        if (lowPowerEntries.isNotEmpty()) {
            diagnostics += "lowpower pkg: " + lowPowerEntries.take(30).joinToString(",") { it.packageName }
        }
        // 清单头决定「有没有开关字段可以走」，是下一步能不能免 root 的唯一依据，所以也进诊断。
        if (lowPowerHead.isNotEmpty()) {
            diagnostics += "lowpower head: " + lowPowerHead.take(400)
        }
        // 默认拒绝提权是安全策略，不等于脚本实际报告了「没有 root」。
        if (rootReported) {
            diagnostics += "root: " + when (rootAccess) {
                "yes" -> "有（su 可用）"
                "maybe" -> "可能有（装了 root 管理器，但没授权过 su）"
                else -> "没有（改 /odm 需要 root）"
            }
        }

        return State(
            level = level,
            configPath = config,
            entries = entries,
            diagnostics = diagnostics,
            lowPowerPath = lowPowerPath,
            lowPowerPackages = lowPowerEntries,
            rootAccess = rootAccess,
        )
    }

    /** The Chinese name for a package this build knows, the package name otherwise. */
    fun labelOf(packageName: String): String = NAMES[packageName] ?: packageName

    /**
     * Names for the packages Xiaomi's shipped config tends to list, taken from the app-name table of
     * a tool that ships the same list. A package missing here reads as its own name, which is ugly
     * but never wrong.
     */
    private val NAMES: Map<String, String> = mapOf(
        "air.tv.douyu.android" to "斗鱼",
        "cn.xiaochuankeji.tieba" to "最右",
        "cn.xuexi.android" to "学习强国",
        "com.UCMobile" to "UC浏览器",
        "com.achievo.vipshop" to "唯品会",
        "com.alibaba.wireless" to "1688",
        "com.android.browser" to "系统浏览器",
        "com.autonavi.minimap" to "高德地图",
        "com.baidu.BaiduMap" to "百度地图",
        "com.coolapk.market" to "酷安",
        "com.cubic.autohome" to "汽车之家",
        "com.google.android.apps.maps" to "Google 地图",
        "com.google.android.apps.youtube.kids" to "YouTube Kids",
        "com.gotokeep.keep" to "Keep",
        "com.hupu.games" to "虎扑",
        "com.jingdong.app.mall" to "京东",
        "com.kuaishou.nebula" to "快手极速版",
        "com.sankuai.meituan" to "美团",
        "com.sankuai.meituan.takeoutnew" to "美团外卖",
        "com.sina.weibo" to "微博",
        "com.smile.gifmaker" to "快手",
        "com.snapchat.android" to "Snapchat",
        "com.ss.android.article.lite" to "今日头条极速版",
        "com.ss.android.article.news" to "今日头条",
        "com.ss.android.auto" to "懂车帝",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.ss.android.ugc.aweme.lite" to "抖音极速版",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.sup.android.superb" to "皮皮虾",
        "com.taobao.idlefish" to "闲鱼",
        "com.taobao.live" to "淘宝直播",
        "com.taobao.taobao" to "淘宝",
        "com.tencent.map" to "腾讯地图",
        "com.tencent.mtt" to "QQ浏览器",
        "com.tencent.news" to "腾讯新闻",
        "com.tencent.qt.qtl" to "企鹅体育",
        "com.tencent.tmgp.sgame" to "王者荣耀",
        "com.tencent.tmgp.speedmobile" to "QQ飞车手游",
        "com.tmall.wireless" to "天猫",
        "com.twitter.android" to "X",
        "com.vanced.android.youtube" to "YouTube Vanced",
        "com.videogo" to "萤石云视频",
        "com.weico.international" to "微博国际版",
        "com.xiaomi.shop" to "小米商城",
        "com.xingin.xhs" to "小红书",
        "com.xs.fm" to "番茄畅听",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.zhihu.android" to "知乎",
        "com.zhiliaoapp.musically.go" to "TikTok Lite",
        "in.mohalla.video" to "Moj",
        "tv.danmaku.bili" to "哔哩哔哩",
    )
}

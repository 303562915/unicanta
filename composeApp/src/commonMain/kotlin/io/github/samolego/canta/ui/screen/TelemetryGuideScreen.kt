package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.telemetry_guide_title
import io.github.samolego.canta.ui.component.ScreenTopBar
import org.jetbrains.compose.resources.stringResource

/**
 * 遥测组件指南：各厂商预置的运营商 / 行为采集组件、它们声明的权限，以及三条权限通道下的卸载结论。
 *
 * 为什么值得单开一页：这类包在「应用」列表里只有包名和一个英文说明，用户在群里问的都是同一组问题
 * ——「这个能不能删」「为什么我删了提示失败」「换 Dhizuku 到底有没有用」。答案不是简单的能/不能：
 * 同一个包在 Shizuku（UID 2000）、Dhizuku（Device Owner）、Root（UID 0）下结论完全不同，
 * 所以这里按厂商分段，每一行同时给出三条通道的结论。
 *
 * 数据来源：`Eynnzerr/TelemetryCleaner` 的内置清单与它的 `docs/reports` 实测报告
 * （ColorOS 16 / PGEM10 的 `com.oplus.olc`、OriginOS 6 / V2454DA 的 `com.vivo.devicereg`），
 * 加上本应用总库（UAD 名单 + `bloat_extra.json`）里这些包的说明。
 */
@Composable
internal fun TelemetryGuideScreen(onNavigateBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = stringResource(Res.string.telemetry_guide_title),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            item(key = "routes") { RoutesCard() }
            items(TelemetryVendors, key = { it.title }) { vendor -> VendorCard(vendor) }
            item(key = "dns") { NetworkFallbackCard() }
            item(key = "source") {
                Text(
                    text = "来源：Eynnzerr/TelemetryCleaner 的内置清单与 docs/reports 实测记录；" +
                        "每个包的说明同步写在本应用的总库（UAD 名单 + bloat_extra.json）。" +
                        "厂商改版后拦截规则可能变，结论以你那台机器的实际返回为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 数据
// ---------------------------------------------------------------------------------------------

/**
 * 一条通道上的结论，只有三态。
 *
 * 不写「需 Dhizuku」这种跨通道的说法：徽章前面已经写了通道名（`Shizuku` / `Dhizuku` / `Root`），
 * 再写「需 Dhizuku」会读成「Dhizuku 需要 Dhizuku」。被拦时该换哪条路，由那一行的说明文字交代。
 */
private enum class Route(val label: String) {
    OK("可卸"),
    BLOCKED("被拦"),
    UNTESTED("未验证"),
    AVOID("别卸"),
}

/**
 * 一个包。
 *
 * [perms] 写它真正要/被默认授予的那几项，不是全部 manifest 权限：用户关心的是「它能看见什么」。
 */
private data class Component(
    val pkg: String,
    val name: String,
    val what: String,
    val perms: String,
    val shizuku: Route,
    val dhizuku: Route,
    val root: Route = Route.OK,
    val note: String = "",
)

private data class Vendor(
    val title: String,
    val subtitle: String,
    val components: List<Component>,
)

private val TelemetryVendors = listOf(
    Vendor(
        title = "跨厂商",
        subtitle = "不绑定某个品牌，有对应运营商固件就会出现",
        components = listOf(
            Component(
                pkg = "com.mobiletools.systemhelper",
                name = "联通 SystemHelper",
                what = "终端入网自注册 + 后台采集：把设备与已安装应用清单整理后上报",
                perms = "电话状态、已安装应用列表、网络与基站信息",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "TelemetryCleaner 实测可卸，卸完不影响日常使用。",
            ),
            Component(
                pkg = "cn.richinfo.dm",
                name = "移动 DM 客户端（Richinfo）",
                what = "运营商终端管理：远程下发配置，并可在运行时加载热补丁（DexClassLoader）",
                perms = "设备信息、已安装应用、网络状态",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "与华为 HwCmccDm 同源；社区实测样本少，第一次卸载建议先记下 `pm install-existing` 的恢复命令。",
            ),
            Component(
                pkg = "com.qti.qcc",
                name = "高通 QCC 芯片级遥测",
                what = "Qualcomm Connected Compute / QDMA：本地库记录设备与固件特征，向 qcc.qualcomm.cn 上报",
                perms = "设备与固件特征、网络信息、本地统计数据",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "高通方案机器上普遍存在，属于厂商遥测而不是运营商组件。",
            ),
            Component(
                pkg = "com.qualcomm.qti.devicestatisticsservice",
                name = "高通设备统计服务",
                what = "设备使用统计上报（高通公共组件）",
                perms = "READ_PRIVILEGED_PHONE_STATE、设备统计",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
        ),
    ),
    Vendor(
        title = "小米 / Redmi / POCO",
        subtitle = "国行 HyperOS / MIUI 的注册与统计组件",
        components = listOf(
            Component(
                pkg = "com.xiaomi.registration",
                name = "电信自注册",
                what = "中国电信入网自注册，后台把设备与 SIM 信息报给运营商",
                perms = "电话状态、已安装应用、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.miui.dmregservice",
                name = "移动 DM 自注册",
                what = "中国移动终端管理（DM）自注册，接收远程配置并上报",
                perms = "电话状态、已安装应用、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.miui.analytics",
                name = "MIUI 统计分析",
                what = "系统与应用的匿名使用统计、崩溃上报",
                perms = "使用统计、设备标识、网络状态",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "卸掉后系统统计页会少数据，功能不受影响。",
            ),
            Component(
                pkg = "com.miui.misightservice",
                name = "米思智统计服务",
                what = "设备侧数据采集与上报（与 analytics 属同一套）",
                perms = "使用统计、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.xiaomi.security.onetrack",
                name = "OneTrack 行为追踪",
                what = "系统级埋点：记录功能使用与页面路径",
                perms = "使用统计、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "老机型上个别系统页可能少一条数据上报，不影响功能。",
            ),
        ),
    ),
    Vendor(
        title = "OPPO / 一加 / realme",
        subtitle = "ColorOS 系；这里有一个包被 ROM 单独保护",
        components = listOf(
            Component(
                pkg = "com.oppo.ctautoregist",
                name = "电信自注册",
                what = "中国电信入网自注册",
                perms = "电话状态、已安装应用、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.coloros.regservice",
                name = "移动 RegService",
                what = "中国移动终端注册与数据上报",
                perms = "电话状态、已安装应用、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.oplus.olc",
                name = "日志服务（OLC）",
                what = "系统日志与诊断服务；实测可读到按应用统计的网络流量",
                perms = "定位、联系人、通话记录、短信、相机、录音、已安装应用、按应用流量统计",
                shizuku = Route.BLOCKED,
                dhizuku = Route.OK,
                note = "APK 里带 `ForbidUninstall=true`，ColorOS 只拦 UID 2000（shell）：`pm`、Shizuku、app_process 全部回 DELETE_FAILED_INTERNAL_ERROR。Dhizuku 走 PackageInstaller 时调用方是 UID 1000，实测可卸可恢复。停用/隐藏连 Dhizuku 也会被 `OplusForbidHideOrDisableManager` 拒绝；系统分身（user 10）只有 Root 能处理。",
            ),
        ),
    ),
    Vendor(
        title = "vivo / iQOO",
        subtitle = "OriginOS 系；运营商组件受 PMS 保护名单约束",
        components = listOf(
            Component(
                pkg = "com.vivo.devicereg",
                name = "移动 + 电信 + 卫星自注册",
                what = "同时承载移动 DM 与电信自注册，含天通卫星短信自注册；可被云端拉配置并自升级",
                perms = "精确/后台定位、短信、存储、电话状态、INSTALL_SELF_UPDATES",
                shizuku = Route.BLOCKED,
                dhizuku = Route.UNTESTED,
                note = "纯 Shizuku 会被 vivo PMS 保护名单拦（DELETE_FAILED_USER_RESTRICTED，停用报 `Cannot disable ... no root permission`）；Dhizuku 路径社区尚无成功实测，失败就直接上 Root —— Root 下 `pm uninstall --user 0` 实测可卸、`install-existing` 可恢复，通话与录音功能正常。",
            ),
            Component(
                pkg = "com.bbk.iqoo.logsystem",
                name = "BBKLOG 日志与应用时长上报",
                what = "本地生成应用使用分析库，向 f-up.vivo.com.cn 上报应用时长",
                perms = "PACKAGE_USAGE_STATS、READ_PRIVILEGED_PHONE_STATE",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "`persistent=true`，禁用后要重启一次才彻底退出内存。",
            ),
            Component(
                pkg = "com.vivo.networkimprove",
                name = "网络优化采样",
                what = "高频记录基站/信号参数并关联当时的前台应用，上报 moni-*stsdk.vivo.com.cn",
                perms = "网络与基站信息、前台应用名、定位",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "本地库实测可涨到几十 MB。",
            ),
            Component(
                pkg = "com.vivo.nps",
                name = "体验调查遥测",
                what = "后台轮询问卷调查配置",
                perms = "网络状态、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.vivo.epm",
                name = "异常策略与性能监控",
                what = "Exception Policy Manager：异常与性能数据上报",
                perms = "使用统计、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.vivo.magazine",
                name = "锁屏画报",
                what = "锁屏壁纸推送及曝光追踪",
                perms = "网络状态、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "卸掉后锁屏画报不再更新，锁屏本身不受影响。",
            ),
            Component(
                pkg = "com.vivo.aiengine",
                name = "VCode 智能引擎（共享 UID 1000）",
                what = "本机采集量最大的模块：本地落盘用户画像，`aware_collect.db` 实测 100 MB 级",
                perms = "后台定位、短信、通话记录、通讯录、应用统计",
                shizuku = Route.AVOID,
                dhizuku = Route.AVOID,
                root = Route.AVOID,
                note = "与系统共享 `android.uid.system`，disable 会让系统设置/推送/智能功能异常。这一类只建议做网络层拦截。",
            ),
            Component(
                pkg = "com.vivo.abe",
                name = "App Behavior Engine（共享 UID 1000）",
                what = "常驻的配置分发与行为记录，本地记录全量应用安装/更新/卸载流水",
                perms = "已安装应用流水、unifiedconfig 配置权限",
                shizuku = Route.AVOID,
                dhizuku = Route.AVOID,
                root = Route.AVOID,
                note = "同上，共享 UID 1000；拦截上报域名比卸包安全。",
            ),
            Component(
                pkg = "com.vivo.vms",
                name = "标识符分发中心（共享 UID 1000）",
                what = "OAID/VAID/AAID 标识符分发与 VCode 汇总上报",
                perms = "设备标识符、网络状态",
                shizuku = Route.AVOID,
                dhizuku = Route.AVOID,
                root = Route.AVOID,
                note = "同上；内置了硬编码直连 IP，靠 DNS 拦不全，但卸载代价更大。",
            ),
        ),
    ),
    Vendor(
        title = "华为 / 荣耀",
        subtitle = "两家的包名一一对应，处理方式相同",
        components = listOf(
            Component(
                pkg = "com.huawei.android.UEInfoCheck",
                name = "华为 电信 UE 信息检查",
                what = "为入网自注册采集设备与终端身份信息",
                perms = "电话状态、设备标识、已安装应用",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.huawei.android.AutoRegSms",
                name = "华为 电信自注册短信",
                what = "后台收发并解析开通短信",
                perms = "短信、电话状态",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.huawei.regservice",
                name = "华为 移动 RegService",
                what = "中国移动终端注册与数据上报",
                perms = "电话状态、已安装应用",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.hihonor.android.UEInfoCheck",
                name = "荣耀 电信 UE 信息检查",
                what = "同华为同名组件",
                perms = "电话状态、设备标识、已安装应用",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.hihonor.android.AutoRegSms",
                name = "荣耀 电信自注册短信",
                what = "同华为同名组件",
                perms = "短信、电话状态",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "com.hihonor.regservice",
                name = "荣耀 移动 RegService",
                what = "中国移动终端注册与数据上报",
                perms = "电话状态、已安装应用",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
        ),
    ),
    Vendor(
        title = "魅族 / 努比亚",
        subtitle = "样本少，结论按保守一档处理",
        components = listOf(
            Component(
                pkg = "com.meizu.regservice",
                name = "魅族 移动 RegService",
                what = "中国移动终端注册与数据上报",
                perms = "电话状态、已安装应用",
                shizuku = Route.OK,
                dhizuku = Route.OK,
            ),
            Component(
                pkg = "cn.nubia.register",
                name = "努比亚 / 红魔 电信注册",
                what = "中国电信入网注册组件",
                perms = "电话状态、设备标识",
                shizuku = Route.OK,
                dhizuku = Route.OK,
                note = "TelemetryCleaner 记为可卸但真机样本少，先记下恢复命令再动手。",
            ),
        ),
    ),
)

// ---------------------------------------------------------------------------------------------
// 界面
// ---------------------------------------------------------------------------------------------

@Composable
private fun RoutesCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Text(
            text = "三条通道分别在什么身份下执行",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        RouteLine(
            badge = "Shizuku",
            body = "以 UID 2000（shell）执行。AOSP 那一层能过，但厂商的「只拦 shell」名单会命中——" +
                "ColorOS 按 APK 元数据 `ForbidUninstall`、vivo 按 PMS 保护名单，都会直接拒绝。",
        )
        RouteLine(
            badge = "Dhizuku",
            body = "以 Device Owner 身份走 PackageInstaller：进 PMS 前会 `Binder.clearCallingIdentity()`，" +
                "调用方变成 UID 1000，并且保留 `DELETE_SYSTEM_APP`(0x4)。只拦 UID 2000 的检查因此失效。" +
                "作用域只有主空间（user 0）。",
        )
        RouteLine(
            badge = "Root",
            body = "UID 0，两层校验都能过；系统分身（user 10）也只有这条能处理。" +
                "卸载建议一律带 `--user <id>`，系统 APK 保留，才能 `pm install-existing` 恢复。",
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "停用/冻结是另一回事：受保护的包在 ColorOS 上连 Dhizuku 的 hide 都会被拒，" +
                "所以「卸不掉就冻结」在这些包上给不出结果，别指望它。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteLine(badge: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VendorCard(vendor: Vendor) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            text = vendor.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = vendor.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        vendor.components.forEachIndexed { index, component ->
            if (index > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(Modifier.height(8.dp))
            }
            ComponentBlock(component)
        }
    }
}

@Composable
private fun ComponentBlock(component: Component) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = component.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        // 三条通道的结论单独一行：跟名字挤在一行时，长名字（「移动 + 电信 + 卫星自注册」）
        // 会把徽章压出去。
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 3.dp),
        ) {
            val allAvoid = component.shizuku == Route.AVOID &&
                component.dhizuku == Route.AVOID &&
                component.root == Route.AVOID
            if (allAvoid) {
                // 三条路都一样的时候写三遍「别卸」只是噪音，一个徽章就够，理由在下面的说明里。
                RouteBadge("", Route.AVOID)
            } else {
                RouteBadge("Shizuku", component.shizuku)
                RouteBadge("Dhizuku", component.dhizuku)
                // Root 只在另外两条通道不是「可卸」时才列出来：全都可卸的包写三个徽章是噪音。
                if (component.shizuku != Route.OK || component.dhizuku != Route.OK) {
                    RouteBadge("Root", component.root)
                }
            }
        }
        Text(
            text = component.pkg,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = scheme.primary,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            text = component.what,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "权限：" + component.perms,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (component.note.isNotBlank()) {
            Text(
                text = component.note,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 通道徽章：`Shizuku 可卸` / `Dhizuku 被拦` / `别卸`。
 *
 * 一条通道只出三态（外加「未验证」），不同通道之间怎么换路由不写在徽章上——那属于说明文字。
 */
@Composable
private fun RouteBadge(route: String, capability: Route) {
    val color = routeColor(capability)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (capability == Route.AVOID) capability.label else "$route ${capability.label}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun routeColor(route: Route): Color {
    val scheme = MaterialTheme.colorScheme
    return when (route) {
        Route.OK -> scheme.primary
        Route.BLOCKED -> scheme.tertiary
        Route.UNTESTED -> scheme.secondary
        Route.AVOID -> scheme.error
    }
}

@Composable
private fun NetworkFallbackCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Text(
            text = "卸不了的怎么办：改拦截上报，而不是动系统包",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "与系统共享 UID 1000 的组件（vivo 的 aiengine / abe / vms 这一类）卸掉或停用都会伤到系统功能，" +
                "官方报告的建议是只做网络层拦截。vivo 的埋点域名大量是「业务前缀 + ort-stsdk」这种连字符写法，" +
                "普通广告规则里的 `*.stsdk.vivo.com.cn` 匹配不到，需要正则：",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "(^|\\.|-)stsdk\\.vivo\\.com\\.cn$\nvcode-od.vivo.com.cn\nvcode-api.vivo.com.cn\ndsplog.vivo.com.cn\ntdc.vivo.com.cn",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = scheme.primary,
        )
    }
}

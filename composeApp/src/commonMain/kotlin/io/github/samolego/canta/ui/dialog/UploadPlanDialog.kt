package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.plan_upload_candidate_count
import io.github.samolego.canta.generated.resources.plan_upload_clear
import io.github.samolego.canta.generated.resources.plan_upload_device_label
import io.github.samolego.canta.generated.resources.plan_upload_flow_hint
import io.github.samolego.canta.generated.resources.plan_upload_name_label
import io.github.samolego.canta.generated.resources.plan_upload_net_blocked
import io.github.samolego.canta.generated.resources.plan_upload_net_blocked_body
import io.github.samolego.canta.generated.resources.plan_upload_net_checking
import io.github.samolego.canta.generated.resources.plan_upload_net_ok
import io.github.samolego.canta.generated.resources.plan_upload_net_retry
import io.github.samolego.canta.generated.resources.plan_upload_no_candidates
import io.github.samolego.canta.generated.resources.plan_upload_note_label
import io.github.samolego.canta.generated.resources.plan_upload_publishing
import io.github.samolego.canta.generated.resources.plan_upload_row_awaiting
import io.github.samolego.canta.generated.resources.plan_upload_row_verified
import io.github.samolego.canta.generated.resources.plan_upload_select_all
import io.github.samolego.canta.generated.resources.plan_upload_selected
import io.github.samolego.canta.generated.resources.plan_upload_sheet_title
import io.github.samolego.canta.generated.resources.plan_upload_state_blocked
import io.github.samolego.canta.generated.resources.plan_upload_state_blocked_body
import io.github.samolego.canta.generated.resources.plan_upload_state_partial
import io.github.samolego.canta.generated.resources.plan_upload_state_partial_body
import io.github.samolego.canta.generated.resources.plan_upload_state_ready
import io.github.samolego.canta.generated.resources.plan_upload_state_ready_body
import io.github.samolego.canta.generated.resources.plan_upload_still_installed
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.fl.isDark
import io.github.samolego.canta.ui.fl.successColor
import io.github.samolego.canta.ui.fl.warningColor
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import org.jetbrains.compose.resources.stringResource

/** Icon size of a package row inside the sheet: small, so a batch of twenty still fits. */
private val CANDIDATE_ICON = 28.dp

/** How much of the window the sheet asks for: it is meant to be a page, not a popup. */
private const val SHEET_HEIGHT_FRACTION: Float = 0.92f

/** 主题胶囊的定宽：横滑时每格一样宽，滑起来不会忽宽忽窄；标签短（「精简AI」）也不缩。 */
private val PICK_WIDTH = 96.dp

/**
 * The shorthand names these sets already go by, offered as one-tap titles.
 *
 * They are words people use for the same idea — "极简" is not a different plan from "干净" — so
 * this is a naming shortcut, not a set of built-in plans: the packages are still whatever the
 * uploader picked, and the title is the only thing a tap writes.
 *
 * 分类胶囊那排也补上同样的名字：上传的人点完「遥测诊断」勾了一批包，方案名十有八九就叫这个，
 * 于是再点一次分类就等于取这个名字。命名用的那几个词在「取名字」那一行里也照旧保留。
 */
private val PLAN_NAME_PRESETS =
    listOf("极简", "省电", "游戏", "长辈机", "干净", "日常", "遥测诊断", "云同步备份", "输入法语音")

/**
 * 一键勾一批的主题清单。社区里实际在用的那几个说法在最前面（最精简、广告追踪、精简游戏、精简AI），
 * 后面补上「遥测诊断」「云同步备份」「输入法语音」——这三类是「精简」里最常见、也最容易漏的。
 *
 * 它只是「勾选」的快捷方式，不是内置方案：命中的包照样要过重启验证才进得了清单，勾上来之后
 * 也照样能在下面逐个取消。所以这里命不中就是灰的、点不动，而不是悄悄勾一堆让人以为选好了。
 *
 * 清单是**横向滚动**的一排（不是换行铺满）：格子多了以后换行会把候选列表往下挤，横滑则保持
 * 一行高度，滑到右边就能看见剩下的格子。
 */
private class PlanPick(
    val label: String,
    /** 它挑的是什么，一行说完；灰掉的时候这一行就是解释。 */
    val hint: String,
    val match: (AppInfo) -> Boolean,
)

/**
 * 广告、统计、追踪一类的包名分段。
 *
 * 短词（`ad`、`ads`、`msa`）只认整段，长词（四个字母以上）认前缀：否则 `com.android.*` 会因为
 * 以「ad」开头被当成广告，那是把整个系统都勾进来。
 *
 * 厂商前缀（`miui`、`oplus`、`vivo`）不单独成为一整段的中文名——那样会连系统框架一起勾上——
 * 所以这里只收广告 SDK 自己的词，厂商的统计组件靠 `com.xiaomi.joyose` 这类整名短语兜。
 */
private val AD_SEGMENTS = listOf(
    "ads", "ad", "mads", "msa", "adsdk", "advert", "adcolony", "admob", "adservice",
    "applovin", "analytics", "tracker", "tracking", "umeng", "appmetrica", "doubleclick",
    "comscore", "appsflyer", "adjust", "joyose", "marketresearch", "apprecommend",
    "datacollect", "beacon", "statistics", "pushservice",
)

/** 游戏中心、游戏服务、游戏助手一类；`game` 是长词，所以 `gamecenter`、`gameturbo` 都算。 */
private val GAME_SEGMENTS = listOf("game", "games", "playgames", "gameturbo", "gamecenter", "gamebooster")

/** 语音助手与 AI 服务；`ai` 只认整段（否则 `mail`、`detail` 都会中）。 */
private val AI_SEGMENTS = listOf(
    "ai", "aicr", "aivoice", "aiservice", "aiengine", "assistant", "voiceassist", "xiaoice",
    "copilot", "gemini", "colortv", "aiimage", "aigallery",
)

/**
 * 遥测、诊断、日志上报一类：系统「用户体验改进计划」「匿名统计」这一类组件的名字。
 *
 * 和广告追踪分开放是有意为之：广告那格勾的是商业追踪 SDK，这格勾的是厂商自己把日志、崩溃、
 * 使用数据传回服务器的服务。两类都有人要卸，但不是同一件事，混成一格会让计数说不清。
 */
private val TELEMETRY_SEGMENTS = listOf(
    "telemetry", "metrics", "metric", "diag", "diagnostic", "feedback", "bugreport",
    "logupload", "logtool", "crashreport", "analyticsengine", "dmp",
)

/** 云同步、备份、跨设备一类的服务；卸掉之后备份和云相册就走系统自带的那条。 */
private val CLOUD_SEGMENTS = listOf(
    "cloud", "cloudservice", "miuicloud", "cloudsync", "cloudbackup", "xcloud",
    "finddevice", "devicelocator",
)

/** 输入法、语音识别、翻译这类「本地能力」插件；很多人只留一个输入法。 */
private val UTILITY_SEGMENTS = listOf(
    "inputmethod", "voicerecog", "translat", "screenreader",
)

/** 应用名里的这些词比包名更直白，中文应用尤其如此。英文的「Ad」不进这张表：它会命中 Shadow。 */
private val AD_NAMES = listOf("广告", "统计", "追踪")
private val GAME_NAMES = listOf("游戏", "Game")
private val AI_NAMES = listOf("小爱", "语音助手", "AI助手", "智能助理", "智能助手")
private val TELEMETRY_NAMES = listOf("遥测", "诊断", "日志", "反馈", "用户体验计划", "数据统计")
private val CLOUD_NAMES = listOf("云服务", "云同步", "云备份", "云相册", "查找设备")
private val UTILITY_NAMES = listOf("输入法", "语音", "翻译", "无障碍")

/** 「云同步备份」这一格的例外名单：这些名字里带 cloud/sync，但卸了就是没设置、没账号、没输入法。 */
private val CLOUD_KEEP_PACKAGES = listOf(
    "com.miui.cloudservice.sysbase",
)

/** 包里明确不能碰的：卸了会起不来，或者根本不是「精简」该动的东西。 */
private val CLOUD_KEEP_SEGMENTS = listOf("settings", "framework", "telecom", "systemui")

/** 「云同步备份」那一格的排除项：名字里带 cloud，卸了却是设置/账号/输入法的底座。 */
private fun AppInfo.isCloudKeystone(): Boolean =
    hasSegment(CLOUD_KEEP_SEGMENTS) || hasPhrase(CLOUD_KEEP_PACKAGES)

/**
 * 分段法命不中的整名组件（小米/红米这批里最常见的那几个）。「广告追踪」那格是
 * `com.miui.msa`（广告服务）、`com.miui.hybrid`（快应用广告位）、`AppRecommend`；
 * 「遥测诊断」那格是 `dmregservice`（推送注册 + 数据上报）和 `com.miui.analytics`。
 *
 * 这些只作为**额外**命中：分段法照旧生效，别的厂商的同类组件也能被这两格挑到。
 */
private val XIAOMI_AD_PACKAGES = listOf(
    "com.miui.apprecommend",
    "com.miui.systemad",
    "com.miui.hybrid",
)

private val XIAOMI_TELEMETRY_PACKAGES = listOf(
    "com.xiaomi.joyose",
    "com.miui.dmregservice",
    "com.miui.analytics",
    "com.miui.misightservice",
    "com.miui.powerkeeper",
    "com.miui.bugreport",
)

/** 包名按 `.`、`-`、`_` 分段，任一段命中就算：`com.miui.msa` 与 `com.google.android.gms.ads` 都算广告。 */
private fun AppInfo.hasSegment(tokens: List<String>): Boolean =
    packageName.lowercase()
        .split('.', '-', '_')
        .any { segment -> tokens.any { token -> segment == token || (token.length >= 4 && segment.startsWith(token)) } }

/** 整名短语：`com.xiaomi.joyose` 这种名字不会被分段法命中，单独按完整包名比对。 */
private fun AppInfo.hasPhrase(phrases: List<String>): Boolean =
    phrases.any { packageName.equals(it, ignoreCase = true) || packageName.startsWith("$it.", ignoreCase = true) }

/** 应用名命中，和分段命中是「或」的关系。 */
private fun AppInfo.namedLike(words: List<String>): Boolean = words.any { name.contains(it, ignoreCase = true) }

/**
 * Whether the batch on screen may be published, as the three colours the sheet draws.
 *
 * The order of the work is the order of this enum: packages are removed, the phone is restarted,
 * the restart check marks what survived — and only then is there a [READY] batch. [PARTIAL] keeps
 * the proven packages uploadable while saying how many are still waiting; [BLOCKED] is a phone
 * that has nothing proven yet, so the sheet explains the steps instead of offering a form.
 */
private enum class GateState { READY, PARTIAL, BLOCKED }

/**
 * Publishes a set of packages as a community plan, as a sheet that comes up from the bottom.
 *
 * The reboot check is the gate, not an afterthought: the sheet opens on the state of the batch
 * (green when it survived a restart, amber while part of it is still waiting, red when nothing has
 * been proven), and only packages that passed the check can be ticked. There is no way around it —
 * a plan nobody restarted the phone for is not a plan anyone should install.
 *
 * @param candidates every package this phone may publish, in the order they should be listed.
 * @param preselected the packages the sheet starts on: the reboot-proven ones.
 * @param verified the packages the reboot check passed; nothing outside this set is tickable.
 * @param reachable whether the community backend answered the preflight: null while it is being
 *   asked, false when nothing came back. An upload cannot leave a phone with no route to it, so the
 *   sheet says so before a name is typed instead of failing silently afterwards.
 * @param checking true while the preflight is running.
 * @param publishing true while the caller is still sending: the sheet stays up, buttons go dead.
 * @param rules 这台手机生效的安全规则：只有「最精简」那一格要它（挑规则判定为安全的包）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPlanDialog(
    candidates: List<AppInfo>,
    preselected: Set<String>,
    profile: DeviceProfile?,
    verified: Set<String>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String, packages: List<String>) -> Unit,
    publishing: Boolean = false,
    reachable: Boolean? = null,
    checking: Boolean = false,
    onRecheck: () -> Unit = {},
    rules: SafetyRules? = null,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    // Order is kept as given: the plan's package list is what the uploader ticked, in list order.
    var selected by remember { mutableStateOf(preselected.toList()) }

    val close: () -> Unit = { if (!publishing) onDismiss() }

    val uploadable = candidates.filter { it.packageName in verified }
    val gate = when {
        uploadable.isEmpty() -> GateState.BLOCKED
        uploadable.size < candidates.size -> GateState.PARTIAL
        else -> GateState.READY
    }
    // Only proven packages count, whatever was passed in: the tick list is filtered by the check.
    val provenSelection = selected.filter { it in verified }
    // Nothing goes up on a phone that cannot reach the backend: the button stays dead until the
    // preflight answered yes, and the card above says what to do when it answered no.
    val mayPublish = title.isNotBlank() && provenSelection.isNotEmpty() && !publishing && reachable == true

    // 一键清单，按此刻可勾的包算：计数就是「点一下会勾上几个」，不点空的无名之选。每次重组
    // 直接重算而不是 remember：这张表跟着上面那几个 TextButton 一起变，缓存一个旧计数只会多一处
    // 要维护的状态，而候选清单本身也就是几十到几百行。
    val pickHits = listOf(
        PlanPick("最精简", "规则判「安全」的") { app ->
            rules?.verdict(app.packageName, profile)?.status == SafetyStatus.SAFE
        },
        PlanPick("广告追踪", "广告 / 统计 / 追踪") { app ->
            app.hasSegment(AD_SEGMENTS) || app.namedLike(AD_NAMES) ||
                app.hasPhrase(XIAOMI_AD_PACKAGES)
        },
        PlanPick("精简游戏", "游戏中心 / 游戏服务") { app ->
            app.hasSegment(GAME_SEGMENTS) || app.namedLike(GAME_NAMES)
        },
        PlanPick("精简AI", "语音助手 / AI 服务") { app ->
            app.hasSegment(AI_SEGMENTS) || app.namedLike(AI_NAMES)
        },
        PlanPick("遥测诊断", "遥测 / 诊断 / 日志") { app ->
            app.hasSegment(TELEMETRY_SEGMENTS) || app.namedLike(TELEMETRY_NAMES) ||
                app.hasPhrase(XIAOMI_TELEMETRY_PACKAGES)
        },
        PlanPick("云同步备份", "云 / 备份 / 同步") { app ->
            (app.hasSegment(CLOUD_SEGMENTS) || app.namedLike(CLOUD_NAMES)) && !app.isCloudKeystone()
        },
        PlanPick("输入法语音", "输入法 / 语音 / 翻译") { app ->
            app.hasSegment(UTILITY_SEGMENTS) || app.namedLike(UTILITY_NAMES)
        },
    ).map { pick -> pick to uploadable.filter(pick.match).map { it.packageName } }

    // A dialog drawn as a bottom sheet, not ModalBottomSheet: the platform sheet takes the scroll
    // of the list inside over to drag itself, which made this one bounce up and down whenever the
    // package list was scrolled. Here the surface sits at the bottom of the window at a fixed
    // height, so the only thing that moves is the list.
    BasicAlertDialog(
        onDismissRequest = { if (!publishing) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !publishing,
            dismissOnClickOutside = !publishing,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The scrim: the dialog owns the window, so the dimmed area is drawn here, and a tap on
            // it closes the sheet the way a platform one does.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close,
                    ),
            )
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = FlSurfaceLevel.Block.colorOf(MaterialTheme.colorScheme),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_HEIGHT_FRACTION),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    SheetGrip(enabled = !publishing, onClose = close)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.plan_upload_sheet_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        StateChip(gate)
                        Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item("net") {
                            NetCard(reachable = reachable, checking = checking, onRecheck = onRecheck)
                        }
                        item("state") { StateCard(gate = gate, awaiting = candidates.size - uploadable.size) }
                        item("device") { DeviceCard(profile = profile, totalCount = candidates.size) }
                        item("pick") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(
                                        Res.string.plan_upload_selected,
                                        provenSelection.size,
                                        uploadable.size,
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { selected = uploadable.map { it.packageName } },
                                    enabled = uploadable.isNotEmpty(),
                                ) {
                                    Text(stringResource(Res.string.plan_upload_select_all), fontSize = 11.sp)
                                }
                                TextButton(onClick = { selected = emptyList() }, enabled = selected.isNotEmpty()) {
                                    Text(stringResource(Res.string.plan_upload_clear), fontSize = 11.sp)
                                }
                            }
                        }
                        item("picks") {
                            // 主题清单：和上面的「全选 / 清空」是同一件事的几种口味，所以紧贴着那一行，
                            // 而不是跑到标题或表单旁边去。横滑一排，滑到右边还有更多分类；有一格命得中
                            // 就整排显示，命不中的格子灰着留着。「最精简」那格按本机规则算，规则还没读到
                            // 或候选全都不安全时它是灰的、点不动。
                            PickRow(
                                hits = pickHits,
                                selected = selected,
                                onToggle = { packages ->
                                    val set = packages.toSet()
                                    // 全都勾上了点一下就整批取消：同一个胶囊第二次点是反选。
                                    selected = if (set.all { it in selected }) {
                                        selected.filterNot { it in set }
                                    } else {
                                        (selected + packages).distinct()
                                    }
                                },
                            )
                        }
                        if (candidates.isEmpty()) {
                            item("empty") {
                                Text(
                                    text = stringResource(Res.string.plan_upload_no_candidates),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(candidates, key = { it.packageName }) { app ->
                                CandidateRow(
                                    app = app,
                                    checked = app.packageName in selected,
                                    proven = app.packageName in verified,
                                    // Only a package the reboot check passed can be part of the plan: the
                                    // row of a waiting one is there to say why it cannot be ticked.
                                    onToggle = {
                                        if (app.packageName in verified) {
                                            selected = if (app.packageName in selected) {
                                                selected - app.packageName
                                            } else {
                                                selected + app.packageName
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        item("name") {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 40) title = it },
                                label = { Text(stringResource(Res.string.plan_upload_name_label), fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // One tap names the plan: the same handful of shorthand words people
                            // already use for these sets ("极简"、"省电"), so a title takes a tap
                            // instead of typing on a phone keyboard. 名字多了以后这一行也横滑，
                            // 否则七格之后会被挤成两行、把下面的备注框往外推。
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(PLAN_NAME_PRESETS.size) { index ->
                                    val preset = PLAN_NAME_PRESETS[index]
                                    val picked = title.trim() == preset
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (picked) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (picked) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                }
                                            )
                                            .clickable { title = if (picked) "" else preset }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        item("note") {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { if (it.length <= 200) note = it },
                                label = { Text(stringResource(Res.string.plan_upload_note_label), fontSize = 12.sp) },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = close, enabled = !publishing) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Button(
                            onClick = { onSubmit(title.trim(), note.trim(), provenSelection) },
                            enabled = mayPublish,
                        ) {
                            if (publishing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (publishing) {
                                    stringResource(Res.string.plan_upload_publishing)
                                } else {
                                    stringResource(Res.string.plan_upload_submit)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The sheet's grip line and the way out. The pill is decoration only — the surface does not follow
 * a drag here, because the list inside is the thing being scrolled — so the close button beside it
 * is what a finger reaching for the top of the sheet finds.
 */
@Composable
private fun SheetGrip(enabled: Boolean, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 40.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(50),
                ),
        )
        IconButton(
            onClick = onClose,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.cancel),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "重启验证通过" / "部分通过" / "待重启验证", in the colour of that state. */
@Composable
private fun StateChip(gate: GateState) {
    val color = gateColor(gate)
    Text(
        text = stringResource(
            when (gate) {
                GateState.READY -> Res.string.plan_upload_state_ready
                GateState.PARTIAL -> Res.string.plan_upload_state_partial
                GateState.BLOCKED -> Res.string.plan_upload_state_blocked
            }
        ),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** The whole point of the sheet: why this batch may or may not go up, said in one card. */
@Composable
private fun StateCard(gate: GateState, awaiting: Int) {
    val color = gateColor(gate)
    val icon: ImageVector = if (gate == GateState.READY) Icons.Default.Check else Icons.Default.Warning
    val headline = stringResource(
        when (gate) {
            GateState.READY -> Res.string.plan_upload_state_ready
            GateState.PARTIAL -> Res.string.plan_upload_state_partial
            GateState.BLOCKED -> Res.string.plan_upload_state_blocked
        }
    )
    val lines = buildList {
        when (gate) {
            GateState.READY -> add(stringResource(Res.string.plan_upload_state_ready_body))
            GateState.PARTIAL -> {
                add(stringResource(Res.string.plan_upload_state_partial_body, awaiting))
                add(stringResource(Res.string.plan_upload_flow_hint))
            }
            GateState.BLOCKED -> {
                add(stringResource(Res.string.plan_upload_state_blocked_body))
                add(stringResource(Res.string.plan_upload_flow_hint))
            }
        }
    }

    Surface(
        color = color.copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.18f else 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(
                modifier = Modifier.size(20.dp).background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFFFFFFFF),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                lines.forEach { line ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The connection line: the backend is not reachable from every network — carrier data and campus
 * Wi-Fi often need a VPN or a proxy for it — so the sheet asks before the upload does. Three
 * answers, three texts: still asking, answered, and nothing came back.
 */
@Composable
private fun NetCard(reachable: Boolean?, checking: Boolean, onRecheck: () -> Unit) {
    val color = when {
        reachable == true -> successColor()
        reachable == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        reachable == true -> Icons.Default.Check
        reachable == false -> Icons.Default.Warning
        else -> Icons.Default.Info
    }
    val headline = stringResource(
        when {
            checking || reachable == null -> Res.string.plan_upload_net_checking
            reachable == true -> Res.string.plan_upload_net_ok
            else -> Res.string.plan_upload_net_blocked
        }
    )
    Surface(
        color = color.copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.18f else 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checking || reachable == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else {
                Box(
                    modifier = Modifier.size(20.dp).background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color(0xFFFFFFFF),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                if (reachable == false) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(Res.string.plan_upload_net_blocked_body),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (reachable == false) {
                TextButton(onClick = onRecheck, enabled = !checking) {
                    Text(stringResource(Res.string.plan_upload_net_retry), fontSize = 11.sp)
                }
            }
        }
    }
}

/** The device line is the promise of the whole feature: plans only match the same model. */
@Composable
private fun DeviceCard(profile: DeviceProfile?, totalCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(Res.string.plan_upload_device_label),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = profile?.shortLabel() ?: "-",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.plan_upload_candidate_count, totalCount),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 主题清单一排。每一格都自己说明挑的是什么：胶囊上写「广告追踪 · 12」，下面一行小字写「广告 /
 * 统计 / 追踪」，点一下把这一类整批勾上或整批取消。
 *
 * 横向滚动而不是 `FlowRow` 换行：格子从四个涨到七个以后，换行会把候选列表整体往下挤一截，
 * 横滑则只占一行高度，往右滑就能看见剩下的。这一排是「选择更多」，不是「页面更长」。
 *
 * 这一批里没有那一类包时，胶囊变灰、点不动——灰的那一格比一个点下去什么也不发生的按钮诚实。
 * 一排装不下七格，所以第一格右边一定露出下一格的一半：手指自然知道还能往右滑。
 */
@Composable
private fun PickRow(
    hits: List<Pair<PlanPick, List<String>>>,
    selected: List<String>,
    onToggle: (List<String>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rowState = rememberLazyListState()
    // 滑到最右就不再画渐隐：右边已经没有东西可看了，留一条影子反而像还有内容。
    val moreToTheRight by remember {
        derivedStateOf {
            val info = rowState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last < info.totalItemsCount - 1
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // 两侧留一点空白：滑到头时第一格和最后一格不贴着边，看得出这一排还能滑。
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(hits.size) { index ->
                val (pick, packages) = hits[index]
                val picked = packages.isNotEmpty() && packages.all { it in selected }
                val enabled = packages.isNotEmpty()
                val container = when {
                    picked -> colors.primary
                    else -> colors.surfaceContainerHigh
                }
                val content = when {
                    picked -> colors.onPrimary
                    enabled -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
                }
                Column(
                    modifier = Modifier
                        .width(PICK_WIDTH)
                        .clip(RoundedCornerShape(10.dp))
                        .background(container)
                        .clickable(enabled = enabled) { onToggle(packages) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = pick.label + (if (enabled) " · ${packages.size}" else ""),
                        fontSize = 11.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (enabled) pick.hint else "这一批里没有",
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        color = if (picked) colors.onPrimary.copy(alpha = 0.8f) else content.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (moreToTheRight) {
            // 右侧渐隐：告诉手指「这儿还有格子」，比一个「更多」按钮轻，也不占一格宽度。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(28.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to FlSurfaceLevel.Block.colorOf(colors),
                        )
                    ),
            )
        }
    }
}

/** One package: tickable when the reboot check passed, state chip at the end of the row. */
@Composable
private fun CandidateRow(app: AppInfo, checked: Boolean, proven: Boolean, onToggle: () -> Unit) {
    val tint = when {
        proven -> successColor()
        app.isUninstalled -> warningColor()
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = proven)
        AppIconImage(app, size = CANDIDATE_ICON)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.name,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystemApp) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "system", fontSize = 8.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(
                text = app.packageName,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = when {
                proven -> stringResource(Res.string.plan_upload_row_verified)
                app.isUninstalled -> stringResource(Res.string.plan_upload_row_awaiting)
                else -> stringResource(Res.string.plan_upload_still_installed)
            },
            fontSize = 9.sp,
            color = tint,
        )
    }
}

/** Green when the batch may go up, amber while it is waiting, red when nothing is proven. */
@Composable
private fun gateColor(gate: GateState): Color = when (gate) {
    GateState.READY -> successColor()
    GateState.PARTIAL -> warningColor()
    GateState.BLOCKED -> MaterialTheme.colorScheme.error
}
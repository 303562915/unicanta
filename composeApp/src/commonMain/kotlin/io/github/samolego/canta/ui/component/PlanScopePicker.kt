package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.data.community.CatalogBuild
import io.github.samolego.canta.data.community.CatalogModel
import io.github.samolego.canta.data.community.FamilyGroup
import io.github.samolego.canta.data.community.PEOPLE_COUNT_THRESHOLD
import io.github.samolego.canta.data.community.groupedByFamily
import io.github.samolego.canta.data.community.peopleSuffix
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf

/**
 * Which phone's plans the page is showing.
 *
 * [All] is the widest scope and the one a young community needs: every verified plan, whatever the
 * phone, so a fresh install (or a model nobody has uploaded for yet) is not an empty page. The next
 * case is this phone, matched exactly (model, codename, ROM and version). The last is a model the
 * community has plans for, optionally narrowed to one of its builds — something a visitor looks at
 * *before* owning the phone, or when a plan for the same phone was uploaded on a different build.
 */
sealed interface PlanScope {
    /** Every model at once: what the community removes most, before there is a phone to match. */
    data object All : PlanScope

    /** This phone, exactly as `plans_for` matches it. */
    data object ThisPhone : PlanScope

    /** Another model; [build] null means every build the backend knows of it. */
    data class Other(val model: CatalogModel, val build: CatalogBuild? = null) : PlanScope
}

/** What the scope calls itself on the page: "全部机型", "本机", or the model's name. */
fun PlanScope.label(ownLabel: String): String = when (this) {
    PlanScope.All -> "全部机型"
    PlanScope.ThisPhone -> ownLabel.ifBlank { "本机" }
    is PlanScope.Other -> model.label
}

/**
 * 存进设置的写法：`型号 | 版本 key | 营销名 | 品牌`，全部机型是空串，「本机」是 `|||`。
 *
 * 老写法只有 `型号 | 版本`。新写法把**判定顶部那行字要用的东西一起存下来**，这是这次改动的关键：
 *
 *  * 以前顶部显示什么，靠「拿存下的型号回目录里反查那一条」。目录是异步来的、返回时会重拉，
 *    目录一空 / 一变动，反查就失败，顶部当场换一台机器 —— 用户说的「切换过来是啥意思」。
 *  * 现在存的是**选择本身**：型号、版本、营销名、品牌。页面不再需要问目录"我选的是哪台"，
 *    顶部那行字直接来自这份记忆。用户不点胶囊，它就一直是那一台。
 *
 * 老写法（只有两段）仍然能读，读到时按两段处理并回目录反查一次；那次失败也只退到「该型号的
 * 全部版本」，不会退成「全部机型」。
 */
private const val SCOPE_SEPARATOR = "|"

/**
 * 「本机」在设置里存成什么样：型号、版本、营销名、品牌四段全空，只留三个分隔符。
 *
 * 公开出来是因为调用方（规则页）要做「不查目录就能认下的两个范围」这件事：空串 = 全部机型，
 * 这一串 = 本机，其余才是他机。两处各写一个分隔符迟早会写歪一个。
 */
const val SCOPE_THIS_PHONE = "$SCOPE_SEPARATOR$SCOPE_SEPARATOR$SCOPE_SEPARATOR"

fun PlanScope.toStoredValue(): String = when (this) {
    PlanScope.All -> ""
    PlanScope.ThisPhone -> SCOPE_THIS_PHONE
    // 营销名和品牌也存：顶部那行字（"Redmi K70"）就靠它，不必再回目录找工作那一行。
    is PlanScope.Other -> listOf(
        model.model,
        build?.key ?: "",
        model.marketName,
        model.brand,
    ).joinToString(SCOPE_SEPARATOR) { it.replace(SCOPE_SEPARATOR, "/") }
}

/**
 * 把 [toStoredValue] 存下来的那串东西还原成范围。
 *
 * 关键点：**这份记忆自己就能说明"选了哪台"，不必等目录**（[storedScopeOf] 会优先用它）。
 * 只有在老写法（2 段）、或存的时候没带营销名/品牌时，才需要 [models] 来补齐显示名与 build —
 * 而那时候目录缺失也不会退成「全部机型」，最多是显示零售编号。
 *
 * 匹配是**大小写不敏感**的：型号在目录里是按 `lowercase()` 分组的，精确比等会让「返回之后又变成
 * 全部机型」每重拉一次目录就复现一次。
 */
fun planScopeOfStored(stored: String, models: List<CatalogModel>): PlanScope {
    val raw = stored.trim()
    if (raw.isEmpty()) return PlanScope.All
    val parts = raw.split(SCOPE_SEPARATOR)
    val rawModel = parts.getOrNull(0).orEmpty()
    // 「本机」：四段全空（新写法 `|||`）或老写法的单分隔符。
    if (rawModel.isEmpty() && parts.drop(1).all { it.isEmpty() }) return PlanScope.ThisPhone
    if (rawModel.isEmpty()) return PlanScope.All
    val storedKey = parts.getOrNull(1).orEmpty()
    // 老写法与"目录缺失"共用这一条：用记忆里的型号去目录里找工作那一行，取它的 build 与名字。
    val catalogModel = models.firstOrNull { it.model.equals(rawModel, ignoreCase = true) }
        ?: models.firstOrNull { it.label.equals(rawModel, ignoreCase = true) }
    val storedMarket = parts.getOrNull(2).orEmpty()
    val storedBrand = parts.getOrNull(3).orEmpty()
    // 目录里没有这台机器时**不再退回全部机型**：记忆本身就是用户的选择，照着它显示。
    val model = catalogModel ?: CatalogModel(
        brand = storedBrand,
        model = rawModel,
        marketName = storedMarket.ifBlank { rawModel },
    )
    if (storedKey.isEmpty()) return PlanScope.Other(model)
    // 版本 key 优先按记忆里的原文比；目录里没有这台机器时直接把选择立起来（老写法存的就是这个 key），
    // 这样顶部那行字仍然说得出"HyperOS 16.0.3"，而不是退到「全部版本」。
    val build = model.builds.firstOrNull { it.key.equals(storedKey, ignoreCase = true) }
        ?: decodeBuildKey(storedKey, model, model.marketName)
    return PlanScope.Other(model, build)
}

/**
 * 老写法只存了 `rom/版本号/build` 三段而没有整行：[model] 的 builds 里找不到时，用它把选择直接
 * 立起来，让顶部那行字仍然说得出"HyperOS 16.0.3"，而不是退到「全部版本」。
 */
private fun decodeBuildKey(key: String, model: CatalogModel, marketName: String): CatalogBuild? {
    val parts = key.split("/")
    if (parts.size < 3) return null
    return CatalogBuild(
        brand = model.brand,
        model = model.model,
        marketName = marketName,
        rom = parts[0],
        romVersion = parts[1].toIntOrNull() ?: 0,
        romBuild = parts.drop(2).joinToString("/"),
    )
}

/**
 * 「只靠记忆就能认出来」的那部分：不查目录。
 *
 * 规则页首帧、以及从详情页返回的那一帧，要的就是这个 —— 顶部左上角显示哪台，由上一次的选择说了算，
 * 不由"目录到了没有"说了算。目录缺席时它给的 `Other` 已经带着完整型号与名字，`builds` 是空的，
 * 所以胶囊行在目录回来之前不会画版本胶囊（画出来也点不了），但**名字是对的**，不会换台。
 */
fun storedScopeOf(stored: String): PlanScope = planScopeOfStored(stored, emptyList())

/**
 * 「这一份列表」的标识，给滚动位置记忆当键用（见 `rememberRememberedListState`）。
 *
 * 三个范围是**三份不同的列表**（`plansAll` / `plansFor` / `plansForModel`），它们的第 N 行不是同一条
 * 方案、也不是同一个包。位置必须按这里分开存，否则换一台机型、再从详情页返回，列表会被恢复成另一份
 * 列表的行号——看上去正是「返回之后顺序跳到下面去了」。键里不带空格与冒号以外的分隔符，避免和
 * `scroll:` 前缀打架。
 */
fun PlanScope.memoryKey(): String = when (this) {
    PlanScope.All -> "all"
    PlanScope.ThisPhone -> "this"
    // 同一台机器的不同 build 各有自己的方案，位置也要分开：`rom/版本/build` 已经能区分它们。
    is PlanScope.Other -> "other:" + model.model + (build?.key?.let { ":" + it } ?: "")
}

/** The build part of the scope: only another model has a separate build chip. */
fun PlanScope.buildLabel(): String? = when (this) {
    PlanScope.All -> null
    PlanScope.ThisPhone -> null
    is PlanScope.Other -> build?.versionLabel ?: "全部版本"
}

/**
 * The bar under the page switch: which phone, and which of its builds — the state the list below is
 * drawn from.
 *
 * The model capsule *is* the button: it carries the chevron that says "there is a list behind
 * this", and the tap target is the capsule itself, not the empty row it sits in. The search button
 * on the right belongs to the list under the switch, not to the phone: it filters rows by package.
 *
 * The builds are capsules of their own, in one row that scrolls sideways instead of wrapping: a
 * model can have a dozen system versions, and a wrapped row would push the ranking down by three
 * lines before the first app. Reaching the rest costs one horizontal swipe.
 *
 * There is no "全部版本" capsule. Opening a phone already means every build of it — a capsule for a
 * choice that has already been made is a button that does nothing visible. Tapping the current build
 * again is how it goes back to every build, which is where the page started.
 *
 * [onSelectBuild] picks a build directly; null keeps the older single capsule for callers with no
 * reload story to hang on it.
 */
@Composable
fun PlanScopeBar(
    scope: PlanScope,
    ownLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null hides the button: a page with no package list under it has nothing to filter. */
    onSearch: (() -> Unit)? = null,
    searching: Boolean = false,
    /**
     * 「搜索也认应用介绍」这颗常驻按钮的值与回调：null 不画。放在这一行而不是藏在搜索框里，
     * 是因为它是一条全局规则（应用页顶栏的搜索同样吃它），藏进搜索框就变成"只有打开了搜索的人
     * 才找得到"。
     */
    descriptions: Boolean = false,
    onToggleDescriptions: (() -> Unit)? = null,
    /**
     * Switch to another build of the opened model, right here, without opening the sheet. `null`
     * means "every build". The caller does the reload — this only reports what was tapped.
     */
    onSelectBuild: ((CatalogBuild?) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val openedScope = scope as? PlanScope.Other
    // One entry per build: a LazyRow key has to be unique, and the client groups builds by
    // (rom, version, build) rather than trusting the row count the backend sent. Two builds of one
    // major version ("16.0.3", "16.0.501") are two entries here — that is the whole point.
    val builds = openedScope?.model?.builds.orEmpty().distinctBy { it.key }
    val currentBuild = openedScope?.build
    val selectedBuildIndex = builds.indexOfFirst { build ->
        currentBuild != null && currentBuild.key == build.key
    }
    // Hoisted out of the branch below: a state remembered inside it would be discarded and rebuilt
    // whenever the bar flips between "has builds" and "has none", and the scroll position with it.
    val buildRowState = rememberLazyListState()
    // Keyed on the model as well as the index: two phones can both have their selected build at
    // position 2, and an index-only key would leave the second one's capsule off screen.
    LaunchedEffect(openedScope?.model?.model, selectedBuildIndex) {
        if (builds.isEmpty()) return@LaunchedEffect
        // Nothing selected means "every build", and that row starts at its first capsule.
        buildRowState.animateScrollToItem(if (selectedBuildIndex >= 0) selectedBuildIndex else 0)
    }
    Row(
        // Not clickable as a row any more: a row-wide target under a lone chip is what made the
        // tap feel like it landed somewhere other than where the finger went.
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScopeChip(
            text = scope.label(ownLabel),
            selected = true,
            onClick = onOpen,
            // The chevron is the hint: a capsule with an arrow on it is read as a list that
            // opens, which is exactly what it does.
            chevron = true,
            // Outlined, not filled: the filled capsule is the heading's job, where it says
            // which phone the page is about. This one is the list's scope — the same model
            // name on purpose, so the two never look like two different things to switch.
            outlined = true,
            // Capped so a long name ("REDMI K100 Pro Max") cannot eat the whole row: the builds
            // after it are the point of this line, and they need room to start.
            modifier = Modifier.widthIn(max = 168.dp),
        )
        if (openedScope != null && onSelectBuild != null && builds.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            LazyRow(
                state = buildRowState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(end = 4.dp),
            ) {
                items(builds, key = { it.key }) { build ->
                    val selected = currentBuild != null && currentBuild.key == build.key
                    ScopeChip(
                        text = build.versionLabel,
                        selected = selected,
                        // Tapping the selected build again steps back to every build of the model.
                        onClick = { onSelectBuild(if (selected) null else build) },
                        outlined = true,
                    )
                }
            }
        } else {
            scope.buildLabel()?.let { build ->
                Spacer(Modifier.width(6.dp))
                // The build stays a plain capsule: it is the finer half of the scope, and two
                // outline capsules side by side would read as two equal choices instead of
                // "this phone, and then this build of it".
                ScopeChip(
                    text = build,
                    selected = false,
                    onClick = onOpen,
                    chevron = true,
                    modifier = Modifier.widthIn(max = 168.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        if (onToggleDescriptions != null) {
            Spacer(Modifier.width(6.dp))
            // 点亮 = 开着：填充胶囊写「含介绍」，关着是描边胶囊写「介绍」。关着就只说"介绍"，
            // 因为那时它还不是搜索的一部分，说「含」反而像已经开着。
            ScopeChip(
                text = if (descriptions) "含介绍" else "介绍",
                selected = descriptions,
                onClick = onToggleDescriptions,
                outlined = !descriptions,
            )
        }
        if (onSearch != null) {
            // Its own tap target: the chip switches the phone, this narrows the list, so one never
            // shadows the other.
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searching) "关闭搜索" else "搜索包名",
                    modifier = Modifier.size(18.dp),
                    // Muted, not primary: the chip beside it is the page's one action here, and a
                    // second equally loud icon reads as a second way to switch the phone.
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The picker, drawn as a sheet that comes up from the bottom of the screen with the whole list in
 * it: every model the backend has plans for, grouped by family, with how many people joined each
 * one — printed only when it is more than a handful — and, one level down, the builds of the model
 * that was opened.
 *
 * A sheet rather than a centred box: the phone is held in one hand and the chip that opened this
 * sits at the bottom of the page, so the list arrives under the thumb instead of above it, and the
 * page behind it stays visible as a dimmed band on top. It is drawn to 92% of the height on
 * purpose — the model list grows with the community, and a peek-sized sheet turns the choice into a
 * scrollbar inside a window smaller than the page it came from. It is a dialog, not
 * `ModalBottomSheet`, for the same reason the upload sheet is one: the platform sheet would take
 * the scroll of the list over to drag itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScopeDialog(
    models: List<CatalogModel>,
    ownLabel: String,
    loading: Boolean,
    scope: PlanScope,
    onSelect: (PlanScope) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * 这份机型目录是什么时候从后端拿到的（本机毫秒），0 = 还没拿到过。
     *
     * 为什么要显示：目录是**实时查后端**的（`plan_catalog`），而「我刚上传了一台手机，
     * 这里却没有它」是这个选择器最常被质疑的一件事。把「几分钟前同步的」写在标题右边，
     * 一眼就能分出是「后端里确实还没有」还是「我手上这份是旧的，点重试」。
     */
    syncedAt: Long = 0L,
) {
    var opened by remember { mutableStateOf<CatalogModel?>(null) }
    val current = opened
    // 机型列表的滚动位置提到对话框这一层：点进一台机器看版本、再按「‹ 机型」返回时，`ModelPicker`
    // 本来会整个离开组合，它内部 `remember` 出来的 `LazyListState` 跟着一起没了 —— 于是「往下翻了
    // 半天、点进去看一眼、退回来又从第一个机型开始」。现在状态在分支外面，跨这一来一回活着；
    // 键也落了盘，对话框整个关掉再打开仍停在原来那一行。
    val modelListState = rememberRememberedListState("scope:models")
    // 时间自己走：对话框可能开着好一会儿，写死一个数字会慢慢变成假话。
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            now = currentTimeMillis()
        }
    }

    BasicAlertDialog(
        // The system back button steps one level down before it closes the sheet: at "builds of a
        // model" a back press used to throw the whole sheet away, taking the reader back to the
        // page instead of to the model list they came from.
        onDismissRequest = { if (opened != null) opened = null else onDismiss() },
        // The dialog owns the window: a platform-default dialog width would clip the chips row
        // after the first few models and cap the list at a third of the screen.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The scrim covers only the band the sheet does not: a full-screen scrim under the
            // sheet itself meant a tap on any part of the sheet with no handler of its own — the
            // rounded corner notches, the gap beside the title, the hint lines right above the
            // chips — fell through to it and closed the whole sheet. Now the only thing that
            // closes it by tapping is the dimmed area the reader can actually see.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(1f - SHEET_HEIGHT_FRACTION)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        // The same one-level step as the back button: the scrim is the "outside"
                        // of the sheet, but it is also still inside the picker.
                        onClick = { if (opened != null) opened = null else onDismiss() },
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
                    SheetGrip(onClose = onDismiss)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (current != null) {
                            TextButton(onClick = { opened = null }) { Text("‹ 机型") }
                        }
                        Text(
                            text = if (current == null) "选择机型" else current.label,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (current == null) 8.dp else 2.dp, end = 12.dp),
                        )
                        // 标题右边这一格：这份目录有多新。
                        //
                        // 只在「一级（选机型）」显示：进了某台机器的 build 列表之后，看的是同一份
                        // 目录的下一层，时间一样，再写一遍只是重复。转圈时不写 —— 那时候还没有
                        // 「什么时候同步的」这回事，写个 0 秒前是假的。
                        if (current == null && !loading && syncedAt > 0L) {
                            Text(
                                text = dataAgeLabel(now - syncedAt),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    }
                    HorizontalDivider()

                    if (current == null) {
                        ModelPicker(
                            models = models,
                            ownLabel = ownLabel,
                            loading = loading,
                            scope = scope,
                            onOpenModel = { opened = it },
                            onRetry = onRetry,
                            onSelectAll = { onSelect(PlanScope.All) },
                            onSelectThisPhone = { onSelect(PlanScope.ThisPhone) },
                            listState = modelListState,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        BuildPicker(
                            model = current,
                            scope = scope,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** How tall the sheet is: most of the screen, so the list is the list and not a peek at it. */
private const val SHEET_HEIGHT_FRACTION: Float = 0.92f

/**
 * The sheet's grip line and the way out. The pill is decoration only — the surface does not follow
 * a drag here, because the list inside is the thing being scrolled — so the close button beside it
 * is what a finger reaching for the top of the sheet finds.
 */
@Composable
private fun SheetGrip(onClose: () -> Unit) {
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
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(
    models: List<CatalogModel>,
    ownLabel: String,
    loading: Boolean,
    scope: PlanScope,
    onOpenModel: (CatalogModel) -> Unit,
    onRetry: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectThisPhone: () -> Unit,
    /** 滚动位置由调用方持有：点进一台机器再返回、整个对话框关掉再开，都停在原处。 */
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            // The one thing a visitor must know before the list: choosing a phone here only changes
            // what is *read*. Nothing is blocked later, whichever phone the plan came from.
            text = "选中别的机型后，下面的列表换成它的社区方案；用他机方案时 Canta 不会拦截。",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The widest scope first, and the one a young community starts on: every plan the
            // backend has, before this phone has a single one of its own.
            ScopeChip(
                text = "全部机型",
                selected = scope == PlanScope.All,
                onClick = onSelectAll,
            )
            ScopeChip(
                text = "本机 · ${ownLabel.ifBlank { "-" }}",
                selected = scope == PlanScope.ThisPhone,
                onClick = onSelectThisPhone,
            )
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading && models.isEmpty() -> Text(
                text = "正在读取机型…",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )

            models.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(
                    text = "后端还没有任何机型的方案，或者这次没连上。",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = "重试",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            else -> LazyColumn(
                // The whole remaining height: the list is the page, not a peek at it.
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(models.groupedByFamily(), key = { it.family.name }) { group ->
                    FamilyBlock(
                        group = group,
                        scope = scope,
                        onOpenModel = onOpenModel,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyBlock(
    group: FamilyGroup,
    scope: PlanScope,
    onOpenModel: (CatalogModel) -> Unit,
) {
    Column {
        Text(
            text = "${group.family.title} · ${group.models.size}",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            group.models.forEach { model ->
                ScopeChip(
                    // Free width per chip: a model is one word, a count is an aside, and a grid of
                    // equal boxes would waste the page on the short names.
                    text = model.label + peopleSuffix(model.people),
                    selected = scope is PlanScope.Other && scope.model.model == model.model,
                    onClick = { onOpenModel(model) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildPicker(
    model: CatalogModel,
    scope: PlanScope,
    onSelect: (PlanScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selectedBuild = (scope as? PlanScope.Other)?.takeIf { it.model.model == model.model }?.build

    Column(modifier = modifier) {
        Text(
            text = "只有后端的方案库里出现过的系统版本才在这里：没有版本，就没有那一版的人试过。",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScopeChip(
                text = "全部版本" + peopleSuffix(model.people),
                selected = selectedBuild == null,
                onClick = { onSelect(PlanScope.Other(model, null)) },
            )
            model.builds.forEach { build ->
                ScopeChip(
                    text = build.versionLabel + peopleSuffix(build.people),
                    selected = selectedBuild?.key == build.key,
                    onClick = { onSelect(PlanScope.Other(model, build)) },
                )
            }
        }
        Text(
            // The same line as the chips above: no "12 人加入" while the chip itself stays quiet.
            text = "${model.label} 一共有 ${model.plans} 个方案" +
                if (model.people > PEOPLE_COUNT_THRESHOLD) "、${model.people} 人加入。" else "。",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
        )
    }
}

/** One chip of the picker: the model, the build, or this phone. */
@Composable
private fun ScopeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    /** The chip opens a list: the arrow says so, and the chip stops looking like a label. */
    chevron: Boolean = false,
    /** Drawn as an outline instead of a filled capsule: a filter, not the page's subject. */
    outlined: Boolean = false,
    /** Room for the caller to cap the width, so a long model name cannot own the row. */
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        outlined && selected -> colors.primary.copy(alpha = 0.12f)
        outlined -> Color.Transparent
        selected -> colors.primary
        else -> colors.surfaceContainerHighest
    }
    // Filled capsule: the label sits on the primary colour. Outline capsule: the label sits on the
    // page, where primary itself is too thin at 11sp — the accent lives in the border and arrow.
    val content = when {
        selected && !outlined -> colors.onPrimary
        else -> colors.onSurface
    }
    val shape = RoundedCornerShape(percent = 50)
    Row(
        // The capsule is the whole tap target and a comfortable one to hit: a label with 4dp of
        // padding reads as text, and text is what nobody thinks to tap. 32dp, not 40: these chips
        // come in rows under the page band, and at 40 they read as a second band of their own and
        // push the list down the page.
        modifier = modifier
            .heightIn(min = 32.dp)
            // Clipped before the tap: an unclipped ripple draws a square inside a capsule.
            .clip(shape)
            .background(container, shape)
            .then(
                if (outlined) {
                    // 0.6, not 0.4: this border sits on the hero's own band, and a 40% primary
                    // line over it is close to invisible in the dark theme — the capsule would
                    // stop reading as a control at all.
                    Modifier.border(1.dp, colors.primary.copy(alpha = 0.60f), shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (chevron) 7.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chevron) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = content,
            )
        }
    }
}

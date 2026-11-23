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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

/** The build part of the scope: only another model has a separate build chip. */
fun PlanScope.buildLabel(): String? = when (this) {
    PlanScope.All -> null
    PlanScope.ThisPhone -> null
    is PlanScope.Other -> build?.versionLabel ?: "全部版本"
}

/**
 * The two chips under the page switch: which phone, and which of its builds — the state the list
 * below is drawn from.
 *
 * The chip *is* the button: it carries the chevron that says "there is a list behind this", and the
 * tap target is the capsule itself, not the empty row it sits in. There used to be a "切换" word to
 * the right of it; that word is gone, because two targets for one action only made the reader guess
 * which one was the real one — and a reader who tapped the blank space between them got nothing at
 * all. The search button on the right belongs to the list under the switch, not to the phone: it
 * filters rows by package.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScopeBar(
    scope: PlanScope,
    ownLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null hides the button: a page with no package list under it has nothing to filter. */
    onSearch: (() -> Unit)? = null,
    searching: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        // Not clickable as a row any more: a row-wide target under a lone chip is what made the
        // tap feel like it landed somewhere other than where the finger went.
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chips flow rather than share one line under an ellipsis: a system version is a long
        // string, and truncating it under a capsule is what made the version look cut off. A pair
        // that does not fit on one line simply wraps.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
            )
            scope.buildLabel()?.let { build ->
                // The build stays a plain capsule: it is the finer half of the scope, and two
                // outline capsules side by side would read as two equal choices instead of
                // "this phone, and then this build of it".
                ScopeChip(
                    text = build,
                    selected = false,
                    onClick = onOpen,
                    chevron = true,
                )
            }
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
) {
    var opened by remember { mutableStateOf<CatalogModel?>(null) }
    val current = opened

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
                    selected = selectedBuild?.rom == build.rom && selectedBuild.romVersion == build.romVersion,
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
        // padding reads as text, and text is what nobody thinks to tap. 40dp rather than Material's
        // 48dp because these chips wrap onto several rows, and pads that reach past the capsule
        // would overlap the row above — which turns one tap into a coin toss.
        modifier = Modifier
            .heightIn(min = 40.dp)
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
            .padding(start = 13.dp, end = if (chevron) 9.dp else 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
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
                modifier = Modifier.size(16.dp),
                tint = content,
            )
        }
    }
}

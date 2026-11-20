package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.data.community.CatalogBuild
import io.github.samolego.canta.data.community.CatalogModel
import io.github.samolego.canta.data.community.FamilyGroup
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
 * below is drawn from. Tapping either opens the picker; the chevron says so. The search button on
 * the right opens the same picker with the model search already focused, for the phone whose name
 * the reader would otherwise scroll a long list of families to find.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScopeBar(
    scope: PlanScope,
    ownLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chips flow rather than share one line under an ellipsis: a system version is a long
        // string, and truncating it under a capsule is what made the version look cut off. A pair
        // that does not fit on one line simply wraps.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScopeChip(
                text = scope.label(ownLabel),
                selected = true,
                onClick = onOpen,
            )
            scope.buildLabel()?.let { build ->
                ScopeChip(text = build, selected = false, onClick = onOpen)
            }
        }
        Text(
            text = "切换",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )
        if (onSearch != null) {
            // Its own tap target: the row opens the picker at the model list, this opens it at the
            // field, so the button is the fast way in and never shadows the 切换 label beside it.
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索机型",
                    modifier = Modifier.size(18.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}

/**
 * The picker: every model the backend has plans for, grouped by family, with how many people joined
 * each one — printed only when it is more than a handful — and, one level down, the builds of the
 * model that was opened.
 *
 * Near-fullscreen on purpose: the model list grows with the community, and a floating box that caps
 * at a few hundred pixels turns the choice into a scrollbar inside a window smaller than the page it
 * came from. A title bar on top, the rest of the height for the list, and the hint lines where they
 * are read — above the models, under the builds.
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
    /** Opens with the model search field already on and focused, for the 搜索 button on the bar. */
    startSearch: Boolean = false,
) {
    var opened by remember { mutableStateOf<CatalogModel?>(null) }
    var searching by remember { mutableStateOf(startSearch) }
    var query by remember { mutableStateOf("") }
    val current = opened

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // The dialog owns the window: a platform-default dialog width would clip the chips row
        // after the first few models and cap the list at a third of the screen.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = FlSurfaceLevel.Block.colorOf(MaterialTheme.colorScheme),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
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
                        modifier = Modifier.weight(1f),
                    )
                    if (current == null) {
                        // The button the page bar points at: a model list that grows by the month is
                        // read by typing a name, not by scrolling family blocks.
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searching) "收起搜索" else "搜索机型",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                HorizontalDivider()

                if (current == null && searching) {
                    ModelSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
                    )
                }

                if (current == null) {
                    ModelPicker(
                        models = models,
                        ownLabel = ownLabel,
                        loading = loading,
                        scope = scope,
                        query = query,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPicker(
    models: List<CatalogModel>,
    ownLabel: String,
    loading: Boolean,
    scope: PlanScope,
    query: String,
    onOpenModel: (CatalogModel) -> Unit,
    onRetry: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectThisPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) models else models.filter { it.matchesModel(needle) }
    Column(modifier = modifier) {
        Text(
            // The one thing a visitor must know before the list: choosing a phone here only changes
            // what is *read*. Nothing is blocked later, whichever phone the plan came from.
            text = "选中别的机型后，下面的列表换成它的社区方案；用他机方案时 Canta 不会拦截。",
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
                if (filtered.isEmpty() && needle.isNotEmpty()) {
                    // Typing a name the backend has no plans for is not an error: say so, and say
                    // what it would take for the phone to appear.
                    item(key = "no-match") {
                        Text(
                            text = "没有匹配「$needle」的机型。后端只收录过有方案的机型，没人上传过就没有这一项。",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                items(filtered.groupedByFamily(), key = { it.family.name }) { group ->
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
            text = "${model.label} 一共有 ${model.plans} 个方案、${model.people} 人加入。",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
        )
    }
}

/**
 * Whether [needle] names this model. Matching is by substring on the marketing name, the retail
 * model number and the brand, so "k70", "23113RKC6C" and "redmi k70" all land on the same chip; a
 * query with spaces must match every word.
 */
private fun CatalogModel.matchesModel(needle: String): Boolean {
    val haystack = (label + " " + model + " " + brand).lowercase()
    return needle.lowercase().split(' ', '　').filter { it.isNotBlank() }.all { it in haystack }
}

/**
 * The model search field: the list below is filtered as the reader types, so there is no submit
 * button and no separate result page. Focused on open when the bar's search button started it.
 */
@Composable
private fun ModelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .focusRequester(focus),
                textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索机型，如 Redmi K70 / 23113RKC6C",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清空",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") },
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One chip of the picker: the model, the build, or this phone. */
@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary else colors.surfaceContainerHighest
    val content = if (selected) colors.onPrimary else colors.onSurface
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(container, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

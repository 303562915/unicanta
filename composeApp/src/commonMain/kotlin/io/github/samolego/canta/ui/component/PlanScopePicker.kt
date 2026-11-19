package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CatalogBuild
import io.github.samolego.canta.data.community.CatalogModel
import io.github.samolego.canta.data.community.FamilyGroup
import io.github.samolego.canta.data.community.groupedByFamily
import io.github.samolego.canta.data.community.peopleSuffix

/**
 * Which phone's plans the page is showing.
 *
 * The default is this phone, matched exactly (model, codename, ROM and version). The other case is
 * a model the community has plans for, optionally narrowed to one of its builds — something a
 * visitor looks at *before* owning the phone, or when a plan for the same phone was uploaded on a
 * different build.
 */
sealed interface PlanScope {
    /** This phone, exactly as `plans_for` matches it. */
    data object ThisPhone : PlanScope

    /** Another model; [build] null means every build the backend knows of it. */
    data class Other(val model: CatalogModel, val build: CatalogBuild? = null) : PlanScope
}

/** What the scope calls itself on the page: "本机" or the model's name. */
fun PlanScope.label(ownLabel: String): String = when (this) {
    PlanScope.ThisPhone -> ownLabel.ifBlank { "本机" }
    is PlanScope.Other -> model.label
}

/** The build part of the scope: "本机" has no separate build chip, another model always does. */
fun PlanScope.buildLabel(): String? = when (this) {
    PlanScope.ThisPhone -> null
    is PlanScope.Other -> build?.versionLabel ?: "全部版本"
}

/**
 * The two chips under the page switch: which phone, and which of its builds — the state the list
 * below is drawn from. Tapping either opens the picker; the chevron says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScopeBar(
    scope: PlanScope,
    ownLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 2.dp),
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
    }
}

/**
 * The picker: every model the backend has plans for, grouped by family, with how many people joined
 * each one — printed only when it is more than a handful — and, one level down, the builds of the
 * model that was opened.
 *
 * A model is a two-step choice on purpose: the list of models is long and the builds of one model
 * are only worth asking about after one of them was picked out.
 */
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

    CantaDialog(
        onDismissRequest = onDismiss,
        title = if (current == null) "选择机型" else current.label,
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            if (current != null) {
                TextButton(onClick = { opened = null }) { Text("返回机型") }
            }
        },
    ) {
        if (current == null) {
            ModelPicker(
                models = models,
                ownLabel = ownLabel,
                loading = loading,
                scope = scope,
                onOpenModel = { opened = it },
                onRetry = onRetry,
                onSelectThisPhone = { onSelect(PlanScope.ThisPhone) },
            )
        } else {
            BuildPicker(
                model = current,
                scope = scope,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<CatalogModel>,
    ownLabel: String,
    loading: Boolean,
    scope: PlanScope,
    onOpenModel: (CatalogModel) -> Unit,
    onRetry: () -> Unit,
    onSelectThisPhone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        // The one thing a visitor must know before the list: choosing a phone here only changes
        // what is *read*. Nothing is blocked later, whichever phone the plan came from.
        text = "选中别的机型后，下面的列表换成它的社区方案；用他机方案时 Canta 不会拦截。",
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    ScopeChip(
        text = "本机 · ${ownLabel.ifBlank { "-" }}",
        selected = scope == PlanScope.ThisPhone,
        onClick = onSelectThisPhone,
    )
    Spacer(Modifier.height(6.dp))

    when {
        loading && models.isEmpty() -> Text(
            text = "正在读取机型…",
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        models.isEmpty() -> Column(modifier = Modifier.padding(vertical = 10.dp)) {
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
            modifier = Modifier.heightIn(max = 430.dp),
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
) {
    val colors = MaterialTheme.colorScheme
    val selectedBuild = (scope as? PlanScope.Other)?.takeIf { it.model.model == model.model }?.build

    Text(
        text = "只有后端的方案库里出现过的系统版本才在这里：没有版本，就没有那一版的人试过。",
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    FlowRow(
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
        modifier = Modifier.padding(top = 12.dp),
    )
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

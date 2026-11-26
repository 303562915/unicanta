package io.github.samolego.canta.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.app.Filter
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.filter_show_all
import io.github.samolego.canta.generated.resources.filter_show_system
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 应用列表顶栏那一个筛选按钮：**一个点击，三个状态**。
 *
 * 1. 亮着的机器人 = 只显示系统应用（默认）。
 * 2. 点一下 → 暗着的九宫格 = 显示全部（系统 + 用户应用）。
 * 3. 再点一下 → **打开分类面板**：全部 / 用户应用 / 未分类 / 推荐卸载 / 进阶 / 专家 / 有风险。
 *    选一个分类之后图标变成漏斗，带颜色，表示"正在按分类筛"。
 *
 * 三个 tab（已安装 / 未安装 / 已停用）共用同一份筛选状态，所以切 tab 时选中的分类留着，
 * 只是每页按自己能匹配上的条目筛。
 *
 * 几何、按压动画与 [io.github.samolego.canta.ui.fl.FlBarButton] 逐项一致，混在顶栏里看不出区别。
 */
@Composable
fun FilterToggleButton(appListViewModel: AppListViewModel) {
    val onlySystem = appListViewModel.onlySystem
    val filter = appListViewModel.selectedFilter
    val filtering = filter != Filter.any
    val scheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }

    // 第三个状态：已经在筛分类了，就一直是漏斗；否则在"系统 / 全部"之间切。
    val description = when {
        filtering -> "分类：${stringResource(filter.label)}"
        onlySystem -> stringResource(Res.string.filter_show_system)
        else -> stringResource(Res.string.filter_show_all)
    }
    val tint = if (filtering || onlySystem) scheme.primary else scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "filter-button-press",
    )
    Box {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .drawBehind { drawCircle(FlSurfaceLevel.Page.raised.colorOf(scheme)) }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = description,
                    onClick = {
                        when {
                            // 已经在筛分类：再点一下就清掉分类、回到只看系统。
                            filtering -> appListViewModel.selectedFilter = Filter.any
                            onlySystem -> appListViewModel.onlySystem = false
                            // 全部 → 分类面板。
                            else -> showMenu = true
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                filtering -> Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )

                onlySystem -> Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )

                else -> Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        CategoryFilterMenu(
            expanded = showMenu,
            selected = filter,
            onDismiss = { showMenu = false },
            onPick = { picked ->
                appListViewModel.selectedFilter = picked
                showMenu = false
            },
        )
    }
}

/**
 * 分类面板：一行一个分类，当前那个打勾。
 *
 * 用 DropdownMenu 而不是对话框：它是"在列表上换一个看法"，选完立刻看到结果，不该盖住整页。
 */
@Composable
private fun CategoryFilterMenu(
    expanded: Boolean,
    selected: Filter,
    onDismiss: () -> Unit,
    onPick: (Filter) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(200.dp),
    ) {
        Filter.availableFilters.forEach { option ->
            val isSelected = option == selected
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(option.label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                onClick = { onPick(option) },
            )
        }
    }
}
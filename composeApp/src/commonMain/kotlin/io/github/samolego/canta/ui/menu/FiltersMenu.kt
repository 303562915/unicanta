package io.github.samolego.canta.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.filter_show_all
import io.github.samolego.canta.generated.resources.filter_show_system
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * 应用列表顶栏那一个筛选按钮：**只有一个点击，两个状态**。
 *
 * 亮着 = 只显示系统应用（默认），点一下就变成显示全部（系统 + 用户应用），再点回去。
 * 原来那个下拉菜单里的「全部 / 用户 / 未分类 / 各种标记」都不再需要——列表页只用它区分
 * "这台手机的系统包"和"我自己装的"。
 *
 * 图标跟着状态一起换：亮着的机器人 = 只有系统，暗着的九宫格 = 全部。几何、按压动画和
 * [io.github.samolego.canta.ui.fl.FlBarButton] 逐项一致，混在顶栏按钮里看不出区别。
 */
@Composable
fun FilterToggleButton(appListViewModel: AppListViewModel) {
    val onlySystem = appListViewModel.onlySystem
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(
        if (onlySystem) Res.string.filter_show_system else Res.string.filter_show_all
    )
    val tint = if (onlySystem) scheme.primary else scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "filter-button-press",
    )
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
                onClick = { appListViewModel.onlySystem = !onlySystem },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (onlySystem) Icons.Default.Android else Icons.Default.Apps,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
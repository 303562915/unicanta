package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.settings_bar_slots_hint
import io.github.samolego.canta.generated.resources.tab_apps
import io.github.samolego.canta.generated.resources.tab_recommend
import io.github.samolego.canta.generated.resources.tab_rules
import io.github.samolego.canta.generated.resources.tab_tweaks
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf

/**
 * 卡片式页面（关于、设置）共用的几何：左右留白与卡片圆角。
 *
 * 原来这两页各写一份 [CardInset]/[CardCorner]，设置页从关于页借布局，所以挪到这里一份。
 */
internal val AppCardInset = 13.dp
internal val AppCardCorner = 12.dp

/**
 * 一张卡片里的一个开关：[title] 一行，[subtitle] 可选一行小字。
 *
 * 整行都是可点的目标，右侧是 [SettingsSwitch]。
 */
@Composable
fun AppToggleCard(
    glyph: FlGlyph,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        SettingsSwitch(checked)
    }
}

/**
 * 一张通向别处的卡片：页面、对话框或站外地址。[subtitle] 是地址或当前取值，
 * [showDot] 在标题旁点一个小红点，表示那页有新东西。
 *
 * [trailing] 是箭头左边那格取值（「3 个」「已开启」这类）。它和 [subtitle] 的分工：
 * 副标题说**那页是干什么的**（一句固定的话），这一格说**现在是多少**（会变的数）。
 * 把会变的数塞进副标题，读的人每次都要重新扫一遍整句才找得到。
 */
@Composable
fun AppLinkCard(
    glyph: FlGlyph,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    showDot: Boolean = false,
    trailing: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = FlText.subheadline,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showDot) {
                    Spacer(Modifier.width(6.dp))
                    UnreadDot()
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = FlText.caption1,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SettingsChevron()
    }
}

/** 两列一行的间距，比整行卡片之间的 10dp 略窄，看起来像同一条带子上的两块。 */
private val PairGap = 8.dp

/**
 * 把两张**文字短**的卡片摆在同一行，各占一半。设置页里「启动密码」「消息通知」这类只写一行标题的
 * 卡片，一张占一行太费高度——一屏滚两下才看得完，而真正需要读的是大段说明，那些仍然独占一行。
 *
 * 几何与 [AppLinkCard] / [AppToggleCard] 对齐：左右仍然是 [AppCardInset]，卡片圆角与底色不变，
 * 所以跳着看过去并不觉得换了一套控件。**只有标题**：两半都窄，副标题写不下就挤成省略号了。
 */
@Composable
fun AppCardPair(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(PairGap),
    ) {
        content()
    }
}

/**
 * [AppCardPair] 里的一半：标题一行，右边一条通往别处的箭头。
 *
 * 和 [AppLinkCard] 的区别只有几何——没有自己的左右留白（外面由 [AppCardPair] 给），也没有副标题。
 * 写成 [RowScope] 扩展是因为它要用 `weight(1f)` 跟另一半平分宽度，而 weight 只在 Row 里成立。
 */
@Composable
fun RowScope.AppLinkTile(
    glyph: FlGlyph,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 一行小字（当前取值之类）；写不下会省略，所以只放短的。 */
    subtitle: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SettingsChevron()
    }
}

/** [AppCardPair] 里的一半：标题一行，右边一个开关。同样是 [RowScope] 扩展，理由同上。 */
@Composable
fun RowScope.AppToggleTile(
    glyph: FlGlyph,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(9.dp))
        Text(
            text = title,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        SettingsSwitch(checked)
    }
}

/** 底栏的一个槽位：[glyph]、[label]、是否点亮，以及点亮与否能不能点。 */
private data class BarSlot(
    val glyph: FlGlyph,
    val label: String,
    val lit: Boolean,
    val onToggle: (() -> Unit)?,
)

/**
 * 底栏本身，按可不可选分成两行。
 *
 * 第一行是四个常亮页：应用、规则、消息、我的——它们一直在，点不点都一样，所以只画不打勾。
 * 第二行是三个可选项：工具、推荐、帖子——点亮哪一格，底栏才多那一个入口。
 *
 * 为什么分行而不是排成一行七格：一行七格的标签在手机上会被压成「应… 规… 工…」，而"固定的"
 * 与"自己选的"本来就是两件事，分行之后不用读说明也看得出来。
 */
@Composable
fun AppBarSlotsCard(
    showTweaks: Boolean,
    showRecommend: Boolean,
    showPosts: Boolean,
    onToggleTweaks: (Boolean) -> Unit,
    onToggleRecommend: (Boolean) -> Unit,
    onTogglePosts: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fixed = listOf(
        BarSlot(FlGlyphs.dashboard, stringResource(Res.string.tab_apps), lit = true, onToggle = null),
        BarSlot(FlGlyphs.rules, stringResource(Res.string.tab_rules), lit = true, onToggle = null),
        BarSlot(FlGlyphs.requests, "消息", lit = true, onToggle = null),
        BarSlot(FlGlyphs.settings, "我的", lit = true, onToggle = null),
    )
    val optional = listOf(
        BarSlot(
            FlGlyphs.tools,
            stringResource(Res.string.tab_tweaks),
            lit = showTweaks,
            onToggle = { onToggleTweaks(!showTweaks) },
        ),
        BarSlot(
            FlGlyphs.appsList,
            stringResource(Res.string.tab_recommend),
            lit = showRecommend,
            onToggle = { onToggleRecommend(!showRecommend) },
        ),
        BarSlot(
            FlGlyphs.resources,
            "帖子",
            lit = showPosts,
            onToggle = { onTogglePosts(!showPosts) },
        ),
        // 商城不再是一格：它整页挪进了设置页（见 `SettingsScreen` 的「商城」那一行），
        // 这里只剩"底栏上还要不要多一个入口"的那三页。
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_bar_slots_hint),
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(9.dp))
        // 第一行：常亮，不可点。
        Row(modifier = Modifier.fillMaxWidth()) {
            fixed.forEach { slot -> BarSlotChip(slot, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点亮下面这一行，底栏才多一个入口",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        // 第二行：可选，点了就亮/灭。
        Row(modifier = Modifier.fillMaxWidth()) {
            optional.forEach { slot -> BarSlotChip(slot, Modifier.weight(1f)) }
            // 三格只占四分之一宽各一，剩下的补一个空位，免得它们被拉得比第一行还宽。
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BarSlotChip(slot: BarSlot, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lit = slot.lit
    val toggle = slot.onToggle
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (lit) scheme.primary else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(
                glyph = slot.glyph,
                size = 18.dp,
                tint = if (lit) scheme.onPrimary else scheme.onSurfaceVariant,
                fill = if (lit) 1f else 0f,
            )
        }
        Text(
            text = slot.label,
            style = FlText.caption2,
            color = if (lit) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

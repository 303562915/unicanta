package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 */
@Composable
fun AppLinkCard(
    glyph: FlGlyph,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    showDot: Boolean = false,
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
        SettingsChevron()
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
 * 底栏本身，一行五格：三个常用页常亮且不可点，工具页与推荐页是两个开关，
 * 槽位点亮后底栏才多一个入口。两个可选页只在这里打开。
 */
@Composable
fun AppBarSlotsCard(
    showTweaks: Boolean,
    showRecommend: Boolean,
    onToggleTweaks: (Boolean) -> Unit,
    onToggleRecommend: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val slots = listOf(
        BarSlot(FlGlyphs.dashboard, stringResource(Res.string.tab_apps), lit = true, onToggle = null),
        BarSlot(FlGlyphs.rules, stringResource(Res.string.tab_rules), lit = true, onToggle = null),
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
        BarSlot(FlGlyphs.settings, "我的", lit = true, onToggle = null),
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
        Row(modifier = Modifier.fillMaxWidth()) {
            slots.forEach { slot -> BarSlotChip(slot, Modifier.weight(1f)) }
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

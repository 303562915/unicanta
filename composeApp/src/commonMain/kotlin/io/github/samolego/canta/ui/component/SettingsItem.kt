package io.github.samolego.canta.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.collapse
import io.github.samolego.canta.generated.resources.expand
import org.jetbrains.compose.resources.stringResource

// Geometry of the settings lists, taken from FlClash's mobile settings page: narrow
// rows that sit inside a rounded card, small leading glyphs and smaller trailing
// controls, so a page fits in far fewer scrolls than a stock Material list.

/** Side inset of a group card from the screen edge. */
private val GroupInset = 13.dp

/** Corner radius of a group card. */
private val GroupCorner = 12.dp

/** Horizontal padding inside a row. */
private val RowPaddingH = 14.dp

/** Vertical padding inside a row. */
private val RowPaddingV = 9.dp

/** Shortest a row gets, so a tap target still feels like one. */
private val RowMinHeight = 44.dp

/** Leading glyph size. */
private val LeadingIcon = 19.dp

/** Gap between the leading glyph and the text. */
private val LeadingGap = 11.dp

/** Gap between the text and the trailing control. */
private val TrailingGap = 8.dp

/** Size of a trailing chevron. */
private val TrailingIcon = 18.dp

/** A switch is drawn at this fraction of its Material size. */
private const val SWITCH_SCALE = 0.78f

/** Opacity of disabled rows. */
private const val DISABLED_ALPHA = 0.4f

/**
 * A titled group of settings: an optional small [title], then one rounded card that
 * holds the rows given as [content]. Rows draw their own hairline with [SettingsItem]'s
 * `divider`, so the card never needs a divider after its last row.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = GroupInset + RowPaddingH,
                    end = GroupInset + RowPaddingH,
                    bottom = 5.dp,
                ),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GroupInset),
            shape = RoundedCornerShape(GroupCorner),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}

/**
 * A settings-style row: optional leading [icon], [title] and [description],
 * and an optional [trailing] control. Clickable when [onClick] is given.
 * [divider] draws the hairline under the row; leave it off for a group's last row.
 */
@Composable
fun SettingsItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .heightIn(min = RowMinHeight)
                .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LeadingIcon),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(LeadingGap))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                Spacer(modifier = Modifier.width(TrailingGap))
                Box(contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
        if (divider) SettingsRowDivider()
    }
}

/** A [SettingsItem] with a switch; the whole row is one accessible toggle. */
@Composable
fun SwitchSettingsItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    icon: ImageVector? = null,
    divider: Boolean = true,
) {
    SettingsItem(
        title = title,
        description = description,
        icon = icon,
        divider = divider,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { SettingsSwitch(checked) },
    )
}

/**
 * The switch every settings row uses: the same size and the same colours everywhere.
 * It is decorative — the row around it handles the toggle — so it never takes a tap.
 */
@Composable
fun SettingsSwitch(checked: Boolean) {
    // The switch keeps its own 52x32 dp layout; scaling its drawing lets the row stay
    // narrow without touching the touch target, which belongs to the row anyway.
    Box(modifier = Modifier.size(width = 41.dp, height = 26.dp), contentAlignment = Alignment.Center) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.wrapContentSize(unbounded = true).scale(SWITCH_SCALE),
        )
    }
}

/** The hairline between two rows of a group. */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = RowPaddingH),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

/** Expand/collapse chevron for expandable rows. */
@Composable
fun ExpandIcon(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = stringResource(if (expanded) Res.string.collapse else Res.string.expand),
        modifier = Modifier.size(TrailingIcon),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The chevron a row shows when tapping it leads somewhere. */
@Composable
fun SettingsChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(TrailingIcon),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A settings row with a text field. The field is seeded from [storedValue]
 * (and re-seeded when it changes), and edits are only saved through
 * [onCommit] on the keyboard's Done action or when the field loses focus.
 */
@Composable
fun SettingsTextItem(
    title: String,
    storedValue: String,
    onCommit: (String) -> Unit,
    description: String? = null,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    divider: Boolean = true,
) {
    var value by remember(storedValue) { mutableStateOf(storedValue) }
    val commit = { if (value != storedValue) onCommit(value.trim()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RowPaddingH, vertical = RowPaddingV),
            verticalAlignment = Alignment.Top
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp).size(LeadingIcon),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(LeadingGap))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).onFocusChanged { if (!it.isFocused) commit() },
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    singleLine = true,
                )
            }
        }
        if (divider) SettingsRowDivider()
    }
}

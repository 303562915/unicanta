package io.github.samolego.canta.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.CANTA_PACKAGE_NAME
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.util.CommunityScore

/** Vertical air around the text inside a tile; the row itself is as tall as its icon. */
private val TILE_VERTICAL_PADDING = 5.dp

/** Space between the icon, the text block and the checkbox. */
private val TILE_GAP = 10.dp

/**
 * One app in the list: icon, name, package with its badges, selection checkbox.
 *
 * Two lines of text and one 36dp icon, so a phone fits ~11 rows instead of the ~6 a three-line
 * `ListItem` allowed; the badges share the package line and only move onto their own line when
 * the package name left no room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppTile(
    modifier: Modifier = Modifier,
    appInfo: AppInfo,
    score: CommunityScore? = null,
    isSelected: Boolean,
    enabled: Boolean = true,
    onCheckChanged: (Boolean) -> Unit,
    onShowDialog: () -> Unit,
    showBorder: Boolean = isSelected,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    checkedBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(
        alpha = 0.2f
    ),
) {
    Card(
        modifier = modifier,
        // Flat cards on the page's own surface, with selection carried by the fill and the
        // checkbox: the rest of the app draws its cards this way, so the list matches.
        shape = MaterialTheme.shapes.small,
        elevation = null,
        border = if (showBorder) BorderStroke(1.dp, checkedColor) else null,
        colors =
                CardDefaults.cardColors(
                    containerColor =
                            if (isSelected) {
                                checkedBackgroundColor
                            } else {
                                MaterialTheme.colorScheme
                                    .surfaceContainerLow
                                    .copy(alpha = if (enabled) 1f else 0.5f)
                            }
                ),
    ) {
        Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onShowDialog)
                            .padding(
                                    start = 10.dp,
                                    end = 2.dp,
                                    top = TILE_VERTICAL_PADDING,
                                    bottom = TILE_VERTICAL_PADDING,
                            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            AppIconImage(appInfo, size = APP_TILE_ICON_SIZE)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = appInfo.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (appInfo.badgeInfo != null) {
                        AppBadge(type = appInfo.badgeInfo!!)
                    }
                    score?.let { CommunityBadge(it.verdict) }
                    if (appInfo.isSystemApp) {
                        SystemBadge()
                    }
                    if (appInfo.isDisabled) {
                        DisabledBadge()
                    }
                    if (appInfo.packageName == CANTA_PACKAGE_NAME) {
                        CantaBadge()
                    }
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckChanged,
                enabled = enabled,
                modifier = Modifier.size(40.dp),
                colors =
                        CheckboxDefaults.colors(
                            checkedColor = checkedColor,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
            )
        }
    }
}

@Composable
fun SelectedAppTile(
    modifier: Modifier = Modifier,
    appInfo: AppInfo,
    score: CommunityScore? = null,
    onCheckChanged: (Boolean) -> Unit,
    onShowDialog: () -> Unit,
) {
    AppTile(
        modifier = modifier,
        appInfo = appInfo,
        score = score,
        isSelected = true,
        showBorder = false,
        onCheckChanged = onCheckChanged,
        onShowDialog = onShowDialog,
        checkedColor = MaterialTheme.colorScheme.error,
        checkedBackgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
    )
}

/** Size app icons are rendered at, both here and by the platform handlers. */
val APP_ICON_SIZE = 48.dp

/**
 * Size the list draws them at. Smaller than [APP_ICON_SIZE] because the row is now one icon
 * tall; the bitmap is only downscaled, so nothing has to be re-rendered.
 */
val APP_TILE_ICON_SIZE = 36.dp

@Composable
fun AppIconImage(
    appInfo: AppInfo,
    size: Dp = APP_ICON_SIZE,
) {
    AppIconImage(
        appIconImage = appInfo.icon,
        contentDescription = appInfo.name,
        size = size,
    )
}

@Composable
fun AppIconImage(
    appIconImage: ImageBitmap?,
    contentDescription: String,
    size: Dp = APP_ICON_SIZE,
) {
    if (appIconImage != null) {
        Image(
            bitmap = appIconImage,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(2.dp).size(size)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = contentDescription,
            modifier = Modifier.padding(2.dp).size(size)
        )
    }
}

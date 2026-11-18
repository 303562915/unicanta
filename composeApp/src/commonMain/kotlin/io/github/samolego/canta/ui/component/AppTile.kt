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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import io.github.samolego.canta.CANTA_PACKAGE_NAME
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.util.CommunityScore

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
        elevation =
                CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        border =
                if (showBorder) {
                    BorderStroke(2.dp, checkedColor)
                } else null
    ) {
        ListItem(
            colors =
                    ListItemDefaults.colors(
                        containerColor =
                                if (isSelected) {
                                    checkedBackgroundColor
                                } else {
                                    MaterialTheme.colorScheme
                                        .surface
                                        .copy(alpha = if (enabled) 1f else 0.5f)
                                }
                    ),
            modifier =
                    Modifier.clickable(
                        onClick = onShowDialog,
                    ),
            // One line each: long app and package names shrink and ellipsise instead of
            // wrapping into a second line and inflating every row.
            headlineContent = {
                Text(
                    text = appInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = appInfo.packageName,
                            modifier = Modifier.weight(9f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FlowRow {
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
            },
            leadingContent = { AppIconImage(appInfo) },
            trailingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onCheckChanged,
                    enabled = enabled,
                    colors =
                            CheckboxDefaults.colors(
                                checkedColor = checkedColor,
                                uncheckedColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                            )
                )
            }
        )
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

/** Size app icons are shown at; Android renders them at exactly this size. */
val APP_ICON_SIZE = 48.dp

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

package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.clear_search
import io.github.samolego.canta.generated.resources.more_options
import io.github.samolego.canta.generated.resources.search
import io.github.samolego.canta.generated.resources.search_apps
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBar
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.menu.FilterToggleButton
import io.github.samolego.canta.ui.menu.MoreOptionsMenu
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * The root page's bar: FlClash's page toolbar, with the app's name as its large title
 * and the app list's own actions as round buttons at its end. Searching swaps the title
 * for a field on the raised surface, which is where FlClash puts a search that fills the
 * bar.
 */
@Composable
fun CantaTopBar(
    openBadgesInfoDialog: () -> Unit,
    navigateToPage: (route: String) -> Unit,
    appListViewModel: AppListViewModel,
) {
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    FlTopBar(
        title = APP_NAME,
        actions = {
            AnimatedVisibility(
                visible = searchActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                SearchField(
                    value = appListViewModel.searchQuery,
                    onValueChange = { appListViewModel.searchQuery = it },
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.width(212.dp),
                )
            }
            FlBarButton(
                glyph = if (searchActive) FlGlyphs.close else FlGlyphs.search,
                contentDescription = stringResource(
                    if (searchActive) Res.string.clear_search else Res.string.search
                ),
                onClick = {
                    if (searchActive) {
                        appListViewModel.searchQuery = ""
                        searchActive = false
                    } else {
                        searchActive = true
                    }
                },
            )
            // 一个图标、两个状态：亮=只显示系统应用，暗=显示全部。
            FilterToggleButton(appListViewModel = appListViewModel)
            FlBarButton(
                glyph = FlGlyphs.more,
                contentDescription = stringResource(Res.string.more_options),
                selected = showMoreOptionsMenu,
                onClick = { showMoreOptionsMenu = !showMoreOptionsMenu },
            )

            MoreOptionsMenu(
                showMenu = showMoreOptionsMenu,
                showBadgeInfoDialog = openBadgesInfoDialog,
                navigateToPage = navigateToPage,
                onDismiss = { showMoreOptionsMenu = false },
            )
        },
    )

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
        }
    }
}

/** A field on the surface above the page, the shape a bar's search takes in FlClash. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = FlSurfaceLevel.Page.raised.colorOf(scheme)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind { drawRect(fill) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(Res.string.search_apps),
                style = FlText.subheadline,
                color = scheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            textStyle = LocalTextStyle.current.merge(FlText.subheadline.copy(color = scheme.onSurface)),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
        )
    }
}

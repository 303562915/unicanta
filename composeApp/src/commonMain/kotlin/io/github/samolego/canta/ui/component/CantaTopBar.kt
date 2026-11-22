package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.clear_search
import io.github.samolego.canta.generated.resources.filter
import io.github.samolego.canta.generated.resources.more_options
import io.github.samolego.canta.generated.resources.search
import io.github.samolego.canta.generated.resources.search_apps
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBar
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.menu.FiltersMenu
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
    /** Replies to this install's comments that it has not opened yet: drawn as a red dot. */
    unreadReplies: Int = 0,
    /** Opens the inbox, which is the page the dot is about; opening it clears the dot. */
    onOpenInbox: () -> Unit = {},
) {
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showFiltersMenu by remember { mutableStateOf(false) }
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
            FlBarButton(
                glyph = FlGlyphs.filter,
                contentDescription = stringResource(Res.string.filter),
                selected = showFiltersMenu,
                onClick = { showFiltersMenu = !showFiltersMenu },
            )
            FlBarButton(
                glyph = FlGlyphs.more,
                contentDescription = stringResource(Res.string.more_options),
                selected = showMoreOptionsMenu,
                onClick = { showMoreOptionsMenu = !showMoreOptionsMenu },
            )
            // The inbox sits at the end of the bar, where a notification entry belongs: it is the
            // one action here that is about something that happened without the page being open.
            InboxBarButton(unread = unreadReplies, onClick = onOpenInbox)

            FiltersMenu(
                showMenu = showFiltersMenu,
                onDismiss = { showFiltersMenu = false },
                appListViewModel = appListViewModel,
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

/**
 * The bar's entry to the inbox, wearing the count of what is waiting in it.
 *
 * It is drawn like the other bar buttons, but with a Material glyph instead of one of the FlClash
 * set: the set has no message icon. The bell is the shape every phone owner already reads as "news
 * arrived", and it is the one that carries a number well — a speech bubble with a "12" on its
 * corner is a chat, a bell with a "12" is the notification list this button actually opens.
 */
@Composable
private fun InboxBarButton(unread: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "inbox-button-press",
    )
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .scale(pressScale)
            .drawBehind { drawCircle(FlSurfaceLevel.Page.raised.colorOf(scheme)) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "消息",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = if (unread > 0) "消息，$unread 条没看" else "消息",
            modifier = Modifier.size(20.dp),
            tint = scheme.onSurface,
        )
        // The count sits on the icon's top corner, leaning a little past it the way a chat app's
        // badge overlaps the button it belongs to; the bar's 8 dp gap has room for the overhang.
        UnreadBadge(
            count = unread,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 3.dp),
        )
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

package io.github.samolego.canta.ui.screen

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.samolego.canta.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.accept
import io.github.samolego.canta.generated.resources.contribute
import io.github.samolego.canta.generated.resources.exit
import io.github.samolego.canta.generated.resources.onboarding_about_chip_issue
import io.github.samolego.canta.generated.resources.onboarding_about_chip_pr
import io.github.samolego.canta.generated.resources.onboarding_about_chip_rules
import io.github.samolego.canta.generated.resources.onboarding_about_chip_translation
import io.github.samolego.canta.generated.resources.onboarding_about_join_body
import io.github.samolego.canta.generated.resources.onboarding_about_join_title
import io.github.samolego.canta.generated.resources.onboarding_about_original_sub
import io.github.samolego.canta.generated.resources.onboarding_about_original_title
import io.github.samolego.canta.generated.resources.onboarding_about_repo_title
import io.github.samolego.canta.generated.resources.onboarding_about_subtitle
import io.github.samolego.canta.generated.resources.onboarding_about_tile_chinese_body
import io.github.samolego.canta.generated.resources.onboarding_about_tile_chinese_title
import io.github.samolego.canta.generated.resources.onboarding_about_tile_rescue_body
import io.github.samolego.canta.generated.resources.onboarding_about_tile_rescue_title
import io.github.samolego.canta.generated.resources.onboarding_about_tile_root_body
import io.github.samolego.canta.generated.resources.onboarding_about_tile_root_title
import io.github.samolego.canta.generated.resources.onboarding_about_tile_rules_body
import io.github.samolego.canta.generated.resources.onboarding_about_tile_rules_title
import io.github.samolego.canta.generated.resources.onboarding_about_title
import io.github.samolego.canta.generated.resources.onboarding_back
import io.github.samolego.canta.generated.resources.onboarding_badges_title
import io.github.samolego.canta.generated.resources.onboarding_change_later
import io.github.samolego.canta.generated.resources.onboarding_change_title
import io.github.samolego.canta.generated.resources.onboarding_contribute_subtitle
import io.github.samolego.canta.generated.resources.onboarding_contribute_title
import io.github.samolego.canta.generated.resources.onboarding_dont_share
import io.github.samolego.canta.generated.resources.onboarding_ip_note
import io.github.samolego.canta.generated.resources.onboarding_ip_title
import io.github.samolego.canta.generated.resources.onboarding_never_sent
import io.github.samolego.canta.generated.resources.onboarding_never_sent_title
import io.github.samolego.canta.generated.resources.onboarding_next
import io.github.samolego.canta.generated.resources.onboarding_network_enable
import io.github.samolego.canta.generated.resources.onboarding_network_note
import io.github.samolego.canta.generated.resources.onboarding_network_recheck
import io.github.samolego.canta.generated.resources.onboarding_network_requirement
import io.github.samolego.canta.generated.resources.onboarding_network_title
import io.github.samolego.canta.generated.resources.onboarding_network_vpn_off
import io.github.samolego.canta.generated.resources.onboarding_network_vpn_on
import io.github.samolego.canta.generated.resources.onboarding_privilege_detect
import io.github.samolego.canta.generated.resources.onboarding_privilege_dhizuku_body
import io.github.samolego.canta.generated.resources.onboarding_privilege_grant
import io.github.samolego.canta.generated.resources.onboarding_privilege_note
import io.github.samolego.canta.generated.resources.onboarding_privilege_recommended
import io.github.samolego.canta.generated.resources.onboarding_privilege_shizuku_action
import io.github.samolego.canta.generated.resources.onboarding_privilege_shizuku_body
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_pending
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_available
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_running
import io.github.samolego.canta.generated.resources.onboarding_privilege_stellar_action
import io.github.samolego.canta.generated.resources.onboarding_privilege_stellar_body
import io.github.samolego.canta.generated.resources.onboarding_privilege_step_grant
import io.github.samolego.canta.generated.resources.onboarding_privilege_step_install
import io.github.samolego.canta.generated.resources.onboarding_privilege_step_start
import io.github.samolego.canta.generated.resources.onboarding_privilege_subtitle
import io.github.samolego.canta.generated.resources.onboarding_privilege_title
import io.github.samolego.canta.generated.resources.onboarding_opt_out_note
import io.github.samolego.canta.generated.resources.onboarding_origin_chip_ci
import io.github.samolego.canta.generated.resources.onboarding_origin_chip_client
import io.github.samolego.canta.generated.resources.onboarding_origin_chip_migration
import io.github.samolego.canta.generated.resources.onboarding_origin_chip_server
import io.github.samolego.canta.generated.resources.onboarding_origin_code_body
import io.github.samolego.canta.generated.resources.onboarding_origin_code_title
import io.github.samolego.canta.generated.resources.onboarding_origin_selfhost_body
import io.github.samolego.canta.generated.resources.onboarding_origin_selfhost_title
import io.github.samolego.canta.generated.resources.onboarding_origin_step1_body
import io.github.samolego.canta.generated.resources.onboarding_origin_step1_title
import io.github.samolego.canta.generated.resources.onboarding_origin_step2_body
import io.github.samolego.canta.generated.resources.onboarding_origin_step2_title
import io.github.samolego.canta.generated.resources.onboarding_origin_step3_body
import io.github.samolego.canta.generated.resources.onboarding_origin_step3_title
import io.github.samolego.canta.generated.resources.onboarding_origin_subtitle
import io.github.samolego.canta.generated.resources.onboarding_origin_title
import io.github.samolego.canta.generated.resources.onboarding_read_readme
import io.github.samolego.canta.generated.resources.onboarding_shared
import io.github.samolego.canta.generated.resources.onboarding_shared_title
import io.github.samolego.canta.generated.resources.onboarding_start
import io.github.samolego.canta.generated.resources.onboarding_welcome_subtitle
import io.github.samolego.canta.generated.resources.onboarding_welcome_title
import io.github.samolego.canta.generated.resources.privacy_policy
import io.github.samolego.canta.generated.resources.settings_privilege_dhizuku_active
import io.github.samolego.canta.generated.resources.show_whats_sent
import io.github.samolego.canta.ui.dialog.NoWarrantyContent
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.dialog.TelemetryPreviewDialog
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.theme.GreenOk
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val REPO_URL = "https://github.com/303562915/unicanta"
private const val README_URL = "$REPO_URL/blob/master/README.md"
private const val PRIVACY_POLICY_URL = "$REPO_URL/blob/master/privacy_policy.md"
private const val ORIGINAL_URL = "https://github.com/samolego/Canta"

/** The privileged runtime page points at both; Stellar is the one we steer people to. */
private const val STELLAR_URL = "https://github.com/roro2239/Stellar/releases"
private const val SHIZUKU_URL = "https://github.com/RikkaApps/Shizuku/releases"

/** Widest the onboarding content gets, so it stays readable in a desktop window. */
private val MAX_CONTENT_WIDTH = 560.dp

private const val PAGE_WARRANTY = 0
private const val PAGE_CONTRIBUTE = 1
private const val PAGE_ORIGIN = 2
private const val PAGE_ABOUT = 3
private const val PAGE_PRIVILEGE = 4
private const val PAGE_NETWORK = 5
private const val PAGE_COUNT = 6


/**
 * First-run flow: the no-warranty disclaimer, the analytics opt-in, two read-only pages that say
 * where the project came from and what it does, then the two pages that decide whether the app can
 * do anything: the privileged runtime, and the network the community data lives behind. The disclaimer
 * can't be skipped: only Accept leads past it, and system back on this start destination just
 * leaves the app.
 *
 * The analytics answer is taken on page two but only written when the last page is done, so the
 * pages after it can still be walked back from without losing the choice.
 *
 * Page five is the one that matters for actually doing something: without Shizuku (or Stellar, the
 * Shizuku-compatible fork we point people at) nothing can be removed at all, so the page checks the
 * runtime live, offers both downloads, and lets the user go on either way — the app is still useful
 * as a list, and the setup dialog is reachable from the main screen.
 *
 * Page six covers the other half: the community backend is Supabase in Tokyo, so with no proxy the
 * community scores, plans and reports all read as if the server were down. It says so plainly and
 * opens the proxy when there is one; the last button is "start", which is what ends the flow.
 *
 * @param handler the platform's package handler, asked for [PrivilegeStatus].
 * @param onFinish called with whether the user chose to contribute.
 */
@Composable
fun OnboardingScreen(
    platform: CantaPlatform,
    handler: CantaHandler,
    closeApp: () -> Unit,
    onFinish: (contribute: Boolean) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_WARRANTY) }
    // Default is "don't share": page two always sets it before the flow can move on.
    var contribute by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(300)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(300)))
                    .togetherWith(
                        slideOutHorizontally(tween(300)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)),
                    )
                    .using(SizeTransform(clip = false))
            },
        ) { current ->
            when (current) {
                PAGE_WARRANTY -> WarrantyPage(closeApp = closeApp, onAccept = { page = PAGE_CONTRIBUTE })
                PAGE_CONTRIBUTE -> ContributePage(
                    platform = platform,
                    onChoose = { choice ->
                        contribute = choice
                        page = PAGE_ORIGIN
                    },
                )
                PAGE_ORIGIN -> OriginPage(
                    platform = platform,
                    onBack = { page = PAGE_CONTRIBUTE },
                    onNext = { page = PAGE_ABOUT },
                )
                PAGE_ABOUT -> AboutPage(
                    platform = platform,
                    onBack = { page = PAGE_ORIGIN },
                    onNext = { page = PAGE_PRIVILEGE },
                )
                PAGE_PRIVILEGE -> PrivilegePage(
                    platform = platform,
                    handler = handler,
                    onBack = { page = PAGE_ABOUT },
                    onNext = { page = PAGE_NETWORK },
                )
                else -> NetworkPage(
                    platform = platform,
                    onBack = { page = PAGE_PRIVILEGE },
                    onDone = { onFinish(contribute) },
                )
            }
        }
    }
}

/** Scrollable [content] on top, a fixed bottom bar with the dots and the left / right actions. */
@Composable
private fun OnboardingPage(
    pageIndex: Int,
    badge: @Composable () -> Unit,
    title: StringResource,
    subtitle: StringResource,
    negative: @Composable () -> Unit,
    positive: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            badge()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
        PageDots(current = pageIndex, count = PAGE_COUNT)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            negative()
            positive()
        }
    }
}

/**
 * The flow's two button styles: one filled, one outlined, both the same height with the same
 * 12 dp corner and the same label size, so the pair reads as one control.
 */
@Composable
private fun OnboardingButton(onClick: () -> Unit, primary: Boolean, label: String) {
    val shape = MaterialTheme.shapes.small
    val padding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
    if (primary) {
        Button(onClick = onClick, shape = shape, contentPadding = padding) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = shape, contentPadding = padding) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

/** [icon] in a filled circle, the header of a page. */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val color = if (index == current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun WarrantyPage(closeApp: () -> Unit, onAccept: () -> Unit) {
    OnboardingPage(
        pageIndex = PAGE_WARRANTY,
        badge = {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
            )
        },
        title = Res.string.onboarding_welcome_title,
        subtitle = Res.string.onboarding_welcome_subtitle,
        negative = {
            OnboardingButton(
                onClick = closeApp,
                primary = false,
                label = stringResource(Res.string.exit),
            )
        },
        positive = {
            OnboardingButton(
                onClick = onAccept,
                primary = true,
                label = stringResource(Res.string.accept),
            )
        },
    ) {
        NoWarrantyContent()
    }
}

/**
 * Page two: the analytics opt-in. Both buttons record the answer and move on; the choice is only
 * written to settings by the last page, so the two pages after this one stay walkable.
 */
@Composable
private fun ContributePage(platform: CantaPlatform, onChoose: (Boolean) -> Unit) {
    var showPreview by rememberSaveable { mutableStateOf(false) }

    // Equal prominence on purpose: neither choice is pre-selected, both are full buttons.
    OnboardingPage(
        pageIndex = PAGE_CONTRIBUTE,
        badge = { IconBadge(Icons.Default.Favorite) },
        title = Res.string.onboarding_contribute_title,
        subtitle = Res.string.onboarding_contribute_subtitle,
        negative = {
            OnboardingButton(
                onClick = { onChoose(false) },
                primary = false,
                label = stringResource(Res.string.onboarding_dont_share),
            )
        },
        positive = {
            OnboardingButton(
                onClick = { onChoose(true) },
                primary = true,
                label = stringResource(Res.string.contribute),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Default.CheckCircle, Res.string.onboarding_shared_title, Res.string.onboarding_shared)
            InfoRow(Icons.Default.Block, Res.string.onboarding_never_sent_title, Res.string.onboarding_never_sent)
            InfoRow(Icons.Default.Lock, Res.string.onboarding_ip_title, Res.string.onboarding_ip_note)
            InfoRow(Icons.Default.Info, Res.string.onboarding_badges_title, Res.string.onboarding_opt_out_note)
            InfoRow(Icons.Default.Settings, Res.string.onboarding_change_title, Res.string.onboarding_change_later)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = { showPreview = true }) { Text(stringResource(Res.string.show_whats_sent)) }
            TextButton(onClick = { platform.openUrl(PRIVACY_POLICY_URL) }) {
                Text(stringResource(Res.string.privacy_policy))
            }
        }
    }

    if (showPreview) TelemetryPreviewDialog(onDismiss = { showPreview = false })
}

/** One step of the origin timeline: a numbered dot, then a title and a line of prose. */
private class TimelineStep(
    val index: String,
    val title: String,
    val body: String,
    val last: Boolean = false,
)

/** Page three: where the project comes from. A timeline, then what anyone can check for themselves. */
@Composable
private fun OriginPage(platform: CantaPlatform, onBack: () -> Unit, onNext: () -> Unit) {
    OnboardingPage(
        pageIndex = PAGE_ORIGIN,
        badge = { IconBadge(Icons.Default.Build) },
        title = Res.string.onboarding_origin_title,
        subtitle = Res.string.onboarding_origin_subtitle,
        negative = {
            OnboardingButton(
                onClick = onBack,
                primary = false,
                label = stringResource(Res.string.onboarding_back),
            )
        },
        positive = {
            OnboardingButton(
                onClick = onNext,
                primary = true,
                label = stringResource(Res.string.onboarding_next),
            )
        },
    ) {
        Timeline(
            listOf(
                TimelineStep(
                    "1",
                    stringResource(Res.string.onboarding_origin_step1_title),
                    stringResource(Res.string.onboarding_origin_step1_body),
                ),
                TimelineStep(
                    "2",
                    stringResource(Res.string.onboarding_origin_step2_title),
                    stringResource(Res.string.onboarding_origin_step2_body),
                ),
                TimelineStep(
                    "3",
                    stringResource(Res.string.onboarding_origin_step3_title),
                    stringResource(Res.string.onboarding_origin_step3_body),
                    last = true,
                ),
            ),
        )
        Spacer(Modifier.height(18.dp))
        VerifyCard(
            chips = listOf(
                stringResource(Res.string.onboarding_origin_chip_client),
                stringResource(Res.string.onboarding_origin_chip_server),
                stringResource(Res.string.onboarding_origin_chip_migration),
                stringResource(Res.string.onboarding_origin_chip_ci),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { platform.openUrl(README_URL) }) {
                Text(stringResource(Res.string.onboarding_read_readme))
            }
        }
    }
}

/** Numbered dots joined by a hairline, the shape of "how we got here". */
@Composable
private fun Timeline(steps: List<TimelineStep>) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        steps.forEach { step ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(
                    modifier = Modifier.width(30.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = step.index,
                            style = FlText.caption2,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimary,
                        )
                    }
                    if (!step.last) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(2.dp)
                                .padding(top = 5.dp)
                                .background(scheme.outlineVariant, RoundedCornerShape(1.dp)),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (step.last) 0.dp else 18.dp),
                ) {
                    Text(
                        text = step.title,
                        style = FlText.headline,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = step.body,
                        style = FlText.footnote,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The "check it yourself" card: what is in the repository, the chips that name it, and the backend note. */
@Composable
private fun VerifyCard(chips: List<String>) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_origin_code_title),
            style = FlText.subheadline,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.onboarding_origin_code_body),
            style = FlText.footnote,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { Chip(text = it, container = scheme.primary.copy(alpha = 0.10f), content = scheme.primary) }
        }
        Spacer(Modifier.height(13.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
            Column {
                Text(
                    text = stringResource(Res.string.onboarding_origin_selfhost_title),
                    style = FlText.subheadline,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.onboarding_origin_selfhost_body),
                    style = FlText.caption1,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Page six, the last one: the network the community features depend on. The backend is Supabase in
 * Tokyo, so without a proxy every community read fails and the app looks broken while it is only
 * unreachable.
 * The page states the situation, gives the shortest path to a working proxy, and lets the user walk
 * on either way — local rules and removal never touch the network.
 *
 * 这一页**不检测** VPN。网卡在不在和连不连得上不是一回事：分流规则、公司网络、节点失效都会让两者
 * 相反，硬判就会对用得好好的人说"你没开代理"。真正回答这个问题的是请求本身——主界面在数据拉不
 * 到时才提示；这一页只把两条出路（开代理 / 换网络）放在手边。
 */
@Composable
private fun NetworkPage(platform: CantaPlatform, onBack: () -> Unit, onDone: () -> Unit) {
    OnboardingPage(
        pageIndex = PAGE_NETWORK,
        badge = { IconBadge(Icons.Default.Info) },
        title = Res.string.onboarding_network_title,
        subtitle = Res.string.onboarding_network_requirement,
        negative = {
            OnboardingButton(
                onClick = onBack,
                primary = false,
                label = stringResource(Res.string.onboarding_back),
            )
        },
        positive = {
            OnboardingButton(
                onClick = onDone,
                primary = true,
                label = stringResource(Res.string.onboarding_start),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingButton(
                onClick = { platform.openProxySettings() },
                primary = true,
                label = stringResource(Res.string.onboarding_network_enable),
            )
            OnboardingButton(
                onClick = { platform.openNetworkSettings() },
                primary = false,
                label = stringResource(Res.string.vpn_notice_switch_network),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_network_note),
            style = FlText.caption1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Page five: the privileged runtime. Everything the app does after this page needs one, so this is
 * the page that has to get the user somewhere — Stellar first (a tuned Shizuku fork that speaks the
 * same API), plain Shizuku as the equally valid second option, and a live status line that turns
 * into "you're set" the moment either one authorizes Canta.
 *
 * Nothing here is a gate: someone who only wants to browse the list can press next, and the main
 * screen's setup dialog can pick the setup up later.
 */
@Composable
private fun PrivilegePage(
    platform: CantaPlatform,
    handler: CantaHandler,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    // Re-read on resume: every step happens in another app, and the user comes straight back.
    var status by remember { mutableStateOf(handler.privilegeStatus) }
    var isAuthorized by remember { mutableStateOf(handler.isAuthorized) }
    // Which privilege is in play: a rooted phone needs no runtime app at all, so the whole page
    // about installing Shizuku is the wrong instruction for it.
    val kind = handler.privilegeKind
    val scope = rememberCoroutineScope()

    fun refresh() {
        status = handler.privilegeStatus
        isAuthorized = handler.isAuthorized
    }

    LifecycleResumeEffect(handler) {
        refresh()
        onPauseOrDispose {}
    }

    val ready = status == PrivilegeStatus.ACTIVE || isAuthorized

    OnboardingPage(
        pageIndex = PAGE_PRIVILEGE,
        badge = { IconBadge(if (ready) Icons.Default.CheckCircle else Icons.Default.Lock) },
        title = Res.string.onboarding_privilege_title,
        subtitle = Res.string.onboarding_privilege_subtitle,
        negative = {
            OnboardingButton(
                onClick = onBack,
                primary = false,
                label = stringResource(Res.string.onboarding_back),
            )
        },
        positive = {
            OnboardingButton(
                onClick = onNext,
                primary = true,
                label = stringResource(Res.string.onboarding_next),
            )
        },
    ) {
        StatusCard(
            ready = ready,
            status = status,
            kind = kind,
            onDetect = { refresh() },
            onGrant = {
                if (status == PrivilegeStatus.NOT_AUTHORIZED) {
                    scope.launch {
                        handler.requestAuthorization()
                        refresh()
                    }
                } else if (kind == PrivilegeKind.ROOT) {
                    // Root is the one case with nothing to install: the grant lives in the root
                    // manager's own prompt, which `requestAuthorization` is what raises.
                    scope.launch {
                        handler.requestAuthorization()
                        refresh()
                    }
                } else {
                    // Nothing installed, or the service is down: the fix lives in the runtime app,
                    // and the two download cards right below are the way there.
                    platform.openUrl(STELLAR_URL)
                }
            },
        )
        if (kind == PrivilegeKind.ROOT) {
            // A rooted phone is done here: no runtime app, no three steps. Saying so plainly is the
            // whole point — the cards below would otherwise tell a rooted user to install Shizuku.
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FlSurfaceLevel.Card.colorOf(MaterialTheme.colorScheme))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "这台手机有 root，Canta 直接用 root 工作",
                    style = FlText.subheadline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "不需要装 Shizuku 或 Stellar。第一次卸载时会弹一次 root 授权，允许之后就一直免询问。" +
                        "如果你在 root 管理器里拒绝过授权，回到这里点「重新检测」或重装 Canta 再授权一次。",
                    style = FlText.caption1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.height(14.dp))
            // A phone already running Dhizuku is not told to install a fifth app for nothing: the
            // note says what it can already do, and the cards below are the route to the removal
            // half it does not have.
            if (kind == PrivilegeKind.DHIZUKU) {
                Text(
                    text = stringResource(Res.string.onboarding_privilege_dhizuku_body),
                    style = FlText.caption1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            RuntimeCard(
                recommended = true,
                mark = "St",
                title = "Stellar",
                body = Res.string.onboarding_privilege_stellar_body,
                action = Res.string.onboarding_privilege_stellar_action,
                onOpen = { platform.openUrl(STELLAR_URL) },
            )
            Spacer(Modifier.height(10.dp))
            RuntimeCard(
                recommended = false,
                mark = "Sh",
                title = "Shizuku",
                body = Res.string.onboarding_privilege_shizuku_body,
                action = Res.string.onboarding_privilege_shizuku_action,
                onOpen = { platform.openUrl(SHIZUKU_URL) },
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepLine("1", Res.string.onboarding_privilege_step_install, done = status != PrivilegeStatus.NOT_AVAILABLE)
                StepLine("2", Res.string.onboarding_privilege_step_start, done = status == PrivilegeStatus.NOT_AUTHORIZED || ready)
                StepLine("3", Res.string.onboarding_privilege_step_grant, done = ready)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.onboarding_privilege_note),
            style = FlText.caption1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The live state of the runtime: one coloured dot, one sentence in plain words, and the one button
 * that moves the user forward from wherever they are.
 */
@Composable
private fun StatusCard(
    ready: Boolean,
    status: PrivilegeStatus,
    kind: PrivilegeKind,
    onDetect: () -> Unit,
    onGrant: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // A rooted phone has no runtime to install or start, so the two failure sentences mean something
    // else there: "no root grant yet" and "root was refused". Telling a rooted user to install
    // Shizuku is exactly the wrong instruction, and it is what this branch exists to avoid.
    val message = if (kind == PrivilegeKind.ROOT) {
        if (ready) {
            Res.string.onboarding_privilege_state_root_active
        } else {
            Res.string.onboarding_privilege_state_root_pending
        }
    } else {
        when {
            // Dhizuku grants a narrower thing than a shell, so "you're ready" would overpromise:
            // freezing works, and the removal half is what the cards right below are for.
            kind == PrivilegeKind.DHIZUKU && ready -> Res.string.settings_privilege_dhizuku_active
            ready -> Res.string.onboarding_privilege_state_active
            status == PrivilegeStatus.NOT_AUTHORIZED -> Res.string.onboarding_privilege_state_not_authorized
            status == PrivilegeStatus.NOT_RUNNING -> Res.string.onboarding_privilege_state_not_running
            else -> Res.string.onboarding_privilege_state_not_available
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (ready) scheme.primaryContainer else FlSurfaceLevel.Card.colorOf(scheme))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(if (ready) GreenOk else scheme.primary, CircleShape),
            )
            Text(
                text = stringResource(message),
                style = FlText.subheadline,
                fontWeight = FontWeight.SemiBold,
                color = if (ready) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (!ready) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingButton(
                    onClick = onGrant,
                    primary = true,
                    label = stringResource(Res.string.onboarding_privilege_grant),
                )
                OnboardingButton(
                    onClick = onDetect,
                    primary = false,
                    label = stringResource(Res.string.onboarding_privilege_detect),
                )
            }
        }
    }
}

/** One of the two runtimes: a mark, a name with the "recommended" pill, one line, one download. */
@Composable
private fun RuntimeCard(
    recommended: Boolean,
    mark: String,
    title: String,
    body: StringResource,
    action: StringResource,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (recommended) scheme.primary.copy(alpha = 0.06f) else FlSurfaceLevel.Card.colorOf(scheme)
            )
            .clickable(onClick = onOpen)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(scheme.primary, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark,
                style = FlText.subheadline,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = FlText.subheadline,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                if (recommended) {
                    Chip(
                        text = stringResource(Res.string.onboarding_privilege_recommended),
                        container = scheme.primary.copy(alpha = 0.14f),
                        content = scheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(body),
                style = FlText.caption1,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(action),
                style = FlText.caption1,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
        }
    }
}

/** A numbered line that strikes through once the runtime reports that step done. */
@Composable
private fun StepLine(index: String, text: StringResource, done: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (done) GreenOk else scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = scheme.onPrimary,
                )
            } else {
                Text(text = index, style = FlText.caption2, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            }
        }
        Text(
            text = stringResource(text),
            style = FlText.footnote,
            color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
        )
    }
}

/** Page four: the four things this fork is, where it lives, who it stands on, and who is welcome. */
@Composable
private fun AboutPage(platform: CantaPlatform, onBack: () -> Unit, onNext: () -> Unit) {
    OnboardingPage(
        pageIndex = PAGE_ABOUT,
        badge = { IconBadge(Icons.Default.Star) },
        title = Res.string.onboarding_about_title,
        subtitle = Res.string.onboarding_about_subtitle,
        negative = {
            OnboardingButton(
                onClick = onBack,
                primary = false,
                label = stringResource(Res.string.onboarding_back),
            )
        },
        positive = {
            OnboardingButton(
                onClick = onNext,
                primary = true,
                label = stringResource(Res.string.onboarding_next),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureTile(
                Icons.Default.Build,
                Res.string.onboarding_about_tile_root_title,
                Res.string.onboarding_about_tile_root_body,
            )
            FeatureTile(
                Icons.Default.Star,
                Res.string.onboarding_about_tile_chinese_title,
                Res.string.onboarding_about_tile_chinese_body,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureTile(
                Icons.Default.CheckCircle,
                Res.string.onboarding_about_tile_rules_title,
                Res.string.onboarding_about_tile_rules_body,
            )
            FeatureTile(
                Icons.Default.Favorite,
                Res.string.onboarding_about_tile_rescue_title,
                Res.string.onboarding_about_tile_rescue_body,
            )
        }
        Spacer(Modifier.height(14.dp))
        LinkCard(
            icon = Icons.Default.Share,
            title = Res.string.onboarding_about_repo_title,
            subtitle = REPO_URL.removePrefix("https://"),
            onClick = { platform.openUrl(REPO_URL) },
        )
        Spacer(Modifier.height(8.dp))
        LinkCard(
            icon = Icons.Default.Person,
            title = Res.string.onboarding_about_original_title,
            subtitle = stringResource(Res.string.onboarding_about_original_sub),
            onClick = { platform.openUrl(ORIGINAL_URL) },
        )
        Spacer(Modifier.height(14.dp))
        JoinCard(
            chips = listOf(
                stringResource(Res.string.onboarding_about_chip_issue),
                stringResource(Res.string.onboarding_about_chip_translation),
                stringResource(Res.string.onboarding_about_chip_rules),
                stringResource(Res.string.onboarding_about_chip_pr),
            ),
        )
    }
}

/** One of the four squares on page four: a tinted glyph, a two-word title, one line under it. */
@Composable
private fun RowScope.FeatureTile(icon: ImageVector, title: StringResource, body: StringResource) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(scheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = scheme.primary,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = stringResource(title),
            style = FlText.subheadline,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(body),
            style = FlText.caption1,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** A full-width tappable card: glyph, title, the thing it points at, and a chevron. */
@Composable
private fun LinkCard(icon: ImageVector, title: StringResource, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(scheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = FlText.subheadline,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = subtitle,
                style = FlText.caption1,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SettingsChevron()
    }
}

/** The closing card, in the primary colour: what the project wants from the people using it. */
@Composable
private fun JoinCard(chips: List<String>) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.primaryContainer)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_about_join_title),
            style = FlText.subheadline,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.onboarding_about_join_body),
            style = FlText.caption1,
            color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach {
                Chip(
                    text = it,
                    container = scheme.onPrimaryContainer.copy(alpha = 0.10f),
                    content = scheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** A small pill of text, the only rounded-to-7dp shape in the flow. */
@Composable
private fun Chip(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = FlText.caption1,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: StringResource,
    body: StringResource,
    vararg args: Any,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(body, *args),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

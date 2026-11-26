package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.profile_default_name
import io.github.samolego.canta.generated.resources.profile_edit_name
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.component.UserAvatar
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.IdentitySetup
import org.jetbrains.compose.resources.stringResource

/** 底栏画在这一页之上，最后一张卡片下面留出它的位置。 */
private val FloatingBarRoom = 96.dp

/**
 * "我的"页：这个安装自己的身份，以及只有本安装才知道的东西（我写过的评论）。
 *
 * 原来这一页叫"设置"，把开关和身份挤在一起；现在开关搬到 [SettingsScreen]，
 * 这一页只回答"我是谁、我写过什么、去哪里改设置"。
 *
 * 底栏那个齿轮仍进这一页 —— 用户说"把设置改为我的"，指的就是这个入口。
 */
@Composable
fun MineScreen(
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenMyComments: () -> Unit,
    /** 打开关于页：版本、更新与联系作者都在那边。 */
    onOpenAbout: () -> Unit = {},
    /** 打开个人主页：头像、名字、身份号都在那儿改；这一页的身份卡点一下就进去。 */
    onOpenProfile: () -> Unit = {},
    /** 自己设的头像直链；空串就是没设过（卡片画首字母圆，见 `UserAvatar`）。 */
    avatarUrl: String = "",
    /** 还没打开过的回复数；大于 0 时「我的方案」那行点一个红点，提醒去消息页看。 */
    unreadReplies: Int = 0,
    /** 有新版本：关于入口带同样的红点，和底栏那个一致。 */
    updateAvailable: Boolean = false,
) {
    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
    val identityUserId by settingsViewModel.identityUserId.collectAsStateWithLifecycle()
    // 名字和头像都设过没有：没设过就发不了言，这张卡要把缺口说清（见 `IdentitySetup`）。
    val setup by settingsViewModel.identitySetup.collectAsStateWithLifecycle()
    // 双击底栏「我的」那一格：这一页用的是普通竖向滚动，也跟着回顶部。
    val mineScroll = rememberScrollState()
    ScrollToTopEffect(mineScroll)
    // 关于页「作者的群」是否点过：没点过时，关于入口也跟着点一个。
    val qqGroupSeen by settingsViewModel.qqGroupSeen.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { ScreenTopBar(title = "我的") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(mineScroll)
                .padding(top = 8.dp, bottom = FloatingBarRoom),
        ) {
            // 身份在最上面：头像、名字，点一下进个人主页（改名与换头像都在那一页）。
            SettingsGroup {
                UserCard(
                    name = userName,
                    identityUserId = identityUserId,
                    avatarUrl = avatarUrl,
                    setup = setup,
                    onOpen = onOpenProfile,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 我上传过的方案：点进去是「我的方案」，一行一个，能删。
            //
            // 「我的评论」搬去了「消息 → 互动消息」——它和互动是同一个问题的两面，摆在一起才对；
            // 这一页剩下的只有「我上传过什么」，名字也就跟着改了。
            //
            // 这一行**不再挂红点**：新消息的去处是消息页，红点挂在「消息」那一栏上；挂在这里却
            // 点进「我的方案」，等于写了「N 条新消息」却把人带到没有消息的页。
            AppLinkCard(
                glyph = FlGlyphs.requests,
                title = "我的方案",
                subtitle = "我上传过的方案，点进去管理和删除",
                onClick = onOpenMyComments,
            )
            Spacer(Modifier.height(10.dp))

            // 开关都在那一页：外观、通用、权限、底栏、启动密码。
            AppLinkCard(
                glyph = FlGlyphs.settings,
                title = "设置",
                subtitle = "外观、通用、权限与底栏",
                onClick = onOpenSettings,
            )
            AppLinkCard(
                glyph = FlGlyphs.info,
                title = "关于",
                subtitle = "版本、检查更新与联系作者",
                onClick = onOpenAbout,
                // 两个理由任一成立就点一个：有新版本；或者还没点过作者的群（那一行里面带点）。
                showDot = updateAvailable || !qqGroupSeen,
            )
        }
    }
}

/**
 * Who the page belongs to: the avatar (the picture they set, or the first character of the name),
 * the name itself, and a tap that opens the profile page where both are edited.
 *
 * 改名与换头像都搬去了个人主页（`ProfileScreen`）：那一页要相册、要压缩、要跟图床说话，
 * 一套流程三处失败，塞在「我的」这张卡片里既点不开也说不清。这张卡片现在只是入口。
 */
@Composable
private fun UserCard(
    name: String,
    identityUserId: Long = 0L,
    avatarUrl: String = "",
    /** 名字与头像设过了没有；没设过就发言发不出去，卡片要把这一条说出来。 */
    setup: IdentitySetup = IdentitySetup.UNSET,
    onOpen: () -> Unit,
) {
    val defaultName = stringResource(Res.string.profile_default_name)
    // What this install is called when nobody picked a name: the number the backend gave it —
    // "#10134" — which is also what every other reader sees beside its plans and comments. It is
    // not a name, and showing it back is the point of the pulse below.
    val identityTag = if (identityUserId > 0L) "#$identityUserId" else ""
    val shown = name.trim().ifEmpty { identityTag.ifEmpty { defaultName } }
    val unnamed = isDefaultIdentityName(name) || (name.isBlank() && identityTag.isNotEmpty())
    // 还没设完（缺名字或缺头像）= 现在发不了言：卡片按同一种黄提示，不另起一套颜色。
    val incomplete = !setup.ready
    // The pulse: pale yellow fading in and out for as long as the name is a number. Slow on
    // purpose — a fast blink reads as an error to dismiss, while this is a request the page makes
    // until it is answered, and nothing but picking a name switches it off.
    val transition = rememberInfiniteTransition(label = "rename-pulse")
    val glow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rename-glow",
    )
    // Kept inside 0.10..0.20 alpha: enough to notice out of the corner of an eye, soft enough that
    // the row stays readable — a wall of colour would read as a warning, and this is a nudge.
    val highlight = if (unnamed || incomplete) 0.10f + 0.10f * glow else 0f
    val glowText = Color(0xFF8A6D00)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (unnamed || incomplete) Color(0xFFFFE96B).copy(alpha = highlight) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像 + 右下角那支小笔：这一页的头像本身就是「换头像」的入口（点进个人主页，在那儿裁剪、上传）。
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(name = shown, url = avatarUrl, size = 38.dp)
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "换头像",
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (unnamed || incomplete) glowText else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unnamed || incomplete) {
                    Spacer(Modifier.width(6.dp))
                    // The one place the page says why it keeps pulsing, instead of a dialog the
                    // reader would dismiss without reading.
                    Text(
                        text = when {
                            unnamed && incomplete -> "还没起名字、没换头像"
                            unnamed -> "还没起名字"
                            else -> "还没换头像"
                        },
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFE96B).copy(alpha = 0.45f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color(0xFF6B5400),
                    )
                }
            }
            Text(
                text = when {
                    unnamed -> {
                        // The number is quoted back, so the request is about something the reader can
                        // see on screen rather than an abstract "rename yourself".
                        "点这里起个名字，别拿 $shown 当名字"
                    }
                    incomplete -> setup.prompt.replaceFirst("先去「我的 → 个人主页」", "点这里")
                    else -> stringResource(Res.string.profile_edit_name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unnamed || incomplete) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (unnamed || incomplete) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Whether [name] is still the identity the backend handed out rather than a name somebody picked.
 *
 * Empty and "#10134" both mean "no name": the first is an install that never opened the dialog, the
 * second is one that saved the number it was shown. Nothing else counts — a user who calls
 * themselves "用户" made a choice, and telling them otherwise would be wrong.
 */
internal fun isDefaultIdentityName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return true
    return trimmed.length > 1 && trimmed.startsWith("#") && trimmed.drop(1).all { it.isDigit() }
}

// 改名对话框原来在这一页（`RenameDialog`），现在在 `ProfileScreen.kt` 里：名字和头像同一个地方改，
// 两处各一份会变成两套长度限制、两套提示文案。

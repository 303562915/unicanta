package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.profile_default_name
import io.github.samolego.canta.generated.resources.profile_edit_name
import io.github.samolego.canta.generated.resources.profile_name_hint
import io.github.samolego.canta.generated.resources.profile_name_title
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource

/** Longest nickname the dialog takes, so the avatar row never has to shrink it. */
private const val MAX_NAME_LENGTH = 24

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
    /** 还没打开过的回复数；大于 0 时"我的评论"那行点一个红点。 */
    unreadReplies: Int = 0,
    /** 有新版本：关于入口带同样的红点，和底栏那个一致。 */
    updateAvailable: Boolean = false,
) {
    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
    val identityUserId by settingsViewModel.identityUserId.collectAsStateWithLifecycle()
    // 关于页「作者的群」是否点过：没点过时，关于入口也跟着点一个。
    val qqGroupSeen by settingsViewModel.qqGroupSeen.collectAsStateWithLifecycle()
    var showRename by rememberSaveable { mutableStateOf(false) }

    if (showRename) {
        RenameDialog(
            current = userName,
            onDismiss = { showRename = false },
            onSave = { name ->
                settingsViewModel.saveUserName(name)
                showRename = false
            },
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "我的") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = FloatingBarRoom),
        ) {
            // 身份在最上面：头像、名字，点一下改名。
            SettingsGroup {
                UserCard(
                    name = userName,
                    identityUserId = identityUserId,
                    onEdit = { showRename = true },
                )
            }
            Spacer(Modifier.height(10.dp))

            // 我写过的：评论、方案，以及别人在下面的回复。
            AppLinkCard(
                glyph = FlGlyphs.requests,
                title = "我的评论",
                subtitle = if (unreadReplies > 0) {
                    "$unreadReplies 条新消息，点这里看"
                } else {
                    "我评论过的方案和应用，别人给我的回复"
                },
                onClick = onOpenMyComments,
                showDot = unreadReplies > 0,
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
 * Who the page belongs to: a letter avatar — the first character of the name, since the app has
 * no picture to show — the name itself, and a tap that renames it.
 */
@Composable
private fun UserCard(name: String, identityUserId: Long = 0L, onEdit: () -> Unit) {
    val defaultName = stringResource(Res.string.profile_default_name)
    // What this install is called when nobody picked a name: the number the backend gave it —
    // "#10134" — which is also what every other reader sees beside its plans and comments. It is
    // not a name, and showing it back is the point of the pulse below.
    val identityTag = if (identityUserId > 0L) "#$identityUserId" else ""
    val shown = name.trim().ifEmpty { identityTag.ifEmpty { defaultName } }
    val unnamed = isDefaultIdentityName(name) || (name.isBlank() && identityTag.isNotEmpty())
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
    val highlight = if (unnamed) 0.10f + 0.10f * glow else 0f
    val glowText = Color(0xFF8A6D00)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (unnamed) Color(0xFFFFE96B).copy(alpha = highlight) else Color.Transparent)
            .clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(name = shown, size = 38.dp)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (unnamed) glowText else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unnamed) {
                    Spacer(Modifier.width(6.dp))
                    // The one place the page says why it keeps pulsing, instead of a dialog the
                    // reader would dismiss without reading.
                    Text(
                        text = "还没起名字",
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
                text = if (unnamed) {
                    // The number is quoted back, so the request is about something the reader can
                    // see on screen rather than an abstract "rename yourself".
                    "点这里起个名字，别拿 $shown 当名字"
                } else {
                    stringResource(Res.string.profile_edit_name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unnamed) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (unnamed) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
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

/** The first character of [name] is drawn by [LetterAvatar]; this dialog only edits the text. */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    val save = { onSave(value.trim()) }
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.profile_name_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = save) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) value = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(Res.string.profile_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
}

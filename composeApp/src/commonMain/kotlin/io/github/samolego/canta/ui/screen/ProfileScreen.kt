package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawImageRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.ImageCrop
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.profile_default_name
import io.github.samolego.canta.generated.resources.profile_name_hint
import io.github.samolego.canta.generated.resources.profile_name_title
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.component.SettingsItem
import io.github.samolego.canta.ui.component.UserAvatar
import io.github.samolego.canta.ui.component.cacheAvatar
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/** 名字最长到这儿（和原来「我的」页那个对话框同一个数）：再长列表里那一行就该换行了。 */
private const val MAX_NAME_LENGTH = 24

/** 中间那个头像的直径。 */
private val AvatarSize = 96.dp

/** 头像右下角那支小笔。 */
private val BadgeSize = 26.dp

/** 上传中盖在头像上的那个转圈。 */
private val UploadSpinnerSize = 30.dp

/**
 * 「个人主页」：这个安装自己长什么样。
 *
 * 一页只回答三件事——**我叫什么**（改名）、**我长什么样**（换头像）、**我是几号**（身份号，只读）。
 * 方案数和评论数是可选的附加信息：拿不到（离线、后端没答）就不画那一组，不显示一半。
 *
 * 为什么单独一页而不是塞进「我的」：换头像要走相册、压缩、上传图床三步，每一步都可能失败，
 * 每一步都得说一句人话；「我的」那一页是入口清单，塞进这一套会把别的入口挤没。所以那一页的
 * 身份卡点一下进这里，改名与换头像都在这儿做。
 *
 * **这一页不认识 settings、也不认识平台层**：owner / 名字 / 头像直链 / 计数都由上层读好了传进来，
 * 相册与压缩是 [pickImage] / [compressImage] 两个函数。这样桌面和网页版（没有相册、没有解码器）
 * 用同一份页面，只是那两个函数回答 null，页面自己会说「换不了」。
 *
 * [repository] 只用来上传头像（`SocialRepository.uploadAvatar`），avatarId 与 overwrite 都在那边，
 * 这一层只管大小与压缩。传 null（比如测试或没有网络能力的宿主）时点换头像只会得到一句提示。
 */
@Composable
fun ProfileScreen(
    repository: SocialRepository? = null,
    /** 这个安装的身份号，纯数字；还没拿到（没登录过）就是空串。 */
    owner: String,
    userName: String,
    avatarUrl: String,
    /** 我上传过的方案数；null 表示"这一趟没拿到"，页面不画这一行。 */
    planCount: Int? = null,
    /** 我写过的评论数（第一页）；null 同上。 */
    commentCount: Int? = null,
    onSaveName: (String) -> Unit,
    onAvatarSaved: (String) -> Unit,
    pickImage: suspend () -> ByteArray?,
    /** 把一张图缩到预览尺寸（见 [ImageBed.AVATAR_PREVIEW_BYTES]）：裁剪框摆在这张缩图上，原图太大会 OOM。 */
    compressImage: (ByteArray) -> ByteArray?,
    /** 头像专用：按裁剪框裁成正方形、缩到头像尺寸，再压到 `20–200KB` 那一档（见 `ImageBed`）。 */
    compressAvatar: (ByteArray, ImageCrop?) -> ByteArray?,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(Res.string.profile_default_name)

    var uploading by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    // 换完头像要当场看见新的那张：上层把直链写进设置、再从 flow 传回来要绕一圈，
    // 这里先拿上传的结果画，等新值到了 remember(avatarUrl) 自然对齐。
    var shownAvatar by remember(avatarUrl) { mutableStateOf(avatarUrl) }
    // 换过头像就 +1：直链不变（覆盖式上传），但 `UserAvatar` 里记着的那张位图得重新读一次缓存，
    // 否则同一趟里换第二张时画出来的还是上一张。被 `key()` 用来重建那一个头像。
    var avatarVersion by remember { mutableStateOf(0) }
    // 已经选好、正等着摆裁剪框的那张图：原图字节（下刀用）与缩过的位图（摆框用）。
    var cropTarget by remember { mutableStateOf<CropTarget?>(null) }

    val identityTag = if (owner.isNotBlank()) "#${owner.trim()}" else ""
    val shownName = userName.trim().ifEmpty { identityTag.ifEmpty { defaultName } }
    // 「还没起名字」和「我的」页同一个判断：名字就是身份号时，那一行说的是号码不是名字。
    val unnamed = shownName == identityTag && identityTag.isNotEmpty()

    if (showRename) {
        RenameDialog(
            current = userName.trim(),
            onDismiss = { showRename = false },
            onSave = { name ->
                onSaveName(name)
                showRename = false
            },
        )
    }

    /**
     * 换头像：选 → 裁 → （压到 20–200KB）→ 传图床 → 记下直链。
     *
     * 和聊天发图不同的地方只有一处，就是那个裁剪框：头像最后画在一个圆里，把整张照片塞进去必然被
     * 裁得莫名其妙；让人自己挪一下、缩一下，比事后抱怨"怎么把我的头切掉了"要好。
     *
     * 规矩照旧：选图返回 null 是**取消**，静默退出；图读不出来、压不下来就说一句实话，不把原图硬塞
     * 上去；上传失败按 [SocialRepository.lastError] 说原因，没有原因就是"检查代理"。
     *
     * 解码、缩放、裁剪、编码都是同步的重活，全部挪到 [Dispatchers.Default]：
     * 在主线程调会卡住界面几百毫秒，人只会看见"点了没反应"。
     */
    val changeAvatar: () -> Unit = {
        if (!uploading) {
            if (owner.isBlank()) {
                // 没有身份号就没有对象名：头像是 `unicanta/头像/<owner>.jpg`，这时候上传只会
                // 落到一个空名字上，下次谁也找不回来。
                hint = "身份号还没拿到，连上网络再试一次"
            } else {
                scope.launch {
                    uploading = true
                    hint = null
                    try {
                        val picked = pickImage()
                        if (picked != null) {
                            // 上层已经把这个 lambda 绑到预览上限（长边 ≤2048、≤400KB），这里只传字节。
                            val preview = withContext(Dispatchers.Default) { compressImage(picked) }
                            val bitmap = preview?.let { bytes ->
                                runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                            }
                            if (bitmap == null) {
                                // 预览都压不出来：要么字节不是图，要么大到连 400KB 都收不住。
                                hint = "这张图太大或者读不出来，换一张"
                            } else {
                                // 交给裁剪框；上传在它按「确定」之后才开始。
                                cropTarget = CropTarget(source = picked, preview = bitmap)
                            }
                        }
                    } finally {
                        // 选图被取消（页面走了、相册被杀）也要放开闸门，否则这个钮再也点不动。
                        uploading = false
                    }
                }
            }
        }
    }

    /**
     * 裁完之后真正落地的那一半：压 + 传 + 记直链。
     *
     * 单独拎出来是因为它要等对话框的「确定」：那一步之后 [uploading] 才再次点亮，人在裁剪期间不会
     * 看见头像转圈。
     */
    fun uploadCropped(source: ByteArray, crop: ImageCrop) {
        scope.launch {
            uploading = true
            hint = null
            try {
                val ready = withContext(Dispatchers.Default) { compressAvatar(source, crop) }
                if (ready == null || ready.size > ImageBed.AVATAR_MAX_BYTES) {
                    hint = "这张图压不下来，换一张"
                } else {
                    val repo = repository
                    val url = repo?.uploadAvatar(owner.trim(), ready)
                    if (url == null) {
                        hint = repo?.lastError?.takeIf { it.isNotBlank() }
                            ?: "头像没上传成，检查代理后再试"
                    } else {
                        // 头像是覆盖式上传，直链不会变：把刚上传的字节解出来盖掉缓存里那一格，
                        // 否则页面会立刻画出缓存中的旧头像，看起来像没换成功。
                        runCatching { ready.decodeToImageBitmap() }.getOrNull()
                            ?.let { bitmap -> cacheAvatar(url, bitmap) }
                        shownAvatar = url
                        avatarVersion += 1
                        onAvatarSaved(url)
                    }
                }
            } finally {
                uploading = false
            }
        }
    }

    // 裁剪框：确定之后才真正压图、上传（见 [uploadCropped]）。
    cropTarget?.let { target ->
        AvatarCropDialog(
            bitmap = target.preview,
            onDismiss = { cropTarget = null },
            onConfirm = { crop ->
                cropTarget = null
                uploadCropped(target.source, crop)
            },
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(onNavigateBack = onNavigateBack, title = "个人主页") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // 头像在最上面，点右下角那支笔换一张。
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AvatarWithBadge(
                    name = shownName,
                    url = shownAvatar,
                    version = avatarVersion,
                    uploading = uploading,
                    onChangeAvatar = changeAvatar,
                )
            }

            // 一行实话：压不下来、传不上去、没身份号，都在这里说，说完不再堆第二句。
            val line = hint
            if (line != null) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            SettingsGroup {
                SettingsItem(
                    title = "名字",
                    description = when {
                        unnamed -> "点这里起个名字，别拿 $shownName 当名字"
                        else -> "别人看到的署名，随时能改"
                    },
                    onClick = { showRename = true },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = shownName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            SettingsChevron()
                        }
                    },
                )
                SettingsItem(
                    title = "身份号",
                    description = "后端给的号码，改不了；别人用它找到我",
                    divider = false,
                    trailing = {
                        Text(
                            text = identityTag.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // 这两行只在拿到数字时出现：拿不到就整组不画，不摆两个"—"。
            if (planCount != null || commentCount != null) {
                Spacer(Modifier.height(12.dp))
                SettingsGroup(title = "我留下的") {
                    if (planCount != null) {
                        SettingsItem(
                            title = "方案",
                            description = "我上传过的方案",
                            divider = commentCount != null,
                            trailing = { CountText(planCount) },
                        )
                    }
                    if (commentCount != null) {
                        SettingsItem(
                            title = "评论",
                            description = "我在别人的方案和应用下写过的",
                            divider = false,
                            trailing = { CountText(commentCount) },
                        )
                    }
                }
            }
        }
    }
}

/** 一个计数：数字大一点，单位在描述里，所以这里只有数字。 */
@Composable
private fun CountText(value: Int) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 大头像 + 右下角那支笔。
 *
 * 上传中：头像半透明、上面盖一个转圈、笔收起来（不收起来就是一个能连点的钮）。
 * 其余时间笔一直在——它是这一页唯一的"换"入口，藏起来就等于没有。
 *
 * [version] 只当 `key()` 用：直链不变时（覆盖式上传）靠它把 [UserAvatar] 重建成"再读一次缓存"。
 */
@Composable
private fun AvatarWithBadge(
    name: String,
    url: String,
    version: Int,
    uploading: Boolean,
    onChangeAvatar: () -> Unit,
) {
    Box(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .alpha(if (uploading) 0.4f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            key(version) {
                UserAvatar(name = name, url = url, size = AvatarSize)
            }
            if (uploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(UploadSpinnerSize),
                    strokeWidth = 2.5.dp,
                )
            }
        }
        if (!uploading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onChangeAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "换头像",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * 改名：和「我的」页原来那个一样——一个输入框、取消/确定，长度卡在 [MAX_NAME_LENGTH]。
 *
 * 保存交给上层（`SettingsViewModel.saveUserName`），这一页只负责输入。
 */
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

/** 选好、等着摆裁剪框的那张图：原图字节（真正下刀用的）与缩过的预览位图（摆框用的）。 */
private class CropTarget(val source: ByteArray, val preview: ImageBitmap)

/**
 * 头像裁剪框：圆形的取景窗，图可以拖、可以缩，按「确定」把取景窗里的那一块交回去。
 *
 * 为什么是圆形窗：头像最终画在圆里，方形窗会让人把眼睛摆在角上，裁完才发现被切掉。圆形窗所见即所得。
 *
 * 画法是"显式给源矩形"，不是把整张图缩放后平移：`Canvas` 里用 `drawImageRect` 把源图上的一块原样
 * 画进取景窗。屏幕上看到的那一块和交出去的 [ImageCrop] 由**同一组数**算出来，所以不存在"看到的和
 * 裁下来的不是同一个地方"；夹取也变成硬边界——源矩形永远落在图内，圆窗里不会露黑边。
 *
 * 交出去的 [ImageCrop] 是**归一化**的（0..1，按预览图的宽高）：真正下刀的是原图，两张图的像素尺寸
 * 不一样，共用的只有比例（见 `core/ImageCrop`）。
 *
 * 交互只有两种：拖 = 挪位置，滑杆 = 缩放（1×–4×）。没做双指缩放：不是这页必须的手势，而滑杆在
 * 小屏上更容易点准。
 */
@Composable
private fun AvatarCropDialog(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
    onConfirm: (ImageCrop) -> Unit,
) {
    // 取景窗的边长写死：跟着屏幕缩放会让"拖多远"的手感在不同机器上来回变。
    val viewport = 260.dp
    var zoom by remember { mutableStateOf(1f) }
    // 取景窗中心相对图中心的位移，单位是**源图像素**：这样改缩放时不用重算、也不会漂。
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    val viewportPx = with(LocalDensity.current) { viewport.toPx() }
    val imageWidth = bitmap.width.toFloat().coerceAtLeast(1f)
    val imageHeight = bitmap.height.toFloat().coerceAtLeast(1f)
    // 1× 时取景窗里那一块的源图边长：取短边，所以 1× 正好是"整图居中裁方"，和旧行为一致。
    val baseSide = minOf(imageWidth, imageHeight)
    // 当前缩放下取景窗对应的源图边长。
    val sourceSide = baseSide / zoom
    val limitX = ((imageWidth - sourceSide) / 2f).coerceAtLeast(0f)
    val limitY = ((imageHeight - sourceSide) / 2f).coerceAtLeast(0f)
    val centerX = imageWidth / 2f + panX.coerceIn(-limitX, limitX)
    val centerY = imageHeight / 2f + panY.coerceIn(-limitY, limitY)
    // 源矩形左上角。夹一次是为了让四舍五入之后仍然整块落在图内（越界会让 drawImageRect 画不出来）。
    val sourceLeft = (centerX - sourceSide / 2f).coerceIn(0f, imageWidth - sourceSide)
    val sourceTop = (centerY - sourceSide / 2f).coerceIn(0f, imageHeight - sourceSide)

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "调整头像",
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = {
                    // 和屏幕上完全同一个算法：中心 ± 半个边长，再归一化。
                    val cx = sourceLeft + sourceSide / 2f
                    val cy = sourceTop + sourceSide / 2f
                    onConfirm(
                        ImageCrop(
                            left = ((cx - sourceSide / 2f) / imageWidth).coerceIn(0f, 1f),
                            top = ((cy - sourceSide / 2f) / imageHeight).coerceIn(0f, 1f),
                            width = (sourceSide / imageWidth).coerceIn(0.02f, 1f),
                            height = (sourceSide / imageHeight).coerceIn(0.02f, 1f),
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .size(viewport)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // 屏幕位移换算成源图像素：取景窗一整个宽 = 当前 sourceSide 个源像素。
                            // zoom / pan 都是 Compose 状态，这里读到的是**此刻**的值，所以放大之后再拖
                            // 依然是跟手的（不会拿着 1× 时的夹取范围去算）。
                            val side = baseSide / zoom
                            val maxX = ((imageWidth - side) / 2f).coerceAtLeast(0f)
                            val maxY = ((imageHeight - side) / 2f).coerceAtLeast(0f)
                            val perPixel = side / viewportPx
                            panX = (panX + drag.x * perPixel).coerceIn(-maxX, maxX)
                            panY = (panY + drag.y * perPixel).coerceIn(-maxY, maxY)
                        }
                    },
            ) {
                drawImageRect(
                    image = bitmap,
                    srcOffset = IntOffset(sourceLeft.roundToInt(), sourceTop.roundToInt()),
                    srcSize = IntSize(sourceSide.roundToInt(), sourceSide.roundToInt()),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "拖动图片调整位置，滑杆缩放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Slider(
                value = zoom,
                onValueChange = { zoom = it },
                valueRange = 1f..4f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 卡片宽度占窗口宽度的比例：再窄一点「只有 #1 到 200」这行红字就要折行。 */
private const val CARD_WIDTH_FRACTION = 0.8f

/** 卡片宽度上限：平板上 0.8 的比例会把这张只有两行字的卡片拉成一条横幅，所以封顶 320dp。 */
private val CARD_MAX_WIDTH = 320.dp

/** 卡片圆角：比 `CantaDialog` 的 `shapes.extraLarge`（28dp）收一档 —— 这张卡片矮，28dp 会显得两头圆过中间。 */
private val CARD_CORNER = 20.dp

/** 卡片内边距：与 `CantaDialog` 的横向 22dp / 纵向 20dp 取同一个纵向数，四周统一 20dp。 */
private val CARD_PADDING = 20.dp

/** 输入框圆角：卡片是 20dp，里面这一格收到 12dp，看起来是「卡片里的一块」，不是第二张卡片。 */
private val FIELD_CORNER = 12.dp

/** 楼层号最多几位：帖子回帖上限 200 层（`PostThread` 的约定），四位数足够，也挡住粘一大串进来。 */
private const val FLOOR_DIGITS_MAX = 4

/** 输入框还没输入时的示例。写成「如 12」而不是「楼层号」：label 已经写了楼层号，占位再写一遍是重复。 */
private const val PLACEHOLDER = "如 12"

/** 输入框的标签（也是 TalkBack 读这个框时说的名字）。 */
private const val FIELD_LABEL = "楼层号"

/**
 * 文字按钮的最小高度：与 M3 `ButtonDefaults.MinHeight`（40dp）同数，改成自绘之后触控高度不掉档。
 */
private val ACTION_MIN_HEIGHT = 40.dp

/** 文字按钮的横向内边距与圆角：M3 `TextButton` 的默认内边距是 12dp 横向，圆角沿用状态层的 10dp。 */
private val ACTION_PADDING_HORIZONTAL = 12.dp
private val ACTION_CORNER = 10.dp

/**
 * 帖子详情页的**「跳到第几楼」输入对话框**：打一个楼层号，回车或点「跳过去」，落到那一层。
 *
 * ## 它干什么
 *
 * 目录面板（`PostTocSheet`）与搜索面板（`PostSearchSheet`）都是「在列表里认出目标」，用途是浏览；
 * 但人常常已经知道层号（引用指示条上写着 `#37`、别人说「看 42 楼」），这时翻列表反而是绕路。
 * 这个对话框只做一件事：收一个 1 基楼层号，交给 [onJump]。
 *
 * ## 为什么自绘卡片，不用 `AlertDialog`
 *
 * 1. 本仓的对话框本来就是自绘的：`CantaDialog` 是 `BasicAlertDialog` +
 *    `Surface(shape = shapes.extraLarge)` + 右对齐的一排 `TextButton`，风格靠这几处数值站住。
 *    换成平台 `AlertDialog` 会直接带回它自己的标题/正文/按钮排版，与 `CantaDialog` 并排放着就是两套。
 * 2. 宽度是需求的一部分（0.8 比例、320dp 封顶）。`AlertDialog` 的宽度由平台 décor 定死，
 *    只有在 `DialogProperties(usePlatformDefaultWidth = false)` 下才谈得上自定，而那条路必须自己画卡片
 *    —— 参考 `CantaDialog` 的同一个开关（`usePlatformDefaultWidth = widthFraction == null`）。
 * 3. 平台 `AlertDialog` 会把两枚按钮铺到对话框最右与最左，而这里「取消 / 跳过去」要紧挨着排在右下
 *    （照 `FloorReportSheet` 的 `Arrangement.End` 那一排），离输入框近，手指不用横跨整条。
 *
 * ## 为什么楼层号是 1 基，`#1` 是主楼
 *
 * 楼层号的语义在 `PostsSection.kt` 的 `floorLabelOf` 定死：主楼 = `#1`，第 i 条回复 = `#(i+2)`
 * （`comments` 里**不含**主楼，见 `PostThread`）。目录面板、引用指示条、「举报 #N」用的都是这一套，
 * 所以这个输入框收的也是同一套：人从界面上抄下来的数字能直接打进去，不需要自己 +1。
 * 因此合法区间恒为 `1..maxFloor`，且 `maxFloor` = 回帖数 + 1（由调用点算好传进来，这里不猜）。
 *
 * ## 键盘
 *
 * `imePadding()` 垫在居中容器上：键盘弹起时可用高度变矮，卡片跟着上移，输入框不会被盖住。
 * 垫在容器而不是卡片上，是因为卡片自己高度是 wrap-content —— 垫在它身上只会把卡片内容压扁，
 * 位置却不动。
 *
 * 关面板收键盘（`focusManager.clearFocus()`）放在**每一个出口**：遮罩 / 返回键 / 「取消」/「跳过去」。
 * 漏一条就是「面板没了，键盘还杵在那儿挡住正文」（同一条纪律见 `PostSearchSheet.kt` 的 `close`）。
 *
 * @param maxFloor 这条帖子现在总共有几层（含主楼 #1），合法输入区间是 `1..maxFloor`。
 * @param onJump 确认时交出楼层号（1 基、含主楼）；**先交出去再关**，与 `PostsSection.kt` 里举报面板
 *   （`onSubmit` 先把 `reportTarget` 置空、再调 `onReportFloor`）同一个顺序：调用方那边一旦慢半拍，
 *   人看到的就是「点了跳过去、面板还盖在上面」。
 * @param onDismiss 收起对话框；键盘由这里自己先收，调用方只管状态。
 */
@Composable
internal fun PostJumpDialog(
    maxFloor: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    // 输入法的持有处：清焦点不一定收得干净（尤其面板整个被移出组合时），所以开关都显式说一遍，
    // 与 `ChatScreen` 的 `dismissKeyboard()` 同一个写法。
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    // 只存输入框里的**数字串**，不存 Int：空的 "" 与 "0" 是两种不同状态（前者不报错、后者要报错），
    // 用 Int 存会把这个区别吃掉（"" 解析成什么都不是，得有第三个值来表示）。
    var typed by remember { mutableStateOf("") }

    // 上界至少取 1：只有主楼的帖子传 1 进来，理论上不会传更小的值，但真传了 0 也不会让
    // `1..0` 变成一个「看起来能输、其实永远不合法」的空区间以外的怪东西。
    val upper = maxFloor.coerceAtLeast(1)
    // 合法楼层号；null = 现在不能跳。三件事一次算完（空串 / 0 / 超上界都落在这里），
    // 按钮可用性、错误行、无障碍标签读的都是这一个值，不会出现「按钮灰着但没报错」这种自相矛盾。
    val floor: Int? = typed.toIntOrNull()?.takeIf { it in 1..upper }
    // 红字只在「输了东西但不合法」时出：空框是还没开始输，不该先挨一句骂。
    val showError = typed.isNotEmpty() && floor == null

    /** 关闭的唯一出口：先收键盘，再交给调用方。 */
    val close: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    /**
     * 确认的唯一出口：键盘上的「前往」（[ImeAction.Go]）与「跳过去」都走这里。
     *
     * 顺序是「收键盘 → 交出楼层号 → 关面板」：`onJump` 之后调用方可能立刻滚动列表，
     * 键盘这时还立着会遮掉刚滚到的那一层；而 `onDismiss` 放最后，是为了让 [onJump] 看到的面板
     * 还在（同 `PostsSection.kt` 举报面板「先交出去再关」的约定）。
     */
    val confirm: () -> Unit = {
        val target = typed.toIntOrNull()
        if (target != null && target in 1..upper) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onJump(target)
            onDismiss()
        }
    }

    // 自动聚焦：先把焦点要过来，键盘自己会跟着弹（`OutlinedTextField` 拿到焦点即请求 IME）。
    //
    // **要等一帧**：对话框是「先组合、后布局」，`FocusRequester` 在 modifier 还没挂上时调
    // `requestFocus()` 会抛 `IllegalStateException: FocusRequester is not initialized`。
    // `withFrameNanos { }` 就是等下一帧，本仓已有两处同样的先例（`PostSearchSheet.kt` 的自动聚焦、
    // `ChatScreen.kt` 的首屏落底）。
    //
    // 焦点拿到之后再显式 `show()` 一次：对话框窗口是新建的，首帧那一下「拿到焦点 → 请求 IME」
    // 有小概率被窗口 Attach 的时序吞掉，那时人看到一个空的输入框却没有键盘，还得再点一下。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = close,
        // 只关掉平台的默认窄边；`decorFitsSystemWindows` 是 Android 版独有的参数，
        // Compose Multiplatform 这份没有，写了必编译不过（`PostImage.kt` 记过同一处）。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 键盘垫在**居中容器**上：可用高度矮下去，卡片跟着往上走（见文件头「键盘」）。
                .imePadding(),
            // 居中而不是贴底：这是一个「要一段数字」的小对话框，不是内容面板；
            // 贴底会让人以为下面还有列表可以拖。
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(CARD_CORNER),
                // `surfaceContainerHigh`：比页面（`surface`）高一档，卡片浮起来一层的观感与
                // `CantaDialog` 的 `FlSurfaceLevel.Block` 同一档亮度。
                color = scheme.surfaceContainerHigh,
                modifier = Modifier
                    // 先按比例铺开，再封顶：顺序反了（先 `widthIn` 后 `fillMaxWidth`）就变成
                    // 「320dp 的 0.8」= 256dp，窄屏上会缩成一条。
                    .fillMaxWidth(CARD_WIDTH_FRACTION)
                    .widthIn(max = CARD_MAX_WIDTH),
            ) {
                Column(modifier = Modifier.padding(CARD_PADDING)) {
                    // 标题行：标题在前、层数在后，层级用字号分（`titleMedium` vs `labelSmall`），
                    // 不靠颜色以外的第二套排版。
                    Text(
                        text = "跳到第几楼",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "共 $upper 层",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = typed,
                        onValueChange = { next ->
                            // 只认 ASCII 数字：`Char.isDigit` 会放过全角「１２」，那种字符
                            // `toIntOrNull()` 解析不出来，界面就会出现「看着全是数字、却报范围错」。
                            // 四位封顶，粘一串东西进来也只留前四位。
                            typed = next.filter { it in '0'..'9' }.take(FLOOR_DIGITS_MAX)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        // `isError` 同时负责边框变色与（配合下面的 `supportingText`）错误行的样式上下文。
                        isError = showError,
                        // label 就是这个框的无障碍名字（TalkBack 读「楼层号，编辑框」）。
                        // **不**再额外挂 `contentDescription`：`semantics` 里的 contentDescription 会
                        // 盖掉输入框自己读出来的值，人听不到自己刚敲的数字。
                        label = {
                            Text(text = FIELD_LABEL, style = MaterialTheme.typography.labelSmall)
                        },
                        placeholder = {
                            Text(text = PLACEHOLDER, style = MaterialTheme.typography.bodyMedium)
                        },
                        shape = RoundedCornerShape(FIELD_CORNER),
                        keyboardOptions = KeyboardOptions(
                            // 数字键盘：这一格只可能收到数字，弹字母键盘等于让人先去按一下「123」。
                            keyboardType = KeyboardType.Number,
                            // 「前往」而不是「完成」：回车在这里是**执行**（跳过去），不是收起键盘。
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { confirm() }),
                        // 错误行挂在 `supportingText` 上而不是在下面另起一行 `Text`：它跟着输入框走，
                        // 出现时把按钮那一排整体往下推一次，不会压在输入框的圆角上。
                        // 这个 `if … { { … } } else null` 的写法是本仓既定形态（`UploadPlanDialog`
                        // 的 `trailingIcon` 就是它），lambda 的可组合性由期望类型给。
                        supportingText = if (showError) {
                            { FloorRangeError(upper) }
                        } else {
                            null
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 按钮排右下角：`Arrangement.End` + 6dp 间距，与 `CantaDialog` 的按钮行同一个排法
                    // （「取消」在前、「跳过去」在后：退出的动作放左边，手指落点偏右不至于误触确认）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardTextAction(
                            label = "取消",
                            onClickLabel = "关闭「跳到第几楼」",
                            enabled = true,
                            color = scheme.onSurfaceVariant,
                            onClick = close,
                        )
                        CardTextAction(
                            label = "跳过去",
                            // 无障碍的动作名把结果说出来：读屏用户听到的是「跳到第 12 楼，按钮」，
                            // 而不是一句没有宾语的动作。
                            onClickLabel = if (floor != null) "跳到第 $floor 楼" else "跳过去",
                            // 空 / 0 / 超上界都点不动（见上面 `floor` 的注释）。
                            enabled = floor != null,
                            color = if (floor != null) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.38f),
                            onClick = confirm,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 非法楼层号那一行红字：「只有 #1 到 #N」。
 *
 * 为什么把上界写进文案里：`#N` 是这个帖子此刻的真实层数，人不用回头去数「这帖到底几层」。
 * 用 `error` 色而不是 `warning`：这是「这个数字收下了」的前置条件没满足，属于拦下来的错误，
 * 不是一句提醒。
 */
@Composable
private fun FloorRangeError(upper: Int) {
    Text(
        text = "只有 #1 到 #$upper",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * 卡片右下角的一枚文字按钮：自绘，不用 M3 的 `TextButton`。
 *
 * 唯一的理由是 `onClickLabel`：`TextButton` / `Button` 的签名里没有这个参数，而 M3 按钮内部的
 * `clickable` 又不让从外面补标签（能补的只有 `contentDescription`，那会把按钮文字也一起吃掉）。
 * 自绘一层 `clickable(role = Role.Button, onClickLabel = …)` 是本仓已有形态
 * （`PostTocSheet` 的行、`FloorReportSheet` 的关闭钮都是这么写的），无障碍语义一次给全。
 *
 * 数值照 M3 `TextButton`：最小高度 40dp、横向内边距 12dp、圆角 10dp；文字用 `labelLarge`。
 * 禁用态不是 `enabled = false` 之后干等：颜色由调用方按可用性给（`onSurfaceVariant` α0.38，
 * 与 M3 禁用文字同一档），这样灰下去的那一刻字色渐变不会缺失。
 *
 * @param onClickLabel 无障碍动作名（`clickable` 的 `onClickLabel`）。
 * @param color 文字颜色；禁用时由调用方传灰。
 */
@Composable
private fun CardTextAction(
    label: String,
    onClickLabel: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            // `clip` 在 `clickable` **之前**：水波纹跟着 10dp 圆角走，不然点下去是一块方角高亮。
            .clip(RoundedCornerShape(ACTION_CORNER))
            .clickable(
                role = Role.Button,
                onClickLabel = onClickLabel,
                enabled = enabled,
                onClick = onClick,
            )
            .heightIn(min = ACTION_MIN_HEIGHT)
            .padding(horizontal = ACTION_PADDING_HORIZONTAL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

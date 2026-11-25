package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文字头像：名字的第一个字符，画在一个实心圆（或方形，见 [shape]）里。
 *
 * 这是全 app 头像的**默认样子**：没上传过照片的人一律显示它。以前这里有一段"名字还是装机码就
 * 换成一整块灰骨架"的短路，结果是没改名的人头像全变灰块、**一个字都看不到**——现在没有那条路了，
 * 文字永远显示，是否改过名与头像无关。
 *
 * 那个字由 [avatarLetter] 取：[name] 的首个字符，`"#10000"` 与 `"Alice"` 都得到一个字符的头像。
 */
@Composable
fun LetterAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = MaterialTheme.colorScheme.primary,
    /** The letter's colour: white on the comment avatars, which are picked per person. */
    letterColor: Color = MaterialTheme.colorScheme.onPrimary,
    /**
     * 那个字的字号：默认跟着 `labelLarge`（小的行内头像够用）。大圆（比如个人主页那个 96）
     * 传一个更大的样式，否则字会缩在圆的中间一小块。
     */
    letterStyle: TextStyle? = null,
    /** 外形：默认圆形；群头像九宫格里传方形（见 [GroupGridAvatar]）。 */
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarLetter(name),
            style = letterStyle ?: MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = letterColor,
        )
    }
}

/**
 * The first character of [name], kept whole when it is a surrogate pair; "?" when there is none.
 *
 * A leading sign is skipped: an install that never picked a name is signed "#10000", and the
 * avatar of that review should read "1", not "#".
 */
fun avatarLetter(name: String): String {
    val trimmed = name.trim()
    var index = 0
    while (index < trimmed.length) {
        val char = trimmed[index]
        // An emoji is two UTF-16 units; take both rather than half a character.
        if (char.isHighSurrogate() && index + 1 < trimmed.length) return trimmed.substring(index, index + 2)
        if (char.isLetterOrDigit()) return char.toString()
        index++
    }
    return "?"
}

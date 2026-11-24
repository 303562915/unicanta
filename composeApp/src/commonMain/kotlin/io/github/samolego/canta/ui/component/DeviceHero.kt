package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plans_hero_source
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/** How tall the picture of the phone is at the top of the safety page. */
private val HERO_HEIGHT = 132.dp

/** Where the picture comes from. Wikimedia Commons is the one public source that can be searched
 *  by model name and hot-linked without a key, so the app ships no per-model image list. */
private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"

/** Wikimedia wants a user agent that says what is asking. */
private const val USER_AGENT = "Canta/4.2 (device hero; https://github.com/303562915/unicanta)"

/** Wide enough for a phone screen at 2x, small enough that the first load is quick. */
private const val THUMB_WIDTH = 960

/**
 * Finds and downloads one picture of a phone, remembered for the life of the process: the hero
 * asks once per model and shows the same bitmap from then on.
 *
 * Nothing here is required for the page to work — a model with no picture (or no network) simply
 * gets the grey fallback behind the model name.
 */
private object DeviceImages {
    private val client = HttpClient { install(HttpTimeout) { requestTimeoutMillis = 9_000 } }
    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** The picture for [model], or null when there is none. */
    suspend fun load(model: String): ImageBitmap? {
        val query = model.trim()
        if (query.isEmpty()) return null
        if (cache.containsKey(query)) return cache[query]
        val bitmap = withTimeoutOrNull(12_000) { fetch(query) }
        cache[query] = bitmap
        return bitmap
    }

    /** The best Commons match for the model name may only be shown when the name is in the title. */
    private suspend fun fetch(query: String): ImageBitmap? = runCatching {
        val text = client.get(COMMONS_API) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("action", "query")
            parameter("generator", "search")
            parameter("gsrsearch", "filetype:bitmap $query")
            parameter("gsrnamespace", "6")
            parameter("gsrlimit", "10")
            parameter("prop", "imageinfo")
            parameter("iiprop", "url")
            parameter("iiurlwidth", THUMB_WIDTH.toString())
            parameter("format", "json")
        }.takeIf { it.status.isSuccess() }?.bodyAsText() ?: return null

        val pages = Json.parseToJsonElement(text).jsonObject["query"]?.jsonObject
            ?.get("pages")?.jsonObject ?: return null
        val tokens = tokensOf(query)

        // Only matches whose file name carries the model name are used: a Commons search for a
        // model also returns unrelated photos, and a wrong phone in the hero would be worse than
        // no phone at all.
        val ranked = pages.values.map { it.jsonObject }.mapNotNull { page ->
            val title = page["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val info = (page["imageinfo"] as? JsonArray)?.firstOrNull()
            val url = info?.jsonObject?.get("thumburl")?.jsonPrimitive?.contentOrNull
                ?: info?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            if (url.isNullOrEmpty()) return@mapNotNull null
            Triple(tokens.count { title.contains(it, ignoreCase = true) }, title, url)
        }.filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, String, String>> { it.first }.thenBy { it.second })

        val url = ranked.firstOrNull()?.third ?: return null
        val bytes = client.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body<ByteArray>()
        bytes.decodeToImageBitmap()
    }.getOrNull()

    private fun tokensOf(query: String): List<String> =
        query.split(' ', '-', '_', '·', '/', '+')
            .map { it.trim() }
            .filter { it.length >= 2 }
}

/**
 * The top of the safety page: the phone this page is about, named and counted on a plain band, the
 * page switch ([footer]) right under it, and the picture of the phone below the switch.
 *
 * The picture is decoration and nothing is written on it, so a missing one keeps the layout — the
 * same band of height [HERO_HEIGHT] in muted grey — and the page still reads the same: the switch
 * never moves, whichever way the picture turned out. [showPhoto] turns the band off altogether, for
 * a phone that would rather have the list start higher up: nothing is looked up, decoded or drawn.
 */
@Composable
fun DeviceHero(
    model: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    counters: List<Pair<String, String>> = emptyList(),
    /**
     * 计数器前面那一下：安全页把「上传本机方案」放在这里，紧挨着「重启验证」的数字——
     * 上传的入口就长在这组数字旁边，而不是要先切到本机才看得见。
     */
    countersLeading: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    /** False hides the picture and keeps its band: the heading and the switch do not move. */
    showPhoto: Boolean = true,
    /**
     * Opens whatever switches the phone this page is about. The name becomes the button: it is the
     * thing the reader is looking at, and a chevron next to it says there is more than one.
     */
    onTitleClick: (() -> Unit)? = null,
    /**
     * The capsule breathes until it has been tapped once: it is the only way to another phone, and
     * a still capsule reads as a label rather than a control. Off after the first tap — a hint that
     * keeps going is a nag — and the caller owns that flag, so it survives leaving the page.
     */
    pulseTitleHint: Boolean = false,
) {
    var image by remember(model) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(model, showPhoto) {
        // Nothing is loaded when the picture is off, so a phone that turned it off pays neither the
        // lookup nor the decode for a picture it will not see.
        image = if (showPhoto) DeviceImages.load(model) else null
    }

    val scheme = MaterialTheme.colorScheme
    val onBand = scheme.onSurface
    val onPicture = Color.White

    // The breathing capsule. Kept to alpha on the capsule the reader already sees: no halo, no
    // expanding ring, nothing that grows past the model name's own outline — a pulse that spills
    // over the band reads as a warning about the phone, which this is not. 2.4s a cycle, slow
    // enough to catch the eye without looking like a loading spinner.
    val hintTransition = rememberInfiniteTransition(label = "scope-hint")
    val hintGlow by hintTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scope-hint-glow",
    )
    val hint = if (pulseTitleHint) hintGlow else 0f
    val capsuleBackground = scheme.primary.copy(alpha = 0.10f + 0.09f * hint)
    val capsuleBorder = scheme.primary.copy(alpha = 0.35f + 0.31f * hint)
    val capsuleAccent = scheme.primary.copy(alpha = 0.72f + 0.28f * hint)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(
                // A colourless gradient: the hero is a backdrop, not a badge.
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHigh,
                        scheme.surfaceContainerHighest,
                    )
                )
            ),
    ) {
        // What the page knows about the phone, above the switch and off the picture.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                // No rounded clip on the text column: a clip here used to cut the last glyph of
                // the system line as it reached the column's edge.
                modifier = Modifier.weight(1f),
            ) {
                if (onTitleClick != null) {
                    // The name is the way to another phone, and now it looks like one: a capsule
                    // drawn around the name, with the word "切换" and a chevron in it. The tap
                    // target is that capsule and nothing else — the system line under it is text
                    // to read, and a reader aiming at the name used to switch the phone by
                    // brushing the line below it.
                    //
                    // The hint is two characters, not four: the capsule shares the band with the
                    // counters, and every dp the hint takes is a dp the model name loses — a long
                    // name ("Galaxy Z Fold8") would be the first thing to go under an ellipsis.
                    Row(
                        modifier = Modifier
                            .heightIn(min = 30.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(capsuleBackground)
                            .border(1.dp, capsuleBorder, RoundedCornerShape(percent = 50))
                            .clickable(onClick = onTitleClick)
                            .padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = onBand,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "切换",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = capsuleAccent,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = capsuleAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = onBand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                countersLeading?.invoke()
                counters.forEach { (value, caption) ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = value,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = onBand,
                        )
                        Text(
                            text = caption,
                            fontSize = 9.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // The page switch, on the band rather than on the photo.
        if (footer != null) {
            footer()
            Spacer(Modifier.height(8.dp))
        }
        // The picture itself, under the switch: the photo band of the page. Turned off it takes no
        // room at all — the band was only there to hold the picture, and a phone that turned the
        // picture off did it to get the list higher up the page.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showPhoto) HERO_HEIGHT else 0.dp)
                .background(scheme.surfaceContainerHighest),
        ) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // A photograph needs its bottom edge softened, so the content under it does not
                // look cut off; a missing one needs nothing.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.35f),
                            )
                        )
                )
            }
            // Wikimedia asks to be credited; the line is only shown when there really is their picture.
            if (image != null) {
                Text(
                    text = stringResource(Res.string.plans_hero_source),
                    fontSize = 8.sp,
                    color = onPicture.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

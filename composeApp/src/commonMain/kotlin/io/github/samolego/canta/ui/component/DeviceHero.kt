package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    footer: (@Composable () -> Unit)? = null,
    /** False hides the picture and keeps its band: the heading and the switch do not move. */
    showPhoto: Boolean = true,
    /**
     * Opens whatever switches the phone this page is about. The name becomes the button: it is the
     * thing the reader is looking at, and a chevron next to it says there is more than one.
     */
    onTitleClick: (() -> Unit)? = null,
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
                // the system line as it reached the column's edge. The click area is the whole
                // column either way.
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onTitleClick != null) {
                            Modifier.clickable(onClick = onTitleClick)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = onBand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onTitleClick != null) {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

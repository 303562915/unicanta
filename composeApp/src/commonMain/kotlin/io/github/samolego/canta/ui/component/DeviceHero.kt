package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
private const val USER_AGENT = "Canta/4.2 (device hero; https://github.com/303562915/Canta)"

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
 * The top of the safety page: the phone this page is about, as a picture, with what the page
 * knows about it written over the foot of the picture.
 *
 * A model whose picture cannot be found keeps the same layout on a muted grey band instead of a
 * coloured one, so a missing image reads as "no photo yet" rather than as a different design. The
 * model name is drawn either way, which is why the picture is only ever decoration.
 */
@Composable
fun DeviceHero(
    model: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var image by remember(model) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(model) {
        image = DeviceImages.load(model)
    }

    val scheme = MaterialTheme.colorScheme
    // The band under the picture: a photo gets a dark scrim so the white text stays readable, a
    // missing one is grey on grey.
    val scrim = if (image != null) Color.Black.copy(alpha = 0.45f) else scheme.surfaceContainerHighest
    val onScrim = if (image != null) Color.White else scheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(HERO_HEIGHT)
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
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to scrim,
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = onScrim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = onScrim.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = trailing,
            )
        }
        // Wikimedia asks to be credited; the line is only shown when there really is their picture.
        if (image != null) {
            Text(
                text = stringResource(Res.string.plans_hero_source),
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

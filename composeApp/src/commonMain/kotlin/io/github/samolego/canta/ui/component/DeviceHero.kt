package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plans_hero_source
import io.ktor.client.HttpClient
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
import io.ktor.client.call.body

/** How tall the picture of the phone is at the top of the plans page. */
private val HERO_HEIGHT = 150.dp

/** Where the picture comes from. Wikimedia Commons is the one public source that can be searched
 *  by model name and hot-linked without a key, so the app ships no per-model image list. */
private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"

/** Wikimedia wants a user agent that says what is asking. */
private const val USER_AGENT = "Canta/4.2 (device plans header; https://github.com/303562915/Canta)"

/** Wide enough for a phone screen at 2x, small enough that the first load is quick. */
private const val THUMB_WIDTH = 960

/**
 * Finds and downloads one picture of a phone, remembered for the life of the process: the header
 * asks once per model and shows the same bitmap from then on.
 *
 * Nothing here is required for the page to work — a model with no picture (or no network) simply
 * gets the drawn fallback.
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
        // model also returns unrelated photos, and a wrong phone in the header would be worse than
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
 * The top of the plans page: the phone this page is about, as a picture.
 *
 * The picture is looked up asynchronously and is purely decorative — the model name is already in
 * the header above, so a missing image changes nothing but the look.
 */
@Composable
fun DeviceHeroImage(model: String, modifier: Modifier = Modifier) {
    var image by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    var attempted by remember(model) { mutableStateOf(false) }

    LaunchedEffect(model) {
        attempted = false
        image = DeviceImages.load(model)
        attempted = true
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer.copy(alpha = 0.85f),
                        colors.tertiaryContainer.copy(alpha = 0.7f),
                    )
                )
            ),
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = model,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (attempted && image == null && model.isNotBlank()) {
            // No picture: the name is the picture.
            Text(
                text = model,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (image != null) {
            Text(
                text = stringResource(Res.string.plans_hero_source),
                fontSize = 8.sp,
                color = colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(colors.surface.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .padding(end = 2.dp),
            )
        }
    }
}

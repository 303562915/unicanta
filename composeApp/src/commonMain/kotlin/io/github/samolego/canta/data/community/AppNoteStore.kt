package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One review of an app, written on this install: what the phone it was written on is, so a
 * reader can weigh it ("does it work on a phone like mine?"), and when it was written.
 *
 * Reviews live on the device for now — there is no table for them on the backend yet — so
 * this is the shape they will keep once they sync.
 */
@Serializable
data class AppReview(
    val author: String,
    val body: String,
    val model: String,
    val sdk: Int,
    val createdAt: String,
    /** The app's own verdict on this phone: whether the reviewer could uninstall it. */
    val removable: Boolean = true,
)

@Serializable
private data class AppNotesBlob(
    val reviews: Map<String, List<AppReview>> = emptyMap(),
    val likedPackages: List<String> = emptyList(),
    val likedPlans: List<Long> = emptyList(),
)

/**
 * What this install remembers about apps and plans: the reviews it wrote, the apps it
 * marked as removable, and the plans it liked.
 *
 * Likes are remembered here rather than asked of the backend: `plan_vote` is idempotent but
 * never says whether this voter already voted, so the device is the only place that knows.
 */
class AppNoteStore(private val storage: AppBlobStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _reviews = MutableStateFlow<Map<String, List<AppReview>>>(emptyMap())
    private val _likedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _likedPlans = MutableStateFlow<Set<Long>>(emptySet())

    /** Reviews by package name, newest first. */
    val reviews: StateFlow<Map<String, List<AppReview>>> = _reviews.asStateFlow()

    /** Packages this install marked as safe to remove. */
    val likedPackages: StateFlow<Set<String>> = _likedPackages.asStateFlow()

    /** Plans this install liked. */
    val likedPlans: StateFlow<Set<Long>> = _likedPlans.asStateFlow()

    private var loaded = false

    /** Reads the blob once; called by every accessor before it answers. */
    private suspend fun ensureLoaded() = mutex.withLock {
        if (loaded) return@withLock
        runCatching {
            storage.read()?.decodeToString()?.let { text ->
                val blob = json.decodeFromString(AppNotesBlob.serializer(), text)
                _reviews.value = blob.reviews
                _likedPackages.value = blob.likedPackages.toSet()
                _likedPlans.value = blob.likedPlans.toSet()
            }
        }
        loaded = true
    }

    suspend fun ensureReady() = ensureLoaded()

    /** Reviews of [pkg], newest first; empty when there are none. */
    suspend fun reviewsOf(pkg: String): List<AppReview> {
        ensureLoaded()
        return _reviews.value[pkg].orEmpty()
    }

    /** Adds a review at the head of [pkg]'s list and stores it. */
    suspend fun addReview(pkg: String, review: AppReview) {
        ensureLoaded()
        mutex.withLock {
            val updated = _reviews.value.toMutableMap()
            updated[pkg] = listOf(review) + updated[pkg].orEmpty()
            _reviews.value = updated
            persistLocked()
        }
    }

    /** Flips whether this install says [pkg] can be removed, and answers the new state. */
    suspend fun togglePackageLike(pkg: String): Boolean {
        ensureLoaded()
        return mutex.withLock {
            val current = _likedPackages.value
            val liked = pkg !in current
            _likedPackages.value = if (liked) current + pkg else current - pkg
            persistLocked()
            liked
        }
    }

    /** Marks a plan as liked; liking twice keeps it liked, as the vote itself does. */
    suspend fun markPlanLiked(planId: Long) {
        ensureLoaded()
        mutex.withLock {
            if (planId in _likedPlans.value) return@withLock
            _likedPlans.value = _likedPlans.value + planId
            persistLocked()
        }
    }

    private suspend fun persistLocked() {
        val blob = AppNotesBlob(
            reviews = _reviews.value,
            likedPackages = _likedPackages.value.toList(),
            likedPlans = _likedPlans.value.toList(),
        )
        runCatching {
            storage.write(json.encodeToString(AppNotesBlob.serializer(), blob).encodeToByteArray())
        }
    }
}

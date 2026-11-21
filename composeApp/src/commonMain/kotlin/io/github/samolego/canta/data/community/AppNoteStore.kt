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
    /** The system the review was written on ("HyperOS 2"); empty on reviews from older builds. */
    val system: String = "",
) {
    /**
     * "Redmi K70 · HyperOS 2": the phone and the build, which is what a reader compares against.
     * The API level is deliberately not shown — [sdk] stays for older stored reviews only.
     */
    val deviceLabel: String
        get() = buildList {
            if (model.isNotBlank()) add(model)
            if (system.isNotBlank()) add(system)
        }.joinToString(" · ")
}

@Serializable
private data class AppNotesBlob(
    val reviews: Map<String, List<AppReview>> = emptyMap(),
    val likedPackages: List<String> = emptyList(),
    val likedPlans: List<Long> = emptyList(),
    /** What this install said about removing a package: true = fine without it, false = it broke something. */
    val packageVerdicts: Map<String, Boolean> = emptyMap(),
    /**
     * The newest reply this install has opened. Anything with a larger id is new, which is what the
     * per-row "new" marker compares against: replies are only ever added, so an id goes up and never
     * down. The red dot itself is decided by [inboxSeenAt].
     */
    val lastSeenReplyId: Long = 0L,
    /**
     * Where each of the four inbox tabs was last read, keyed by the backend's own kind name
     * (`reply`, `plan_comment`, `comment_heart`, `plan_heart`) and valued with the instant the
     * backend stamped. One cursor per tab is what keeps "read the replies" from clearing the
     * hearts; see [InboxCursors].
     *
     * Empty on an install that has never opened the inbox, and on one that was upgraded from a
     * build that only knew [inboxSeenAt] — [ensureLoaded] migrates that single value here.
     */
    val inboxSeenCursors: Map<String, String> = emptyMap(),
    /**
     * The old single cursor: one instant standing for the whole inbox.
     *
     * Nothing reads it any more — the four positions in [inboxSeenCursors] are the truth — but it is
     * still written as the newest of the four, so an older build reading the same blob still keeps
     * its red dot off instead of counting the whole history again.
     */
    val inboxSeenAt: String = "",
    /**
     * Comments written but not sent, keyed by what they answer ("plan:12", "app:com.a"): leaving
     * the page, or answering a call, must not throw away something half-typed.
     *
     * Only the one draft per target is kept — a second half-written comment for the same page
     * would have nowhere to go back to.
     */
    val commentDrafts: Map<String, String> = emptyMap(),
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
    private val _packageVerdicts = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _lastSeenReplyId = MutableStateFlow(0L)
    private val _inboxSeenCursors = MutableStateFlow(InboxCursors.EMPTY)
    private val _inboxSeenAt = MutableStateFlow("")
    private val _commentDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Reviews by package name, newest first. */
    val reviews: StateFlow<Map<String, List<AppReview>>> = _reviews.asStateFlow()

    /** Packages this install marked as safe to remove. */
    val likedPackages: StateFlow<Set<String>> = _likedPackages.asStateFlow()

    /** Plans this install liked. */
    val likedPlans: StateFlow<Set<Long>> = _likedPlans.asStateFlow()

    /**
     * What this install last said about each package: true = the phone was fine without it,
     * false = removing it broke something. Kept locally so the button shows the choice back,
     * and so the vote survives the backend being unreachable.
     */
    val packageVerdicts: StateFlow<Map<String, Boolean>> = _packageVerdicts.asStateFlow()

    /** The newest reply this install has opened; see [markRepliesSeen]. */
    val lastSeenReplyId: StateFlow<Long> = _lastSeenReplyId.asStateFlow()

    /** Where each of the four tabs was last read; see [markInboxSeen]. */
    val inboxSeenCursors: StateFlow<InboxCursors> = _inboxSeenCursors.asStateFlow()

    /**
     * The four positions as one string, for the callers that only carry a cursor around: the inbox
     * page hands it to `PlanRepository.inboxSummary` and gets the next one back as
     * [InboxSummary.checkedAt]. Encoding and decoding are [InboxCursors.encode] / [InboxCursors.decode];
     * empty means "never opened".
     */
    val inboxSeenAt: StateFlow<String> = _inboxSeenAt.asStateFlow()

    /** Comments written but not sent, by target key; see [draftOf] and [setDraft]. */
    val commentDrafts: StateFlow<Map<String, String>> = _commentDrafts.asStateFlow()

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
                _packageVerdicts.value = blob.packageVerdicts
                _lastSeenReplyId.value = blob.lastSeenReplyId
                // The four positions, or the single one an older build stored: that one instant is
                // taken as "all four tabs were read up to here", which is exactly what it meant
                // before the tabs were counted apart. Treating it as "nothing was read" instead
                // would light the red dot with a year of news the reader had already seen.
                _inboxSeenCursors.value = if (blob.inboxSeenCursors.isNotEmpty()) {
                    InboxCursors.ofMap(blob.inboxSeenCursors)
                } else {
                    InboxCursors.decode(blob.inboxSeenAt)
                }
                _inboxSeenAt.value = _inboxSeenCursors.value.encode()
                _commentDrafts.value = blob.commentDrafts
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

    /**
     * Stores what this install says about removing [pkg]: true = the phone was fine without it,
     * false = it broke something. Voting again simply replaces the earlier answer, matching the
     * one-vote-per-install the backend keeps.
     */
    suspend fun setPackageVerdict(pkg: String, canRemove: Boolean) {
        ensureLoaded()
        mutex.withLock {
            val updated = _packageVerdicts.value.toMutableMap()
            updated[pkg] = canRemove
            _packageVerdicts.value = updated
            persistLocked()
        }
    }

    /**
     * Remembers that every reply up to [id] has been read, so the red dot clears.
     *
     * It only ever moves forwards: opening an old page after a newer one must not make an answer
     * that was already read look new again.
     */
    suspend fun markRepliesSeen(id: Long) {
        if (id <= 0L) return
        ensureLoaded()
        mutex.withLock {
            if (id <= _lastSeenReplyId.value) return@withLock
            _lastSeenReplyId.value = id
            persistLocked()
        }
    }

    /**
     * Remembers that the inbox was read up to the four positions in [at] — the encoded cursors the
     * backend handed back (an [InboxSummary.checkedAt]).
     *
     * Each tab moves on its own: a tab whose rows the reader was not shown keeps its position, and
     * so keeps its mark. A plain instant — a value from a build that predates the four cursors, or
     * a backend that only answers one — means all four tabs read up to that instant, which is what
     * it meant before.
     *
     * Every position only ever moves forwards: the stamps are the backend's own UTC strings, so they
     * compare in order, and a slow answer from an earlier visit must not un-read what arrived after
     * it.
     *
     * Answers whether the dot may be cleared on the device: true only when all four positions ended
     * up at one and the same instant. That is the state the old single cursor was in, and the only
     * one where clearing the whole dot is true; a tab left at an earlier instant means news nobody
     * was shown, and the dot has to stay on for the next count to say how much. A backend that
     * answers no `checked_at` leaves every position where it was, so this answers false and the dot
     * does not blink.
     */
    suspend fun markInboxSeen(at: String): Boolean {
        val incoming = InboxCursors.decode(at)
        if (incoming.isBlank) return false
        ensureLoaded()
        return mutex.withLock {
            val before = _inboxSeenCursors.value
            var after = before
            InboxKind.entries.forEach { kind -> after = after.with(kind, incoming[kind]) }
            if (after == before) return@withLock false
            writeCursors(after)
            after == InboxCursors.of(after.latest)
        }
    }

    /**
     * Remembers that one tab was read up to [at] — the position the backend echoed for that kind.
     *
     * This is the call a single tab makes when it is opened, and the one the per-tab marks are built
     * on: the other tabs do not move. Only moves forwards, and answers whether it did.
     */
    suspend fun markInboxSeen(kind: InboxKind, at: String): Boolean {
        if (!isStamp(at)) return false
        ensureLoaded()
        return mutex.withLock {
            val before = _inboxSeenCursors.value
            val after = before.with(kind, at)
            if (after == before) return@withLock false
            writeCursors(after)
            true
        }
    }

    /** Stores [cursors] as the four read positions, and keeps the single-cursor mirror in step. */
    private suspend fun writeCursors(cursors: InboxCursors) {
        _inboxSeenCursors.value = cursors
        _inboxSeenAt.value = cursors.encode()
        persistLocked()
    }

    /** What this install said about [pkg], or null when it never voted. */
    suspend fun packageVerdict(pkg: String): Boolean? {
        ensureLoaded()
        return _packageVerdicts.value[pkg]
    }

    /** The half-written comment for [key], or empty when there is none. */
    suspend fun draftOf(key: String): String {
        ensureLoaded()
        return _commentDrafts.value[key].orEmpty()
    }

    /**
     * Keeps [body] as the draft for [key]; an empty body forgets it instead, so a page that was
     * written and then cleared does not come back with the old text.
     */
    suspend fun setDraft(key: String, body: String) {
        ensureLoaded()
        mutex.withLock {
            val current = _commentDrafts.value
            val updated = if (body.isBlank()) current - key else current + (key to body)
            if (updated == current) return@withLock
            _commentDrafts.value = updated
            persistLocked()
        }
    }

    private suspend fun persistLocked() {
        val blob = AppNotesBlob(
            reviews = _reviews.value,
            likedPackages = _likedPackages.value.toList(),
            likedPlans = _likedPlans.value.toList(),
            packageVerdicts = _packageVerdicts.value,
            lastSeenReplyId = _lastSeenReplyId.value,
            // Only the tabs that have a position: an unread tab is an absent key, not an empty one.
            inboxSeenCursors = InboxKind.entries.mapNotNull { kind ->
                _inboxSeenCursors.value[kind].takeIf { it.isNotEmpty() }?.let { kind.wire to it }
            }.toMap(),
            // The single-cursor mirror an older build reads: the newest of the four.
            inboxSeenAt = _inboxSeenCursors.value.latest,
            commentDrafts = _commentDrafts.value,
        )
        runCatching {
            storage.write(json.encodeToString(AppNotesBlob.serializer(), blob).encodeToByteArray())
        }
    }
}

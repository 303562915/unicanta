package io.github.samolego.canta.data.community

import io.github.samolego.canta.testing.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The half-written comments: a page left in the middle of writing one must come back with it.
 *
 * Leaving to look up a package name, answering a call, or the app being killed by the system are
 * all ordinary; losing the text is not.
 */
class AppNoteStoreDraftTest {

    @Test
    fun aDraftComesBackAfterARestart() = runTest {
        val storage = MemoryStorage()
        AppNoteStore(storage).setDraft("plan:12", "这个方案在我的机器上")

        // A second store over the same bytes is what the next launch of the app is.
        val reopened = AppNoteStore(storage)
        assertEquals("这个方案在我的机器上", reopened.draftOf("plan:12"))
    }

    @Test
    fun draftsAreKeptPerPage() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.setDraft("plan:12", "给方案 12 的")
        store.setDraft("app:com.a", "给这个应用的")

        assertEquals("给方案 12 的", store.draftOf("plan:12"))
        assertEquals("给这个应用的", store.draftOf("app:com.a"))
        assertEquals("", store.draftOf("plan:99"), "a page never written has nothing to restore")
    }

    @Test
    fun anEmptiedDraftIsForgotten() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.setDraft("plan:12", "写了一半")
        // The composer cleared: the page must not come back with the text it just sent.
        store.setDraft("plan:12", "")

        assertEquals("", store.draftOf("plan:12"))
        assertTrue(store.commentDrafts.value.isEmpty())
    }

    @Test
    fun sendingLeavesNothingBehindForAnEmptyBody() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.setDraft("plan:12", "   ")
        assertEquals("", store.draftOf("plan:12"), "whitespace is not something to restore")
    }
}
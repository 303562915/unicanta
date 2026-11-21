package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four inbox groups: what each one keeps, and which backend count it shows.
 *
 * The numbers on the chips have to be the backend's own, because they are the parts the red dot is
 * made of — a chip that computed its own count would disagree with the dot on the way in.
 */
class InboxFilterTest {

    private fun item(id: Long, kind: InboxKind, target: String = "12") = InboxItem(
        kind = kind,
        id = id,
        targetKind = COMMENT_KIND_PLAN,
        target = target,
        title = "title",
        body = "body",
        actor = "u-2",
        createdAt = "2026-11-20T10:00:00.000000Z",
    )

    private fun inbox() = listOf(
        item(1, InboxKind.REPLY),
        item(2, InboxKind.PLAN_COMMENT),
        item(3, InboxKind.COMMENT_HEART),
        item(4, InboxKind.PLAN_HEART),
    )

    @Test
    fun everyGroupKeepsItsOwnKind() {
        assertEquals(1, inbox().matching(InboxFilter.REPLY).size)
        assertEquals(1, inbox().matching(InboxFilter.PLAN_COMMENT).size)
        assertEquals(2, inbox().matching(InboxFilter.HEART).size, "both hearts are one tab")
        assertEquals(4, inbox().matching(InboxFilter.ALL).size)
    }

    @Test
    fun noGroupDropsARowItShouldKeep() {
        // Every row belongs to exactly one group: 1 + 1 + 2 = 4, so nothing is left out and
        // nothing is counted twice.
        val total = InboxFilter.entries
            .filter { it != InboxFilter.ALL }
            .sumOf { inbox().matching(it).size }
        assertEquals(inbox().size, total)
    }

    @Test
    fun eachChipShowsTheBackendCountForItsOwnGroup() {
        val summary = InboxSummary(
            replies = 3,
            planComments = 2,
            commentHearts = 7,
            planHearts = 5,
        )

        assertEquals(3, InboxFilter.REPLY.unreadIn(summary))
        assertEquals(2, InboxFilter.PLAN_COMMENT.unreadIn(summary))
        assertEquals(12, InboxFilter.HEART.unreadIn(summary), "both heart counts, added")
        assertEquals(17, InboxFilter.ALL.unreadIn(summary))
        assertEquals(summary.total, InboxFilter.ALL.unreadIn(summary), "the total is the dot's number")
    }

    @Test
    fun anEmptySummaryLeavesEveryChipQuiet() {
        InboxFilter.entries.forEach { filter ->
            assertTrue(filter.unreadIn(InboxSummary.EMPTY) == 0, "$filter counted something from nothing")
        }
    }

    @Test
    fun aGroupAcceptsOnlyWhatItNames() {
        assertTrue(InboxFilter.HEART.accepts(InboxKind.COMMENT_HEART))
        assertTrue(InboxFilter.HEART.accepts(InboxKind.PLAN_HEART))
        assertTrue(!InboxFilter.REPLY.accepts(InboxKind.PLAN_COMMENT), "a plan comment is not a reply to me")
        assertTrue(InboxFilter.ALL.accepts(InboxKind.PLAN_HEART))
    }

    @Test
    fun everyGroupNamesTheKindsItHolds() {
        // The bridge to the per-tab cursors: a tab reads the kinds it holds, and nothing else.
        assertEquals(setOf(InboxKind.REPLY), InboxFilter.REPLY.kinds())
        assertEquals(setOf(InboxKind.PLAN_COMMENT), InboxFilter.PLAN_COMMENT.kinds())
        assertEquals(setOf(InboxKind.COMMENT_HEART, InboxKind.PLAN_HEART), InboxFilter.HEART.kinds())
        assertEquals(InboxKind.entries.toSet(), InboxFilter.ALL.kinds())
    }

    @Test
    fun theGroupsTogetherCoverEveryKindExactlyOnce() {
        // Two tabs must not share a kind, or reading one would move the other's position; and no
        // kind may be missing, or its news would have no cursor at all.
        val named = InboxFilter.entries.filter { it != InboxFilter.ALL }.flatMap { it.kinds() }
        assertEquals(named.size, named.toSet().size, "two tabs hold the same kind")
        assertEquals(InboxKind.entries.toSet(), named.toSet(), "a kind belongs to no tab")
    }

    @Test
    fun acceptingAndNamingAgreeForEveryKind() {
        InboxFilter.entries.forEach { filter ->
            InboxKind.entries.forEach { kind ->
                assertEquals(kind in filter.kinds(), filter.accepts(kind), "$filter about $kind")
            }
        }
    }
}
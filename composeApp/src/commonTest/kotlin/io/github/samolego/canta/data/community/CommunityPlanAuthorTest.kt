package io.github.samolego.canta.data.community

import io.github.samolego.canta.ui.component.avatarLetter
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a plan card draws next to the avatar when the backend does or does not know the name. */
class CommunityPlanAuthorTest {

    @Test
    fun namedUploaderShowsTheName() {
        val plan = CommunityPlan(
            id = 1, title = "t", note = "", packages = listOf("com.a"), votes = 0,
            comments = 0, createdAt = "", rebootedAt = "", owner = "10001", uploader = "Alice",
        )
        assertEquals("Alice", plan.authorName)
        assertEquals("A", avatarLetter(plan.authorName))
    }

    @Test
    fun unnamedUploaderFallsBackToTheNumber() {
        // A plan from before names were synced, or a deployment that does not send owner_name yet.
        val plan = CommunityPlan(
            id = 1, title = "t", note = "", packages = listOf("com.a"), votes = 0,
            comments = 0, createdAt = "", rebootedAt = "", owner = "10000",
        )
        assertEquals("#10000", plan.authorName)
        // The avatar skips the sign, so the letter is the first digit.
        assertEquals("1", avatarLetter(plan.authorName))
    }

    @Test
    fun ownerlessPlanHasNoAuthor() {
        val plan = CommunityPlan(
            id = 1, title = "t", note = "", packages = emptyList(), votes = 0,
            comments = 0, createdAt = "", rebootedAt = "",
        )
        assertEquals("", plan.authorName)
        assertEquals("?", avatarLetter(plan.authorName))
    }
}

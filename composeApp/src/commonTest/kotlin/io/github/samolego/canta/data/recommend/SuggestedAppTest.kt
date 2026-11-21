package io.github.samolego.canta.data.recommend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the two things a proposed app is judged by: the link has to be a GitHub project, and the
 * counts decide whether it joins the page — 40 of 50 yes is in, 40 of 51 is not.
 */
class SuggestedAppTest {

    @Test
    fun acceptsProjectPagesAndKeepsTheRoot() {
        assertEquals("https://github.com/ImranR98/Obtainium", githubProjectUrl("https://github.com/ImranR98/Obtainium"))
        // A tab someone was reading is one tap further; the entry opens the project.
        assertEquals(
            "https://github.com/ImranR98/Obtainium",
            githubProjectUrl("https://github.com/ImranR98/Obtainium/releases/tag/v1.0?tab=readme"),
        )
        assertEquals("https://github.com/ImranR98/Obtainium", githubProjectUrl("  ImranR98/Obtainium  "))
        assertEquals("https://github.com/ImranR98/Obtainium", githubProjectUrl("github.com/ImranR98/Obtainium"))
        assertEquals("https://github.com/ImranR98/Obtainium", githubProjectUrl("http://github.com/ImranR98/Obtainium"))
        // A clone URL is still the project.
        assertEquals("https://github.com/ImranR98/Obtainium", githubProjectUrl("https://github.com/ImranR98/Obtainium.git"))
    }

    @Test
    fun refusesAnythingThatIsNotAGithubProject() {
        assertNull(githubProjectUrl(""))
        assertNull(githubProjectUrl("   "))
        assertNull(githubProjectUrl("https://gitlab.com/owner/repo"))
        assertNull(githubProjectUrl("https://github.com/"))
        assertNull(githubProjectUrl("https://github.com/owner"))
        assertNull(githubProjectUrl("https://example.com/owner/repo"))
        assertNull(githubProjectUrl("ftp://github.com/owner/repo"))
        assertNull(githubProjectUrl("https://github.com/owner/repo.git.git"))
        assertNull(githubProjectUrl("Obtainium"))
    }

    @Test
    fun ownerOfIsTheAccountBehindTheLink() {
        assertEquals("ImranR98", githubOwnerOf("https://github.com/ImranR98/Obtainium"))
        assertEquals("", githubOwnerOf("https://gitlab.com/owner/repo"))
    }

    @Test
    fun ruleIsFortyFiveVotesAndEightyPercent() {
        assertEquals(45, SuggestedApp.VOTES_NEEDED)
        assertEquals(0.8f, SuggestedApp.SHARE_NEEDED)
        // The defaults a row from a backend that names no rule falls back to are the same rule.
        assertEquals(45, SuggestedApp().needed)
        assertEquals(0.8f, SuggestedApp().shareNeeded)
    }

    @Test
    fun thirtySixOfFortyFiveIsInButThirtySixOfFortySixIsNot() {
        // Forty-five votes with thirty-six approving is exactly 80%: it clears with no vote to spare.
        val passes = SuggestedApp(agrees = 36, disagrees = 9, needed = 45, shareNeeded = 0.8f)
        assertEquals(45, passes.votes)
        assertEquals(36, passes.agrees)
        assertEquals(0, passes.missingVotes)
        assertTrue(passes.satisfied)

        // The same thirty-six approvals among forty-six votes is 78%: the bar has to be missed.
        val fails = SuggestedApp(agrees = 36, disagrees = 10, needed = 45, shareNeeded = 0.8f)
        assertEquals(0, fails.missingVotes)
        assertFalse(fails.satisfied)

        val barely = SuggestedApp(agrees = 45, disagrees = 11, needed = 45, shareNeeded = 0.8f)
        assertTrue(barely.satisfied)

        val short = SuggestedApp(agrees = 20, disagrees = 10, needed = 45, shareNeeded = 0.8f)
        assertEquals(15, short.missingVotes)
        assertFalse(short.satisfied)
    }

    @Test
    fun noVotesMeansNoShare() {
        val fresh = SuggestedApp()
        assertEquals(0, fresh.votes)
        assertNull(fresh.share)
        assertEquals(45, fresh.missingVotes)
        assertFalse(fresh.satisfied)
        assertEquals("?", fresh.tileMark)
        assertEquals("O", SuggestedApp(name = "Obtainium").tileMark)
    }
}
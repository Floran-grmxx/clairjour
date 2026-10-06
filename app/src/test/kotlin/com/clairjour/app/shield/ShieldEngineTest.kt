package com.clairjour.app.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldEngineTest {

    private var now = 0L
    private val engine = ShieldEngine { now }
    private val allRules = ShieldRule.entries.toSet()

    private fun advance(millis: Long = 1_000) { now += millis }

    private val directThread = InstagramScreen(directThread = true)
    private val homeFeed = InstagramScreen(selectedTab = InstagramTab.HOME)
    private val profile = InstagramScreen(profile = true)
    private fun reel(key: String?) = InstagramScreen(reelViewer = true, reelContentKey = key)

    @Test
    fun `reels tab redirects to the home tab`() {
        val actions = engine.onScreen(InstagramScreen(selectedTab = InstagramTab.REELS, reelViewer = true), allRules)
        assertEquals(listOf(ShieldAction.OpenHomeTab), actions)
    }

    @Test
    fun `reels tab is allowed when its rule is off`() {
        val actions = engine.onScreen(
            InstagramScreen(selectedTab = InstagramTab.REELS, reelViewer = true),
            allRules - ShieldRule.REELS_TAB
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `reel from a conversation plays until the user swipes`() {
        engine.onScreen(directThread, allRules)
        advance()
        assertTrue(engine.onScreen(reel("alice"), allRules).isEmpty())
        advance()
        assertTrue(engine.onScreen(reel("alice"), allRules).isEmpty())
        advance()
        assertEquals(
            listOf(ShieldAction.GoBack(ShieldRule.DIRECT_SINGLE_REEL)),
            engine.onScreen(reel("bob"), allRules)
        )
    }

    @Test
    fun `reel key appearing late becomes the reference`() {
        engine.onScreen(directThread, allRules)
        advance()
        assertTrue(engine.onScreen(reel(null), allRules).isEmpty())
        advance()
        assertTrue(engine.onScreen(reel("alice"), allRules).isEmpty())
        advance()
        assertEquals(1, engine.onScreen(reel("bob"), allRules).size)
    }

    @Test
    fun `conversation reels are free when the single reel rule is off`() {
        val rules = allRules - ShieldRule.DIRECT_SINGLE_REEL
        engine.onScreen(directThread, rules)
        engine.onScreen(reel("alice"), rules)
        advance()
        assertTrue(engine.onScreen(reel("bob"), rules).isEmpty())
    }

    @Test
    fun `reel from the home feed goes back`() {
        engine.onScreen(homeFeed, allRules)
        advance()
        assertEquals(listOf(ShieldAction.GoBack(ShieldRule.OTHER_REELS)), engine.onScreen(reel("alice"), allRules))
    }

    @Test
    fun `reel from a profile follows the profile rule`() {
        engine.onScreen(profile, allRules - ShieldRule.PROFILE_REELS)
        assertTrue(engine.onScreen(reel("alice"), allRules - ShieldRule.PROFILE_REELS).isEmpty())

        engine.reset()
        engine.onScreen(profile, allRules)
        advance()
        assertEquals(listOf(ShieldAction.GoBack(ShieldRule.PROFILE_REELS)), engine.onScreen(reel("alice"), allRules))
    }

    @Test
    fun `back actions are throttled`() {
        engine.onScreen(homeFeed, allRules)
        advance()
        assertEquals(1, engine.onScreen(reel("alice"), allRules).size)
        advance(100)
        assertTrue(engine.onScreen(reel("alice"), allRules).isEmpty())
        advance(ShieldEngine.NAVIGATION_COOLDOWN_MILLIS)
        assertEquals(1, engine.onScreen(reel("alice"), allRules).size)
    }

    @Test
    fun `origin survives transient unknown screens`() {
        engine.onScreen(directThread, allRules)
        engine.onScreen(InstagramScreen(), allRules)
        advance()
        assertTrue(engine.onScreen(reel("alice"), allRules).isEmpty())
    }

    @Test
    fun `explore grid is covered once and hidden when leaving`() {
        val grid = NodeBounds(0, 300, 1080, 2250)
        val search = InstagramScreen(selectedTab = InstagramTab.SEARCH, exploreGridBounds = grid)
        assertEquals(listOf(ShieldAction.ShowCover(grid, ShieldRule.EXPLORE_GRID)), engine.onScreen(search, allRules))
        assertTrue(engine.onScreen(search, allRules).isEmpty())
        assertEquals(listOf(ShieldAction.HideCover), engine.onScreen(homeFeed, allRules))
    }

    @Test
    fun `home feed cover follows its rule`() {
        val feedArea = NodeBounds(0, 520, 1080, 2250)
        val feed = homeFeed.copy(homeFeedBounds = feedArea)
        assertTrue(engine.onScreen(feed, ShieldRule.defaults).isEmpty())
        assertEquals(
            listOf(ShieldAction.ShowCover(feedArea, ShieldRule.HOME_FEED)),
            engine.onScreen(feed, ShieldRule.defaults + ShieldRule.HOME_FEED)
        )
    }

    @Test
    fun `search results are forced onto accounts`() {
        val results = InstagramScreen(searchResultTabs = SearchResultTabs(accountsSelected = false))
        assertEquals(listOf(ShieldAction.SelectAccountsTab), engine.onScreen(results, allRules))
        advance(100)
        assertTrue(engine.onScreen(results, allRules).isEmpty())
        val onAccounts = InstagramScreen(searchResultTabs = SearchResultTabs(accountsSelected = true))
        advance()
        assertTrue(engine.onScreen(onAccounts, allRules).isEmpty())
    }

    @Test
    fun `stories go back only when their rule is on`() {
        val story = InstagramScreen(storyViewer = true)
        assertTrue(engine.onScreen(story, ShieldRule.defaults).isEmpty())
        advance()
        assertEquals(listOf(ShieldAction.GoBack(ShieldRule.STORIES)), engine.onScreen(story, allRules))
    }

    @Test
    fun `reset hides an active cover`() {
        val search = InstagramScreen(selectedTab = InstagramTab.SEARCH, exploreGridBounds = NodeBounds(0, 300, 1080, 2250))
        engine.onScreen(search, allRules)
        assertEquals(listOf(ShieldAction.HideCover), engine.reset())
        assertTrue(engine.reset().isEmpty())
    }
}

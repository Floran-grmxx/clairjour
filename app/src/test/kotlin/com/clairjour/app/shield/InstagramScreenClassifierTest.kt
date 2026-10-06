package com.clairjour.app.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramScreenClassifierTest {

    private val screenBounds = NodeBounds(0, 0, 1080, 2400)
    private val bottomBarBounds = NodeBounds(0, 2250, 1080, 2400)

    private fun bottomBar(selected: String): ScreenNode = ScreenNode(
        viewId = "tab_bar",
        bounds = bottomBarBounds,
        children = listOf("feed_tab", "search_tab", "clips_tab", "profile_tab").map {
            ScreenNode(viewId = it, isSelected = it == selected, bounds = bottomBarBounds)
        }
    )

    private fun reelPager(author: String): ScreenNode = ScreenNode(
        viewId = "clips_viewer_view_pager",
        bounds = screenBounds,
        children = listOf(
            ScreenNode(
                bounds = screenBounds,
                children = listOf(ScreenNode(viewId = "clips_author_username", text = author))
            )
        )
    )

    private fun root(vararg children: ScreenNode) = ScreenNode(bounds = screenBounds, children = children.toList())

    @Test
    fun `reels tab is detected from the selected bottom tab`() {
        val screen = InstagramScreenClassifier.classify(root(reelPager("alice"), bottomBar("clips_tab")))
        assertTrue(screen.reelViewer)
        assertEquals(InstagramTab.REELS, screen.selectedTab)
        assertEquals(InstagramSurface.REELS_TAB, screen.surface)
    }

    @Test
    fun `reel content key changes with the author`() {
        val first = InstagramScreenClassifier.classify(root(reelPager("alice")))
        val second = InstagramScreenClassifier.classify(root(reelPager("bob")))
        assertEquals("alice", first.reelContentKey)
        assertNotEquals(first.reelContentKey, second.reelContentKey)
    }

    @Test
    fun `selected flag on the tab container is inherited`() {
        val tab = ScreenNode(
            viewId = "search_tab_container",
            isSelected = true,
            bounds = bottomBarBounds,
            children = listOf(ScreenNode(viewId = "search_tab", bounds = bottomBarBounds))
        )
        val screen = InstagramScreenClassifier.classify(root(tab))
        assertEquals(InstagramTab.SEARCH, screen.selectedTab)
    }

    @Test
    fun `bottom tab fallback uses content descriptions in the bottom bar only`() {
        val bottomReels = ScreenNode(contentDescription = "Reels", isSelected = true, bounds = bottomBarBounds)
        assertEquals(InstagramTab.REELS, InstagramScreenClassifier.classify(root(bottomReels)).selectedTab)

        val profileReelsTab = ScreenNode(
            contentDescription = "Reels",
            isSelected = true,
            bounds = NodeBounds(360, 900, 720, 1000)
        )
        assertNull(InstagramScreenClassifier.classify(root(profileReelsTab)).selectedTab)
    }

    @Test
    fun `direct thread is detected`() {
        val screen = InstagramScreenClassifier.classify(root(ScreenNode(viewId = "direct_thread_container")))
        assertTrue(screen.directThread)
        assertEquals(InstagramSurface.DIRECT, screen.surface)
    }

    @Test
    fun `explore grid bounds are reported`() {
        val grid = ScreenNode(viewId = "explore_grid", bounds = NodeBounds(0, 300, 1080, 2250))
        val screen = InstagramScreenClassifier.classify(root(grid, bottomBar("search_tab")))
        assertEquals(NodeBounds(0, 300, 1080, 2250), screen.exploreGridBounds)
        assertEquals(InstagramSurface.SEARCH, screen.surface)
    }

    @Test
    fun `home feed cover starts below the stories tray`() {
        val feed = ScreenNode(
            viewId = "main_feed_list",
            bounds = NodeBounds(0, 200, 1080, 2250),
            children = listOf(ScreenNode(viewId = "reels_tray_container", bounds = NodeBounds(0, 200, 1080, 520)))
        )
        val screen = InstagramScreenClassifier.classify(root(feed, bottomBar("feed_tab")))
        assertEquals(NodeBounds(0, 520, 1080, 2250), screen.homeFeedBounds)
    }

    private fun searchTabs(selectedLabel: String): ScreenNode = ScreenNode(
        viewId = "search_tabs",
        children = listOf("Pour vous", "Comptes", "Reels", "Audio").mapIndexed { index, label ->
            ScreenNode(
                isSelected = label == selectedLabel,
                bounds = NodeBounds(index * 270, 300, (index + 1) * 270, 400),
                children = listOf(ScreenNode(text = label, bounds = NodeBounds(index * 270, 310, (index + 1) * 270, 390)))
            )
        }
    )

    @Test
    fun `search result tabs report whether accounts is selected`() {
        assertEquals(false, InstagramScreenClassifier.classify(root(searchTabs("Pour vous"))).searchResultTabs?.accountsSelected)
        assertEquals(true, InstagramScreenClassifier.classify(root(searchTabs("Comptes"))).searchResultTabs?.accountsSelected)
    }

    @Test
    fun `a lone accounts label is not a search tab row`() {
        val screen = InstagramScreenClassifier.classify(root(ScreenNode(text = "Comptes")))
        assertNull(screen.searchResultTabs)
        assertFalse(screen.reelViewer)
    }
}

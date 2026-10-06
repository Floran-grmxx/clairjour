package com.clairjour.app.shield

import com.clairjour.app.shield.InstagramSelectors as Selectors

enum class InstagramTab { HOME, SEARCH, REELS, PROFILE }

/** Where the user is in Instagram, used to know where a reel was opened from. */
enum class InstagramSurface { HOME_FEED, SEARCH, REELS_TAB, DIRECT, PROFILE, STORY, OTHER }

/** Tabs displayed after submitting a search ("Accounts", "Reels", "For you"...). */
data class SearchResultTabs(val accountsSelected: Boolean)

/** Everything the rules need to know about the current Instagram screen. */
data class InstagramScreen(
    val selectedTab: InstagramTab? = null,
    val reelViewer: Boolean = false,
    /** Identifies the reel currently shown; changes when swiping to another reel. */
    val reelContentKey: String? = null,
    val storyViewer: Boolean = false,
    val directThread: Boolean = false,
    val profile: Boolean = false,
    val exploreGridBounds: NodeBounds? = null,
    /** Home feed area below the stories tray. */
    val homeFeedBounds: NodeBounds? = null,
    val searchResultTabs: SearchResultTabs? = null
) {
    val surface: InstagramSurface
        get() = when {
            reelViewer && selectedTab == InstagramTab.REELS -> InstagramSurface.REELS_TAB
            storyViewer -> InstagramSurface.STORY
            directThread -> InstagramSurface.DIRECT
            profile -> InstagramSurface.PROFILE
            selectedTab == InstagramTab.HOME -> InstagramSurface.HOME_FEED
            selectedTab == InstagramTab.SEARCH -> InstagramSurface.SEARCH
            selectedTab == InstagramTab.PROFILE -> InstagramSurface.PROFILE
            else -> InstagramSurface.OTHER
        }
}

object InstagramScreenClassifier {

    private const val SAME_ROW_TOLERANCE_PIXELS = 60
    private const val REEL_KEY_MAX_LABELS = 3

    fun classify(root: ScreenNode): InstagramScreen {
        val nodes = root.walk().toList()
        val reelViewerNode = nodes.firstOrNull { Selectors.matches(it.viewId, Selectors.reelViewerIds) }

        return InstagramScreen(
            selectedTab = selectedTab(root),
            reelViewer = reelViewerNode != null,
            reelContentKey = reelViewerNode?.let { reelContentKey(it) },
            storyViewer = nodes.any { Selectors.matches(it.viewId, Selectors.storyViewerIds) },
            directThread = nodes.any { Selectors.matches(it.viewId, Selectors.directThreadIds) },
            profile = nodes.any { Selectors.matches(it.viewId, Selectors.profileIds) },
            exploreGridBounds = nodes
                .firstOrNull { Selectors.matches(it.viewId, Selectors.exploreGridIds) }
                ?.bounds?.takeUnless { it.isEmpty },
            homeFeedBounds = homeFeedBounds(nodes),
            searchResultTabs = searchResultTabs(root)
        )
    }

    private fun selectedTab(root: ScreenNode): InstagramTab? {
        val tabsById = listOf(
            InstagramTab.HOME to Selectors.homeTabIds,
            InstagramTab.SEARCH to Selectors.searchTabIds,
            InstagramTab.REELS to Selectors.reelsTabIds,
            InstagramTab.PROFILE to Selectors.profileTabIds
        )
        val bottomBarMinTop = root.bounds.top + (root.bounds.height * Selectors.BOTTOM_BAR_MIN_TOP_RATIO).toInt()
        val tabsByLabel = listOf(
            InstagramTab.HOME to Selectors.homeTabLabels,
            InstagramTab.SEARCH to Selectors.searchTabLabels,
            InstagramTab.REELS to Selectors.reelsTabLabels,
            InstagramTab.PROFILE to Selectors.profileTabLabels
        )
        for ((node, selected) in root.walkWithSelection()) {
            if (!selected) continue
            tabsById.firstOrNull { Selectors.matches(node.viewId, it.second) }?.let { return it.first }
        }
        // Fallback on content descriptions, only for nodes sitting in the bottom bar.
        for ((node, selected) in root.walkWithSelection()) {
            if (!selected || node.bounds.top < bottomBarMinTop) continue
            val description = node.contentDescription?.lowercase()?.trim() ?: continue
            tabsByLabel.firstOrNull { description in it.second }?.let { return it.first }
        }
        return null
    }

    private fun reelContentKey(pager: ScreenNode): String? {
        // The visible page is the largest child of the pager.
        val page = pager.children.maxByOrNull { it.bounds.height.coerceAtLeast(0) } ?: pager
        val authorLabels = page.walk()
            .filter { Selectors.matches(it.viewId, Selectors.reelAuthorIds) }
            .mapNotNull { it.label?.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val labels = authorLabels.ifEmpty {
            page.walk()
                .mapNotNull { it.label?.trim() }
                .filter { it.length >= 3 && it.any(Char::isLetter) }
                .take(REEL_KEY_MAX_LABELS)
                .toList()
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString("|")
    }

    private fun homeFeedBounds(nodes: List<ScreenNode>): NodeBounds? {
        val feed = nodes.firstOrNull { Selectors.matches(it.viewId, Selectors.homeFeedListIds) }
            ?.bounds?.takeUnless { it.isEmpty } ?: return null
        val trayBottom = nodes
            .filter { Selectors.matches(it.viewId, Selectors.storiesTrayIds) && !it.bounds.isEmpty }
            .maxOfOrNull { it.bounds.bottom }
        val top = maxOf(feed.top, trayBottom ?: feed.top)
        return NodeBounds(feed.left, top, feed.right, feed.bottom).takeUnless { it.isEmpty }
    }

    private fun searchResultTabs(root: ScreenNode): SearchResultTabs? {
        val labelled = root.walkWithSelection()
            .mapNotNull { (node, selected) ->
                node.label?.lowercase()?.trim()?.let { Triple(it, node, selected) }
            }
            .toList()
        val accountsTab = labelled.firstOrNull { it.first in Selectors.searchAccountsTabLabels } ?: return null
        val rowTop = accountsTab.second.bounds.top
        val otherTabs = labelled.filter {
            it.first in Selectors.searchOtherTabLabels &&
                kotlin.math.abs(it.second.bounds.top - rowTop) <= SAME_ROW_TOLERANCE_PIXELS
        }
        // A real tab row shows "Accounts" next to at least two other result tabs.
        if (otherTabs.map { it.first }.distinct().size < 2) return null
        return SearchResultTabs(accountsSelected = accountsTab.third)
    }
}

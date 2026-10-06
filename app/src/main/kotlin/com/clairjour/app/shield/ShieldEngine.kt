package com.clairjour.app.shield

/** Something the accessibility service must do on the Instagram screen. */
sealed interface ShieldAction {
    data class GoBack(val rule: ShieldRule) : ShieldAction
    /** Leaves the Reels tab by tapping the home tab (falls back to "back"). */
    data object OpenHomeTab : ShieldAction
    /** Taps the "Accounts" tab of the search results. */
    data object SelectAccountsTab : ShieldAction
    data class ShowCover(val bounds: NodeBounds, val rule: ShieldRule) : ShieldAction
    data object HideCover : ShieldAction
}

/**
 * Pure decision logic: receives the classified Instagram screen and the active rules,
 * returns the actions to perform. Keeps track of where each reel was opened from.
 */
class ShieldEngine(private val clock: () -> Long) {

    private data class ReelSession(val origin: InstagramSurface, val firstContentKey: String?)

    private var lastSurface = InstagramSurface.OTHER
    private var reelSession: ReelSession? = null
    private var currentCover: NodeBounds? = null
    private var lastNavigationAt = Long.MIN_VALUE / 2
    private var lastTabSelectionAt = Long.MIN_VALUE / 2

    /** Called when Instagram leaves the foreground. */
    fun reset(): List<ShieldAction> {
        lastSurface = InstagramSurface.OTHER
        reelSession = null
        return hideCover()
    }

    fun onScreen(screen: InstagramScreen, rules: Set<ShieldRule>): List<ShieldAction> {
        val actions = mutableListOf<ShieldAction>()

        if (screen.reelViewer) {
            navigationFor(screen, rules)?.let { if (canNavigate()) actions += it }
        } else {
            reelSession = null
            if (screen.surface != InstagramSurface.OTHER) lastSurface = screen.surface
            if (screen.storyViewer && ShieldRule.STORIES in rules && canNavigate()) {
                actions += ShieldAction.GoBack(ShieldRule.STORIES)
            }
        }

        val accountsMissing = screen.searchResultTabs?.accountsSelected == false
        if (accountsMissing && ShieldRule.SEARCH_ACCOUNTS_ONLY in rules) {
            val now = clock()
            if (now - lastTabSelectionAt >= TAB_SELECTION_COOLDOWN_MILLIS) {
                lastTabSelectionAt = now
                actions += ShieldAction.SelectAccountsTab
            }
        }

        actions += coverFor(screen, rules)
        return actions
    }

    private fun navigationFor(screen: InstagramScreen, rules: Set<ShieldRule>): ShieldAction? {
        if (screen.selectedTab == InstagramTab.REELS) {
            reelSession = ReelSession(InstagramSurface.REELS_TAB, screen.reelContentKey)
            return if (ShieldRule.REELS_TAB in rules) ShieldAction.OpenHomeTab else null
        }

        val session = reelSession
            ?.let { if (it.firstContentKey == null) it.copy(firstContentKey = screen.reelContentKey) else it }
            ?: ReelSession(lastSurface, screen.reelContentKey)
        reelSession = session

        return when (session.origin) {
            InstagramSurface.DIRECT -> {
                val swipedToAnotherReel = session.firstContentKey != null &&
                    screen.reelContentKey != null &&
                    screen.reelContentKey != session.firstContentKey
                if (swipedToAnotherReel && ShieldRule.DIRECT_SINGLE_REEL in rules) {
                    ShieldAction.GoBack(ShieldRule.DIRECT_SINGLE_REEL)
                } else null
            }
            InstagramSurface.PROFILE ->
                if (ShieldRule.PROFILE_REELS in rules) ShieldAction.GoBack(ShieldRule.PROFILE_REELS) else null
            InstagramSurface.REELS_TAB ->
                if (ShieldRule.REELS_TAB in rules) ShieldAction.GoBack(ShieldRule.REELS_TAB) else null
            else ->
                if (ShieldRule.OTHER_REELS in rules) ShieldAction.GoBack(ShieldRule.OTHER_REELS) else null
        }
    }

    private fun coverFor(screen: InstagramScreen, rules: Set<ShieldRule>): List<ShieldAction> {
        val wanted: Pair<NodeBounds, ShieldRule>? = when {
            screen.reelViewer || screen.storyViewer -> null
            screen.selectedTab == InstagramTab.SEARCH && ShieldRule.EXPLORE_GRID in rules ->
                screen.exploreGridBounds?.let { it to ShieldRule.EXPLORE_GRID }
            screen.surface == InstagramSurface.HOME_FEED && ShieldRule.HOME_FEED in rules ->
                screen.homeFeedBounds?.let { it to ShieldRule.HOME_FEED }
            else -> null
        }
        if (wanted == null) return hideCover()
        if (wanted.first == currentCover) return emptyList()
        currentCover = wanted.first
        return listOf(ShieldAction.ShowCover(wanted.first, wanted.second))
    }

    private fun hideCover(): List<ShieldAction> {
        if (currentCover == null) return emptyList()
        currentCover = null
        return listOf(ShieldAction.HideCover)
    }

    /** Prevents chained "back" actions while Instagram is still animating the previous one. */
    private fun canNavigate(): Boolean {
        val now = clock()
        if (now - lastNavigationAt < NAVIGATION_COOLDOWN_MILLIS) return false
        lastNavigationAt = now
        return true
    }

    companion object {
        const val NAVIGATION_COOLDOWN_MILLIS = 700L
        const val TAB_SELECTION_COOLDOWN_MILLIS = 800L
    }
}

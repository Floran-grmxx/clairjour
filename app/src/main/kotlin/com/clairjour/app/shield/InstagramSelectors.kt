package com.clairjour.app.shield

/**
 * Every Instagram-specific selector lives here, so that an Instagram update only requires
 * editing this file. Instagram calls Reels "clips" internally and Stories "reels".
 *
 * Calibrated on Instagram 449.0.0.52.84 (Android 14, French UI) on 2026-10-06.
 * View ids are compared exactly, unless the selector name says "fragments".
 */
object InstagramSelectors {

    const val PACKAGE_NAME = "com.instagram.android"
    const val VIEW_ID_PREFIX = "$PACKAGE_NAME:id/"

    // Bottom navigation tabs. Exact match matters: the profile page has "profile_tab_layout".
    const val HOME_TAB_ID = "feed_tab"
    const val SEARCH_TAB_ID = "search_tab"
    const val REELS_TAB_ID = "clips_tab"
    const val PROFILE_TAB_ID = "profile_tab"

    // Fallback when the tab ids change: content descriptions of the bottom tabs (FR + EN).
    val homeTabLabels = setOf("accueil", "home")
    val searchTabLabels = setOf("rechercher et explorer", "search and explore")
    val reelsTabLabels = setOf("reels")
    val profileTabLabels = setOf("profil", "profile")

    /** Bottom tabs are only trusted in the lower part of the screen (ratio of screen height). */
    const val BOTTOM_BAR_MIN_TOP_RATIO = 0.80

    /** Full-screen Reels player: Reels tab, and any reel opened from Explore, feed, DMs or profiles. */
    val reelViewerIds = setOf("clips_viewer_view_pager")

    /** Author name inside a reel page: changes when swiping to the next reel. */
    val reelAuthorIds = setOf("clips_author_username")

    /** Stories and highlights player. */
    val storyViewerIds = setOf("reel_viewer_root")

    /** Direct message conversation. */
    val directThreadIds = setOf("thread_fragment_container", "row_thread_composer_edittext")

    /** Profile page (own or someone else's). */
    val profileIdFragments = listOf("profile_header_", "row_profile_header")

    /** Explore grid: a generic "recycler_view" whose items are grid cards. */
    const val EXPLORE_GRID_CONTAINER_ID = "recycler_view"
    val exploreGridItemIds = setOf("grid_card_layout_container", "layout_container")

    /** Search bar drawn above the top of the Explore grid: the cover must start below it. */
    const val SEARCH_BAR_ID = "action_bar_search_edit_text"

    /** Home feed list and the items of the stories tray at its top. */
    const val HOME_FEED_LIST_ID = "android:id/list"
    const val STORIES_TRAY_ITEM_ID = "outer_container"

    // Tabs shown after submitting a search (FR + EN labels, compared lowercase).
    val searchAccountsTabLabels = setOf("comptes", "accounts")
    val searchOtherTabLabels = setOf(
        "pour vous", "for you", "top", "non personnalisée", "not personalized",
        "reels", "audio", "tags", "hashtags", "lieux", "places", "publications", "posts"
    )

    fun matchesAny(viewId: String?, ids: Set<String>): Boolean = viewId != null && viewId in ids

    fun containsAny(viewId: String?, fragments: List<String>): Boolean =
        viewId != null && fragments.any { viewId.contains(it) }
}

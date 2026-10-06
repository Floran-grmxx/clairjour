package com.clairjour.app.shield

/**
 * Every Instagram-specific selector lives here, so that an Instagram update only requires
 * editing this file. Instagram calls Reels "clips" internally and Stories "reels".
 *
 * Each selector is a list of fragments: a view id matches when it contains one of them.
 */
object InstagramSelectors {

    const val PACKAGE_NAME = "com.instagram.android"
    const val VIEW_ID_PREFIX = "$PACKAGE_NAME:id/"

    // Bottom navigation tabs
    val homeTabIds = listOf("feed_tab")
    val searchTabIds = listOf("search_tab")
    val reelsTabIds = listOf("clips_tab")
    val profileTabIds = listOf("profile_tab")

    // Fallback when the tab ids change: content descriptions of the bottom tabs (FR + EN).
    val homeTabLabels = setOf("accueil", "home")
    val searchTabLabels = setOf("rechercher et explorer", "search and explore", "rechercher", "search")
    val reelsTabLabels = setOf("reels")
    val profileTabLabels = setOf("profil", "profile")

    /** Bottom tabs are only trusted in the lower part of the screen (ratio of screen height). */
    const val BOTTOM_BAR_MIN_TOP_RATIO = 0.80

    // Full-screen Reels player (Reels tab, and any reel opened from feed, DMs or profiles).
    val reelViewerIds = listOf("clips_viewer_view_pager", "clips_viewer_pager")

    // Author name inside a reel page: used to notice the swipe to the next reel.
    val reelAuthorIds = listOf("clips_author_username", "clips_author_info", "username")

    // Stories player.
    val storyViewerIds = listOf("reel_viewer_root", "reel_viewer_media_container", "reel_viewer_content")

    // Direct message conversation.
    val directThreadIds = listOf(
        "direct_thread_container",
        "thread_root",
        "row_thread_composer",
        "thread_composer",
        "direct_thread_toolbar"
    )

    // Profile page.
    val profileIds = listOf("profile_header", "row_profile_header")

    // Explore grid shown on the search tab before typing.
    val exploreGridIds = listOf("explore_grid", "explore_recycler", "explore_container")

    // Home feed list and the stories tray at its top.
    val homeFeedListIds = listOf("main_feed_list", "feed_recycler_view")
    val storiesTrayIds = listOf("reels_tray_container", "stories_tray")

    // Tabs shown after submitting a search (FR + EN labels, compared lowercase).
    val searchAccountsTabLabels = setOf("comptes", "accounts")
    val searchOtherTabLabels = setOf(
        "pour vous", "for you", "top", "meilleurs résultats",
        "reels", "audio", "tags", "hashtags", "lieux", "places", "publications", "posts"
    )

    fun matches(viewId: String?, fragments: List<String>): Boolean =
        viewId != null && fragments.any { viewId.contains(it) }
}

package com.clairjour.app.shield

/**
 * One blocking rule the user can switch on or off. [key] is persisted: never rename it.
 */
enum class ShieldRule(val key: String, val enabledByDefault: Boolean) {
    /** Opening the Reels tab sends the user back to the home tab. */
    REELS_TAB("reels_tab", true),

    /** A reel received in a conversation plays, swiping to the next one goes back. */
    DIRECT_SINGLE_REEL("direct_single_reel", true),

    /** Reels opened from the home feed, notifications or anywhere else go back. */
    OTHER_REELS("other_reels", true),

    /** Reels opened from a profile go back. */
    PROFILE_REELS("profile_reels", false),

    /** The Explore grid of the search tab is covered; the search bar stays usable. */
    EXPLORE_GRID("explore_grid", true),

    /** Submitted searches are forced onto the "Accounts" result tab. */
    SEARCH_ACCOUNTS_ONLY("search_accounts_only", true),

    /** The home feed is covered; the stories tray stays visible. */
    HOME_FEED("home_feed", false),

    /** Stories go back. */
    STORIES("stories", false);

    companion object {
        val defaults: Set<ShieldRule> = entries.filter { it.enabledByDefault }.toSet()
    }
}

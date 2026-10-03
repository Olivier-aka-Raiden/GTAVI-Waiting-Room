package com.gtavi.news;

import java.util.Set;

/**
 * The product vocabulary shared by extraction, grouping and the public catalog. Collectibles,
 * merchandise and music follow the same rules and differ only in these labels, so the sets live here
 * instead of being repeated per feature.
 */
public final class ProductCategories {
    private ProductCategories() {}

    /** Categories an announcement may report for one of its products. */
    public static final Set<String> ALL =
        Set.of("COLLECTIBLE", "VINYL", "CD", "ALBUM", "MERCHANDISE", "GAME");

    /** Product categories whose cards are music releases rather than collectibles or merchandise. */
    public static final Set<String> MUSIC = Set.of("MUSIC", "ALBUM", "VINYL", "CD");

    public static boolean music(String category) {
        return MUSIC.contains(category);
    }
}

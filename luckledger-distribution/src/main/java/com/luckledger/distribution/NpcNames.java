package com.luckledger.distribution;

import java.util.List;

/**
 * Curated pools of NPC storefront names for the two seeded games (DESIGN.md §3.5 — dealers are NPC
 * storefronts, each with a name). These read as the proprietor/stall a player walks up to, rather than
 * the placeholder "Dealer N". A {@link DealerRegistry} is constructed with one of these pools so each
 * game's dealers are themed and distinct.
 */
public final class NpcNames {

    /** Auspicious, heavenly stalls for Celestial Fortune. */
    public static final List<String> CELESTIAL = List.of(
            "Jade Lantern Pavilion",
            "Madam Quan's Fortune Stall",
            "The Golden Crane Kiosk",
            "Heavenly Tally House",
            "Master Bo's Lucky Counter",
            "Cloud Terrace Emporium",
            "The Vermilion Gate Stand",
            "Auntie Hua's Blessing Booth");

    /** Talisman and warding stalls for Demon Seal. */
    public static final List<String> DEMON = List.of(
            "Crossroads Talisman Stall",
            "Old Mei's Seal Shop",
            "The Ashen Ward Booth",
            "Brother Shen's Charm Cart",
            "Hollow Lantern Stand",
            "Widow Tang's Warding House",
            "Graveyard Gate Kiosk",
            "The Cinnabar Seal Counter");

    /** Neutral fallback pool for any game that does not supply its own themed names. */
    public static final List<String> DEFAULT = List.of(
            "Corner Fortune Stall",
            "The Lucky Counter",
            "Riverside Kiosk",
            "Market Square Booth",
            "Old Town Tally House");

    private NpcNames() {}
}

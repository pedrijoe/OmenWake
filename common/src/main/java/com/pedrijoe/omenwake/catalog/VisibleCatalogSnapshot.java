package com.pedrijoe.omenwake.catalog;

import java.util.List;

public record VisibleCatalogSnapshot(
        int protocolVersion,
        long revision,
        int totalEntries,
        int discoveredCount,
        int completedCount,
        int lifetimePoints,
        int availablePoints,
        List<VisibleEncounterEntry> entries
) {
    public static final int PROTOCOL_VERSION = 2;
    public static final int MAX_ENTRIES = 128;
    public static final VisibleCatalogSnapshot EMPTY = new VisibleCatalogSnapshot(
            PROTOCOL_VERSION, 0, 0, 0, 0, 0, 0, List.of());

    public VisibleCatalogSnapshot {
        entries = List.copyOf(entries);
        if (protocolVersion != PROTOCOL_VERSION || revision < 0 || totalEntries < 0
                || totalEntries > MAX_ENTRIES || discoveredCount < 0 || completedCount < 0
                || discoveredCount > totalEntries || completedCount > discoveredCount
                || lifetimePoints < 0 || availablePoints < 0 || availablePoints > lifetimePoints
                || entries.size() != totalEntries) {
            throw new IllegalArgumentException("Visible catalog snapshot is invalid");
        }
    }
}

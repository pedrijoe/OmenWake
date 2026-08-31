package com.pedrijoe.omenwake.catalog;

import java.util.List;

public sealed interface VisibleEncounterEntry permits VisibleEncounterEntry.Unknown, VisibleEncounterEntry.Known {
    record Unknown(int slot) implements VisibleEncounterEntry {}

    record Known(
            String encounterId,
            DiscoveryState state,
            String titleKey,
            String descriptionKey,
            String objectiveKey,
            String triggerKey,
            int activationChancePercent,
            int participationCount,
            int completionCount,
            int protectionCharges,
            List<String> completedVariantKeys
    ) implements VisibleEncounterEntry {
        public Known {
            completedVariantKeys = List.copyOf(completedVariantKeys);
            if (participationCount < 0 || completionCount < 0 || completionCount > participationCount
                        || protectionCharges < 0 || activationChancePercent < 0 || activationChancePercent > 100
                        || titleKey.length() > 256 || descriptionKey.length() > 256 || objectiveKey.length() > 256
                        || triggerKey.length() > 256 || encounterId.length() > 256
                    || completedVariantKeys.size() > 64) {
                throw new IllegalArgumentException("Visible encounter entry is invalid");
            }
        }
    }
}

package com.pedrijoe.omenwake.catalog;

import com.pedrijoe.omenwake.EncounterTestingMode;
import com.pedrijoe.omenwake.encounter.EncounterDefinitionEntry;
import com.pedrijoe.omenwake.encounter.EncounterDefinitionRepository;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CatalogProjectionService {
    private final EncounterDefinitionRepository definitions;
    private final PlayerEncounterDataRepository playerData;
    private final EncounterTestingMode testingMode;
    private final Map<UUID, Long> revisions = new HashMap<>();

    public CatalogProjectionService(EncounterDefinitionRepository definitions,
                                    PlayerEncounterDataRepository playerData) {
        this(definitions, playerData, new EncounterTestingMode());
    }

    public CatalogProjectionService(EncounterDefinitionRepository definitions,
                                    PlayerEncounterDataRepository playerData,
                                    EncounterTestingMode testingMode) {
        this.definitions = definitions;
        this.playerData = playerData;
        this.testingMode = testingMode;
    }

    public VisibleCatalogSnapshot projectFor(UUID ownerId) {
        PlayerEncounterData data = playerData.get(ownerId);
        List<EncounterDefinitionEntry> entries = definitions.allEntries();
        Map<Identifier, EncounterProgress> progress = data.encounters();
        int discovered = 0;
        int completed = 0;
        List<VisibleEncounterEntry> visible = new java.util.ArrayList<>();
        for (int slot = 0; slot < entries.size(); slot++) {
            EncounterDefinitionEntry entry = entries.get(slot);
            EncounterProgress current = progress.getOrDefault(entry.id(), EncounterProgress.EMPTY);
            DiscoveryState visibleState = testingMode.enabled() && current.state() == DiscoveryState.UNDISCOVERED
                    ? DiscoveryState.DISCOVERED : current.state();
            if (visibleState == DiscoveryState.UNDISCOVERED) {
                visible.add(new VisibleEncounterEntry.Unknown(slot));
                continue;
            }
            discovered++;
            if (visibleState == DiscoveryState.COMPLETED) {
                completed++;
            }
            visible.add(new VisibleEncounterEntry.Known(
                    entry.id().toString(), visibleState, entry.definition().catalogTitleKey(),
                    entry.definition().catalogDescriptionKey(),
                    "objective.omenwake." + entry.definition().objective().name().toLowerCase(java.util.Locale.ROOT),
                    "encounter.omenwake." + entry.id().getPath() + ".trigger",
                    Math.round(entry.definition().baseProbability() * 100.0F),
                    current.participationCount(), current.completionCount(), current.protectionCharges(),
                    current.completedVariants().stream().map(Identifier::toString).sorted().toList()));
        }
        long revision = revisions.merge(ownerId, 1L, Long::sum);
        return new VisibleCatalogSnapshot(VisibleCatalogSnapshot.PROTOCOL_VERSION, revision,
                visible.size(), discovered, completed, data.points().lifetimePoints(),
                data.points().availablePoints(), visible);
    }

    public String titleKeyFor(UUID ownerId, Identifier encounterId) {
        return definitions.allEntries().stream()
                .filter(entry -> entry.id().equals(encounterId))
                .map(entry -> entry.definition().catalogTitleKey())
                .findFirst()
                .orElse("screen.omenwake.catalog.unknown");
    }
}
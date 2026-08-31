package com.pedrijoe.omenwake;

import com.pedrijoe.omenwake.encounter.EncounterDefinitionRepository;
import com.pedrijoe.omenwake.encounter.EncounterFinishedNotifier;
import com.pedrijoe.omenwake.encounter.EncounterService;
import com.pedrijoe.omenwake.encounter.InMemoryCooldownService;
import com.pedrijoe.omenwake.encounter.NoOpEncounterFinishedNotifier;
import com.pedrijoe.omenwake.catalog.EncounterProgressionService;
import com.pedrijoe.omenwake.catalog.InMemoryPlayerEncounterDataRepository;
import com.pedrijoe.omenwake.catalog.PlayerEncounterDataRepository;
import com.pedrijoe.omenwake.catalog.CatalogProjectionService;
import com.pedrijoe.omenwake.encounter.ProtectionNotifier;
import com.pedrijoe.omenwake.trigger.TriggerDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Omenwake {
    public static final String MOD_ID = "omenwake";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Omenwake() {}

    public static OmenwakeServices createServices() {
        return createServices(new InMemoryPlayerEncounterDataRepository(),
                (player, titleKey, remainingCharges) -> {},
                new NoOpEncounterFinishedNotifier());
    }

    public static OmenwakeServices createServices(PlayerEncounterDataRepository playerDataRepository,
                                                  ProtectionNotifier protectionNotifier) {
        return createServices(playerDataRepository, protectionNotifier, new NoOpEncounterFinishedNotifier());
    }

    public static OmenwakeServices createServices(PlayerEncounterDataRepository playerDataRepository,
                                                  ProtectionNotifier protectionNotifier,
                                                  EncounterFinishedNotifier finishedNotifier) {
        EncounterDefinitionRepository encounterDefinitionRepository = new EncounterDefinitionRepository();
        EncounterProgressionService progressionService = new EncounterProgressionService(playerDataRepository);
        EncounterTestingMode testingMode = new EncounterTestingMode();
        CatalogProjectionService catalogProjectionService = new CatalogProjectionService(
            encounterDefinitionRepository, playerDataRepository, testingMode);
        EncounterService encounterService = new EncounterService(
            encounterDefinitionRepository, new InMemoryCooldownService(), progressionService,
                protectionNotifier, finishedNotifier, progressionService, testingMode);
        TriggerDispatcher triggerDispatcher = new TriggerDispatcher(encounterService);
        LOGGER.info("Omenwake services initialized");
        return new OmenwakeServices(triggerDispatcher, encounterService,
            encounterDefinitionRepository, progressionService, catalogProjectionService, testingMode);
    }
}

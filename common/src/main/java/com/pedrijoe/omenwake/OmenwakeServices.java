package com.pedrijoe.omenwake;

import com.pedrijoe.omenwake.encounter.EncounterDefinitionRepository;
import com.pedrijoe.omenwake.encounter.EncounterService;
import com.pedrijoe.omenwake.catalog.EncounterProgressionService;
import com.pedrijoe.omenwake.catalog.CatalogProjectionService;
import com.pedrijoe.omenwake.trigger.TriggerDispatcher;

public record OmenwakeServices(
        TriggerDispatcher triggerDispatcher,
        EncounterService encounterService,
        EncounterDefinitionRepository encounterDefinitionRepository,
        EncounterProgressionService progressionService,
        CatalogProjectionService catalogProjectionService,
        EncounterTestingMode testingMode
) {
        public void tick() {
                encounterService.tick();
        }
}
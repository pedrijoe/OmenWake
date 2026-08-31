package com.pedrijoe.omenwake.trigger;

import com.pedrijoe.omenwake.encounter.EncounterService;

import java.util.Objects;

public final class TriggerDispatcher {
    private final EncounterService encounterService;

    public TriggerDispatcher(EncounterService encounterService) {
        this.encounterService = Objects.requireNonNull(encounterService);
    }

    public void dispatch(TriggerContext context) {
        Objects.requireNonNull(context);
        encounterService.evaluate(context);
    }
}
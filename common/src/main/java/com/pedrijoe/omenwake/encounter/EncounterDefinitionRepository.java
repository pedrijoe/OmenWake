package com.pedrijoe.omenwake.encounter;

import com.pedrijoe.omenwake.trigger.TriggerType;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holds the currently loaded encounter definitions, indexed by trigger type.
 * Reloading swaps this state atomically so active {@link EncounterInstance}s are never rewritten.
 */
public final class EncounterDefinitionRepository {
    private volatile Map<Identifier, EncounterDefinition> definitionsById = Map.of();
    private volatile Map<TriggerType, List<EncounterDefinitionEntry>> definitionsByTrigger = Map.of();

    public void reload(Map<Identifier, EncounterDefinition> loaded) {
        definitionsById = Map.copyOf(loaded);
        definitionsByTrigger = loaded.entrySet().stream()
            .map(entry -> new EncounterDefinitionEntry(entry.getKey(), entry.getValue()))
            .collect(Collectors.groupingBy(entry -> entry.definition().trigger()));
        definitionsByTrigger.replaceAll((trigger, entries) -> entries.stream()
            .sorted(java.util.Comparator.comparing(entry -> entry.id().toString()))
            .toList());
    }

    public List<EncounterDefinition> byTrigger(TriggerType triggerType) {
        return definitionsByTrigger.getOrDefault(triggerType, List.of()).stream()
            .map(EncounterDefinitionEntry::definition)
            .toList();
    }

    public List<EncounterDefinitionEntry> entriesByTrigger(TriggerType triggerType) {
        return definitionsByTrigger.getOrDefault(triggerType, List.of());
    }

    public List<EncounterDefinitionEntry> allEntries() {
        return definitionsById.entrySet().stream()
                .map(entry -> new EncounterDefinitionEntry(entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparing(entry -> entry.id().toString()))
                .toList();
    }

    public int size() {
        return definitionsById.size();
    }
}

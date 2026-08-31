package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class EncounterDefinitionReloadListener extends SimpleJsonResourceReloadListener<EncounterDefinition> {
    private static final Logger LOGGER = LoggerFactory.getLogger("omenwake");
    private final EncounterDefinitionRepository repository;
    private final Runnable afterReload;

    public EncounterDefinitionReloadListener(EncounterDefinitionRepository repository) {
        this(repository, () -> {
        });
    }

    public EncounterDefinitionReloadListener(EncounterDefinitionRepository repository, Runnable afterReload) {
        super(EncounterDefinition.CODEC, FileToIdConverter.json("encounters"));
        this.repository = repository;
        this.afterReload = afterReload;
    }

    @Override
    protected void apply(Map<Identifier, EncounterDefinition> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        repository.reload(loaded);
        LOGGER.info("Loaded {} encounter definitions", loaded.size());
        afterReload.run();
    }
}

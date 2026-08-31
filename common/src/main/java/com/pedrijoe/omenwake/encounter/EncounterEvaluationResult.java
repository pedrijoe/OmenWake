package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record EncounterEvaluationResult(
        EvaluationOutcome outcome,
    ActivationOutcome activationOutcome,
        Optional<Identifier> encounterId,
        Optional<UUID> instanceId,
        List<CandidateEvaluation> candidates
) {
    public EncounterEvaluationResult {
        candidates = List.copyOf(candidates);
    }
}
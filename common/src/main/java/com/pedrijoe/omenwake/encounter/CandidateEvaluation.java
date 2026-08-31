package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

public record CandidateEvaluation(
        Identifier encounterId,
        CandidateStatus status,
        RejectionReason rejectionReason,
        double effectiveProbability,
        double randomRoll,
        long cooldownRemainingTicks
) {}
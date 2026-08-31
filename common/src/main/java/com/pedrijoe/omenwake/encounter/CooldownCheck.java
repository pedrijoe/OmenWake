package com.pedrijoe.omenwake.encounter;

public record CooldownCheck(boolean allowed, RejectionReason rejectionReason, long remainingTicks) {}
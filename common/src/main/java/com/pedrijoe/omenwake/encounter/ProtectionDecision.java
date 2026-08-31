package com.pedrijoe.omenwake.encounter;

public sealed interface ProtectionDecision permits ProtectionDecision.NotProtected, ProtectionDecision.Consumed {
    record NotProtected() implements ProtectionDecision {}

    record Consumed(int remainingCharges) implements ProtectionDecision {}
}
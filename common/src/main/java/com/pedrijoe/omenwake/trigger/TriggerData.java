package com.pedrijoe.omenwake.trigger;

public record TriggerData(boolean debug, int amount, TriggerProvenance provenance) {
	public TriggerData {
		if (amount <= 0) {
			throw new IllegalArgumentException("Trigger amount must be positive");
		}
		if (provenance == null) {
			throw new IllegalArgumentException("Trigger provenance must be present");
		}
		if (debug != (provenance == TriggerProvenance.DEBUG)) {
			throw new IllegalArgumentException("Debug flag and trigger provenance must agree");
		}
	}

	public TriggerData(boolean debug) {
		this(debug, 1, debug ? TriggerProvenance.DEBUG : TriggerProvenance.NORMAL);
	}

	public static TriggerData normal(int amount) {
		return new TriggerData(false, amount, TriggerProvenance.NORMAL);
	}
}
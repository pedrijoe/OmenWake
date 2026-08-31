package com.pedrijoe.omenwake.encounter;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class InMemoryCooldownService implements CooldownService {
    private final Map<UUID, Long> globalExpiry = new HashMap<>();
    private final Map<CooldownKey, Long> encounterExpiry = new HashMap<>();

    @Override
    public CooldownCheck check(UUID playerId, Identifier encounterId, long now) {
        long globalRemaining = remaining(globalExpiry, playerId, now);
        if (globalRemaining > 0) {
            return new CooldownCheck(false, RejectionReason.PLAYER_GLOBAL_COOLDOWN, globalRemaining);
        }
        long encounterRemaining = remaining(encounterExpiry, new CooldownKey(playerId, encounterId), now);
        if (encounterRemaining > 0) {
            return new CooldownCheck(false, RejectionReason.PLAYER_ENCOUNTER_COOLDOWN, encounterRemaining);
        }
        return new CooldownCheck(true, null, 0);
    }

    @Override
    public void commit(UUID playerId, Identifier encounterId, long globalDurationTicks,
                       long encounterDurationTicks, long now) {
        globalExpiry.put(playerId, now + globalDurationTicks);
        encounterExpiry.put(new CooldownKey(playerId, encounterId), now + encounterDurationTicks);
    }

    @Override
    public void clear(UUID playerId) {
        globalExpiry.remove(playerId);
        Iterator<CooldownKey> keys = encounterExpiry.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().playerId().equals(playerId)) {
                keys.remove();
            }
        }
    }

    private static <K> long remaining(Map<K, Long> expiries, K key, long now) {
        Long expiry = expiries.get(key);
        if (expiry == null || expiry <= now) {
            if (expiry != null) {
                expiries.remove(key);
            }
            return 0;
        }
        return expiry - now;
    }

    private record CooldownKey(UUID playerId, Identifier encounterId) {}
}
package com.pedrijoe.omenwake.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class EncounterSpawnPlanner {
    private static final int MIN_HORIZONTAL_DISTANCE = 4;
    private static final int MAX_HORIZONTAL_DISTANCE = 20;
    private static final int VERTICAL_SEARCH_RANGE = 12;

    public List<BlockPos> findGroundPositions(ServerLevel level, BlockPos origin, int requiredCount) {
        List<BlockPos> positions = new ArrayList<>(requiredCount);
        for (int distance = MIN_HORIZONTAL_DISTANCE; distance <= MAX_HORIZONTAL_DISTANCE; distance += 2) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != distance) {
                        continue;
                    }
                    for (int yOffset = VERTICAL_SEARCH_RANGE; yOffset >= -VERTICAL_SEARCH_RANGE; yOffset--) {
                        BlockPos candidate = origin.offset(x, yOffset, z);
                        if (isSafeGroundPosition(level, candidate)) {
                            positions.add(candidate);
                            break;
                        }
                    }
                    if (positions.size() >= requiredCount) {
                        return List.copyOf(positions);
                    }
                }
            }
        }
        return List.of();
    }

    private static boolean isSafeGroundPosition(ServerLevel level, BlockPos position) {
        return !level.getBlockState(position.below()).isAir()
                && level.getBlockState(position).isAir()
                && level.getBlockState(position.above()).isAir();
    }
}
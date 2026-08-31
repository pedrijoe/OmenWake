package com.pedrijoe.omenwake.catalog;

import java.util.UUID;

@FunctionalInterface
public interface ProgressionChangeListener {
    void onChanged(UUID ownerId);
}
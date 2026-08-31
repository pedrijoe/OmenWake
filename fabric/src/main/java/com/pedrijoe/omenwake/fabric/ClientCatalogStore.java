package com.pedrijoe.omenwake.fabric;

import com.pedrijoe.omenwake.catalog.VisibleCatalogSnapshot;

public final class ClientCatalogStore {
    private static final ClientCatalogStore INSTANCE = new ClientCatalogStore();
    private volatile VisibleCatalogSnapshot snapshot = VisibleCatalogSnapshot.EMPTY;

    private ClientCatalogStore() {
    }

    public static ClientCatalogStore getInstance() {
        return INSTANCE;
    }

    public void replace(VisibleCatalogSnapshot incoming) {
        if (incoming.revision() >= snapshot.revision()) {
            snapshot = incoming;
        }
    }

    public VisibleCatalogSnapshot snapshot() {
        return snapshot;
    }

    public void clear() {
        snapshot = VisibleCatalogSnapshot.EMPTY;
    }
}

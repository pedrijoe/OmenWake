package com.pedrijoe.omenwake.neoforge;
import com.pedrijoe.omenwake.Omenwake;
import com.pedrijoe.omenwake.OmenwakeServices;
import net.neoforged.fml.common.Mod;
@Mod(Omenwake.MOD_ID)
public final class OmenwakeNeoForge {
    private final OmenwakeServices services;

    public OmenwakeNeoForge() {
        services = Omenwake.createServices();
    }
}

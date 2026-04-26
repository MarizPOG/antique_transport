package me.mariz.antique_transport.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class ModCompat {
    public static final boolean CREATE = FabricLoader.getInstance().isModLoaded("create");
    public static final boolean SABLE = FabricLoader.getInstance().isModLoaded("sable");
    public static final boolean AERONAUTICS = FabricLoader.getInstance().isModLoaded("create-aeronautics");
    private ModCompat() {
    }
}
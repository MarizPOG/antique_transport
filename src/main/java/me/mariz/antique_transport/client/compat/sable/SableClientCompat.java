package me.mariz.antique_transport.client.compat.sable;

import me.mariz.antique_transport.server.ShipNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Comparator;
import java.util.UUID;

public final class SableClientCompat {
    private SableClientCompat() {
    }

    public static void init() {
        ShipNetworking.register();
        registerShipTextureReload();
        registerCommands();
    }

    private static void registerShipTextureReload() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.tryBuild("antique_transport", "shiptexturesloader");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        ShipCache.SHIP_TEXTURES.clear();

                        manager.listResources("textures/gui/ship_textures", path -> path.toString().endsWith(".png"))
                                .keySet()
                                .stream()
                                .filter(rl -> rl.getNamespace().equals("antique_transport"))
                                .sorted(Comparator.comparing(ResourceLocation::getPath))
                                .forEach(ShipCache.SHIP_TEXTURES::add);
                    }
                });
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("antique_transport");

            root.then(ClientCommandManager.literal("resetships").executes(ctx -> {
                ShipCache.hiddenShips.clear();
                ShipCache.save();
                ctx.getSource().sendFeedback(Component.literal("Antique Transport: All hidden ships restored!"));
                return 1;
            }));

            root.then(ClientCommandManager.literal("markship").executes(ctx -> {
                var player = ctx.getSource().getPlayer();
                if (player == null) {
                    return 0;
                }

                var level = Minecraft.getInstance().level;
                if (level == null) {
                    return 0;
                }

                UUID nearestId = SableShipLocator.findNearestShipId(player, level);
                if (nearestId == null) {
                    ctx.getSource().sendFeedback(Component.literal("Antique Transport: No ship found nearby!"));
                    return 0;
                }

                Minecraft.getInstance().execute(() -> {
                    var current = Minecraft.getInstance().screen;
                    if (current instanceof folk.sisby.antique_atlas.gui.AtlasScreen atlasScreen) {
                        atlasScreen.addChild(new ShipNameModal(nearestId));
                    } else {
                        ShipCache.pendingModalShipId = nearestId;
                        var screen = new folk.sisby.antique_atlas.gui.AtlasScreen();
                        screen.prepareToOpen();
                        Minecraft.getInstance().setScreen(screen);
                    }
                });

                return 1;
            }));

            dispatcher.register(root);
        });
    }
}
package me.mariz.antique_transport.client.compat.create;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StationLandmarkSync {
    private StationLandmarkSync() {
    }

    public static void sync(boolean show) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }

        UUID owner = WorldLandmarks.GLOBAL;

        // Remove all old station landmarks from every known graph dimension.
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            ResourceKey<Level> graphDim = graph.getNodes().stream()
                    .findFirst()
                    .map(loc -> loc.dimension)
                    .orElse(null);

            if (graphDim == null) {
                continue;
            }

            WorldSummary summary = SurveyorClient.tryGetSummary(graphDim);
            if (summary == null || summary.landmarks() == null) {
                continue;
            }

            List<ResourceLocation> toRemove = new ArrayList<>();
            assert summary.landmarks() != null;
            summary.landmarks().keySet(null).get(owner).forEach(id -> {
                if ("antique_transport".equals(id.getNamespace()) && id.getPath().startsWith("station/")) {
                    toRemove.add(id);
                }
            });

            Multimap<UUID, ResourceLocation> removed = HashMultimap.create();
            toRemove.forEach(id -> removed.put(owner, id));
            WorldAtlasData.getOrCreate(graphDim).onLandmarksRemoved(summary, removed);
        }

        if (!show) {
            return;
        }

        // Rebuild current station landmarks from Create graphs.
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            ResourceKey<Level> graphDim = graph.getNodes().stream()
                    .findFirst()
                    .map(loc -> loc.dimension)
                    .orElse(null);

            if (graphDim == null) {
                continue;
            }

            WorldSummary summary = SurveyorClient.tryGetSummary(graphDim);
            if (summary == null || summary.landmarks() == null) {
                continue;
            }

            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station.blockEntityPos == null) {
                    continue;
                }

                BlockPos pos = station.blockEntityPos;
                ResourceLocation id = ResourceLocation.tryBuild(
                        "antique_transport",
                        "station/white/" + pos.getX() + "/" + pos.getZ()
                );

                Landmark landmark = Landmark.create(owner, id, builder -> builder
                        .add(LandmarkComponentTypes.POS, pos)
                        .add(LandmarkComponentTypes.NAME, Component.literal(station.name))
                        .add(LandmarkComponentTypes.COLOR, DyeColor.WHITE.getTextureDiffuseColor())
                        .add(LandmarkComponentTypes.STACK, new ItemStack(
                                BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track_station"))
                        ))
                );

                assert summary.landmarks() != null;
                summary.landmarks().putLocal(landmark);

                Multimap<UUID, ResourceLocation> added = HashMultimap.create();
                added.put(owner, id);
                WorldAtlasData.getOrCreate(graphDim).onLandmarksAdded(summary, added);
            }
        }
    }
}
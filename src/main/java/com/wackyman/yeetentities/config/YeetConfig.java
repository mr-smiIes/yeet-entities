package com.wackyman.yeetentities.config;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class YeetConfig {
    public double throwSpeed = 5.5;
    public double pickupRange = 6.0;
    public boolean allowPlayers = true;
    public boolean killOnBlockHit = true;
    public boolean killTargetOnEntityHit = true;
    public boolean killThrownEntityOnEntityHit = true;
    public boolean preserveNoGravity = false;
    public boolean playSounds = true;
    public int impactParticles = 20;

    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("yeet_entities.json");
    private static YeetConfig INSTANCE;

    public static YeetConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                INSTANCE = new GsonBuilder().setPrettyPrinting().create().fromJson(json, YeetConfig.class);
                if (INSTANCE == null) INSTANCE = new YeetConfig();
            } else {
                INSTANCE = new YeetConfig();
            }
            save();
        } catch (Exception e) {
            INSTANCE = new YeetConfig();
            System.err.println("[Yeet Entities] Could not load config: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.writeString(PATH, new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE));
        } catch (IOException e) {
            System.err.println("[Yeet Entities] Could not save config: " + e.getMessage());
        }
    }
}

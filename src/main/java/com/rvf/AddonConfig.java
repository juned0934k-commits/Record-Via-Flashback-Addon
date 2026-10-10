package com.rvf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings, stored in config/rvf.json. Edit the file and restart the game to change them.
 * "record" options decide what is written into new recordings, "show" options decide what is drawn in replays.
 *
 * Everything is on by default, because a recording can't be changed afterwards: whatever was off while recording
 * can never be shown in the replay. Chat typing hides the arguments of slash commands (see ReplayChatTyping.redact).
 */
public class AddonConfig {

    public boolean recordInventory = true;
    public boolean recordPerspective = true;
    public boolean recordEffects = true;
    public boolean recordSpyglass = true;
    public boolean recordChatTyping = true;

    public boolean showInventory = true;
    public boolean showEffects = true;
    public boolean showSpyglass = true;
    public boolean showChatTyping = true;
    public boolean autoPerspective = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AddonConfig instance = new AddonConfig();

    public static AddonConfig get() {
        return instance;
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("rvf.json");
        try {
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            RVF.LOGGER.error("Failed to write {}", path, e);
        }
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("rvf.json");

        AddonConfig loaded = null;
        if (Files.exists(path)) {
            try {
                loaded = GSON.fromJson(Files.readString(path), AddonConfig.class);
            } catch (Exception e) {
                RVF.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance = loaded != null ? loaded : new AddonConfig();

        try {
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            RVF.LOGGER.error("Failed to write {}", path, e);
        }
    }

}

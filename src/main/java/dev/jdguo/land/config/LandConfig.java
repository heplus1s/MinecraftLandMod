package dev.jdguo.land.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.jdguo.land.LandMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/landmod.json}. A value of 0 or less means a player can claim
 * any number of chunks. The file is only created when it is missing, so extra
 * keys added by a later version are left in place.
 */
public final class LandConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MAX = 32;

    private static Path path;
    private static long seenMillis = Long.MIN_VALUE;
    private static int maxClaims = DEFAULT_MAX;

    private LandConfig() {
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve("landmod.json");
        if (Files.notExists(path)) {
            writeDefault();
        }
        read();
    }

    public static int maxClaimsPerPlayer() {
        refresh();
        return maxClaims;
    }

    private static void refresh() {
        if (path == null || Files.notExists(path)) {
            return;
        }
        try {
            long millis = Files.getLastModifiedTime(path).toMillis();
            if (millis != seenMillis) {
                read();
            }
        } catch (IOException exception) {
            LandMod.LOGGER.warn("Could not check landmod config", exception);
        }
    }

    private static void read() {
        try (Reader reader = Files.newBufferedReader(path)) {
            FileModel model = GSON.fromJson(reader, FileModel.class);
            if (model == null) {
                maxClaims = DEFAULT_MAX;
            } else {
                maxClaims = model.maxClaimsPerPlayer;
            }
            seenMillis = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | JsonSyntaxException exception) {
            LandMod.LOGGER.warn("Could not read config/landmod.json, using {} claims per player", maxClaims, exception);
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new FileModel(), writer);
            }
        } catch (IOException exception) {
            LandMod.LOGGER.warn("Could not write default landmod config", exception);
        }
    }

    private static final class FileModel {
        int maxClaimsPerPlayer = DEFAULT_MAX;
    }
}

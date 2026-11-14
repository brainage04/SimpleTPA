package io.github.brainage04.simpletpa.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.brainage04.brainagelib.storage.JsonFile;
import io.github.brainage04.simpletpa.SimpleTPA;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Whether each player's TP requests wait while the drop below the player they teleport to would
 * hurt them. On for everyone until they turn it off; a file that fails to load is never
 * overwritten, and nothing is saved unless {@link #load} ran in this server run, as for {@link
 * InstantTpaWhitelist}.
 */
public final class SafetyPreference {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<Map<String, Boolean>> FILE_TYPE = new TypeToken<>() {};
    private static final Map<UUID, Boolean> REFUSES_DANGEROUS_DROP = new HashMap<>();
    private static JsonFile<Map<String, Boolean>> file;

    private SafetyPreference() {}

    public static synchronized void load(MinecraftServer server) {
        file = safetyFile(server);
        REFUSES_DANGEROUS_DROP.clear();

        if (!(file.load()
                        instanceof
                        JsonFile.LoadResult.Loaded<Map<String, Boolean>>(
                                Map<String, Boolean> saved))
                || saved == null) {
            return;
        }

        for (Map.Entry<String, Boolean> entry : saved.entrySet()) {
            UUID player = parseUuid(entry.getKey());
            if (player != null && entry.getValue() != null) {
                REFUSES_DANGEROUS_DROP.put(player, entry.getValue());
            }
        }
    }

    public static synchronized boolean save() {
        if (file == null) {
            return false;
        }

        Map<String, Boolean> saved = new TreeMap<>();
        REFUSES_DANGEROUS_DROP.forEach((player, refuses) -> saved.put(player.toString(), refuses));
        return file.save(saved).saved();
    }

    /** Forgets the loaded file and choices, so nothing is saved until the next {@link #load}. */
    public static synchronized void unload() {
        file = null;
        REFUSES_DANGEROUS_DROP.clear();
    }

    private static JsonFile<Map<String, Boolean>> safetyFile(MinecraftServer server) {
        return JsonFile.builder(
                        server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-safety.json"),
                        GSON,
                        FILE_TYPE)
                .description("SimpleTPA safety preferences")
                .logger(SimpleTPA.LOGGER)
                .build();
    }

    /** Whether {@code player}'s requests are refused while the drop below the target would hurt. */
    public static synchronized boolean refusesDangerousDrop(ServerPlayer player) {
        return REFUSES_DANGEROUS_DROP.getOrDefault(player.getUUID(), true);
    }

    public static synchronized boolean setRefusesDangerousDrop(
            ServerPlayer player, boolean refuses) {
        Boolean previous = REFUSES_DANGEROUS_DROP.put(player.getUUID(), refuses);
        return (previous == null || previous) != refuses;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

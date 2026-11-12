package io.github.brainage04.simpletpa.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.brainage04.brainagelib.storage.JsonFile;
import io.github.brainage04.simpletpa.SimpleTPA;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Accept-all is an entry whose uuid is {@value #ACCEPT_ALL_MARKER}. Files written before accept-all
 * existed load unchanged, and older versions skip the marker entry because it is not a valid UUID.
 * A file that fails to load is never overwritten; changes then apply until the server stops.
 *
 * <p>Nothing is saved unless {@link #load} ran in this server run. Fabric fires SERVER_STOPPING at
 * the head of {@code stopServer}, which vanilla also calls when the start failed
 * (MinecraftServer.java:700-703,772-775), so a save there would write empty lists over the file.
 */
public final class InstantTpaWhitelist {
    public static final String ACCEPT_ALL_MARKER = "*";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<Map<String, List<Entry>>> FILE_TYPE = new TypeToken<>() {};
    private static final Map<UUID, Map<UUID, String>> WHITELISTS = new HashMap<>();
    private static final Set<UUID> ACCEPT_ALL = new HashSet<>();
    private static JsonFile<Map<String, List<Entry>>> file;

    private InstantTpaWhitelist() {}

    public static synchronized void load(MinecraftServer server) {
        file = whitelistFile(server);
        WHITELISTS.clear();
        ACCEPT_ALL.clear();

        if (!(file.load()
                instanceof
                JsonFile.LoadResult.Loaded<Map<String, List<Entry>>>(
                        Map<String, List<Entry>> saved))) {
            return;
        }

        for (Map.Entry<String, List<Entry>> ownerEntry : saved.entrySet()) {
            UUID ownerId = parseUuid(ownerEntry.getKey());

            if (ownerId == null || ownerEntry.getValue() == null) {
                continue;
            }

            Map<UUID, String> allowedPlayers = new HashMap<>();

            for (Entry allowedEntry : ownerEntry.getValue()) {
                if (allowedEntry == null) {
                    continue;
                }
                if (ACCEPT_ALL_MARKER.equals(allowedEntry.uuid)) {
                    ACCEPT_ALL.add(ownerId);
                    continue;
                }
                UUID allowedId = parseUuid(allowedEntry.uuid);

                if (allowedId != null) {
                    allowedPlayers.put(
                            allowedId,
                            allowedEntry.name == null ? allowedId.toString() : allowedEntry.name);
                }
            }

            if (!allowedPlayers.isEmpty()) {
                WHITELISTS.put(ownerId, allowedPlayers);
            }
        }
    }

    public static synchronized boolean save() {
        if (file == null) {
            return false;
        }

        Map<String, List<Entry>> saved = new TreeMap<>();

        for (UUID ownerId : ACCEPT_ALL) {
            List<Entry> entries = new ArrayList<>();
            entries.add(new Entry(ACCEPT_ALL_MARKER, "everyone"));
            saved.put(ownerId.toString(), entries);
        }

        for (Map.Entry<UUID, Map<UUID, String>> ownerEntry : WHITELISTS.entrySet()) {
            List<Entry> entries =
                    saved.computeIfAbsent(
                            ownerEntry.getKey().toString(), ignored -> new ArrayList<>());

            ownerEntry.getValue().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getValue().toLowerCase()))
                    .forEach(
                            entry ->
                                    entries.add(
                                            new Entry(
                                                    entry.getKey().toString(), entry.getValue())));
        }

        return file.save(saved).saved();
    }

    /** Forgets the loaded file and lists, so nothing is saved until the next {@link #load}. */
    public static synchronized void unload() {
        file = null;
        WHITELISTS.clear();
        ACCEPT_ALL.clear();
    }

    private static JsonFile<Map<String, List<Entry>>> whitelistFile(MinecraftServer server) {
        return JsonFile.builder(
                        server.getWorldPath(LevelResource.ROOT)
                                .resolve("simpletpa-instant-accept-whitelist.json"),
                        GSON,
                        FILE_TYPE)
                .description("SimpleTPA instant accept whitelist")
                .logger(SimpleTPA.LOGGER)
                .build();
    }

    public static synchronized boolean allows(ServerPlayer owner, ServerPlayer requester) {
        return ACCEPT_ALL.contains(owner.getUUID())
                || WHITELISTS
                        .getOrDefault(owner.getUUID(), Map.of())
                        .containsKey(requester.getUUID());
    }

    public static synchronized boolean acceptsAll(ServerPlayer owner) {
        return ACCEPT_ALL.contains(owner.getUUID());
    }

    public static synchronized void setAcceptAll(ServerPlayer owner, boolean acceptAll) {
        if (acceptAll) {
            ACCEPT_ALL.add(owner.getUUID());
        } else {
            ACCEPT_ALL.remove(owner.getUUID());
        }
    }

    public static synchronized boolean add(ServerPlayer owner, ServerPlayer requester) {
        return WHITELISTS
                        .computeIfAbsent(owner.getUUID(), ignored -> new HashMap<>())
                        .put(requester.getUUID(), requester.getScoreboardName())
                == null;
    }

    /**
     * Removes the entry of {@code owner}'s whitelist whose stored name or UUID is {@code player},
     * ignoring case, and returns its stored name; null when there is none.
     */
    public static synchronized @Nullable String remove(ServerPlayer owner, String player) {
        Map<UUID, String> allowedPlayers = WHITELISTS.get(owner.getUUID());

        if (allowedPlayers == null) {
            return null;
        }

        for (Iterator<Map.Entry<UUID, String>> entries = allowedPlayers.entrySet().iterator();
                entries.hasNext(); ) {
            Map.Entry<UUID, String> entry = entries.next();
            if (entry.getValue().equalsIgnoreCase(player)
                    || entry.getKey().toString().equalsIgnoreCase(player)) {
                entries.remove();
                if (allowedPlayers.isEmpty()) {
                    WHITELISTS.remove(owner.getUUID());
                }
                return entry.getValue();
            }
        }

        return null;
    }

    public static synchronized boolean clear(ServerPlayer owner) {
        return WHITELISTS.remove(owner.getUUID()) != null;
    }

    public static synchronized List<String> list(ServerPlayer owner) {
        return WHITELISTS.getOrDefault(owner.getUUID(), Map.of()).values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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

    private record Entry(String uuid, String name) {}
}

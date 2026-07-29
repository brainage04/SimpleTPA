package io.github.brainage04.simpletpa.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class InstantTpaWhitelist {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type FILE_TYPE = new TypeToken<Map<String, List<Entry>>>() {
	}.getType();
	private static final Map<UUID, Map<UUID, String>> WHITELISTS = new HashMap<>();
	private static Path filePath;

	private InstantTpaWhitelist() {
	}

	public static synchronized void load(MinecraftServer server) {
		filePath = server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-instant-accept-whitelist.json");
		WHITELISTS.clear();

		if (!Files.isRegularFile(filePath)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(filePath)) {
			Map<String, List<Entry>> saved = GSON.fromJson(reader, FILE_TYPE);

			if (saved == null) {
				return;
			}

			for (Map.Entry<String, List<Entry>> ownerEntry : saved.entrySet()) {
				UUID ownerId = parseUuid(ownerEntry.getKey());

				if (ownerId == null || ownerEntry.getValue() == null) {
					continue;
				}

				Map<UUID, String> allowedPlayers = new HashMap<>();

				for (Entry allowedEntry : ownerEntry.getValue()) {
					UUID allowedId = parseUuid(allowedEntry.uuid);

					if (allowedId != null) {
						allowedPlayers.put(allowedId, allowedEntry.name == null ? allowedId.toString() : allowedEntry.name);
					}
				}

				if (!allowedPlayers.isEmpty()) {
					WHITELISTS.put(ownerId, allowedPlayers);
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load SimpleTPA instant accept whitelist", exception);
		}
	}

	public static synchronized void save(MinecraftServer server) {
		if (filePath == null) {
			filePath = server.getWorldPath(LevelResource.ROOT).resolve("simpletpa-instant-accept-whitelist.json");
		}

		Map<String, List<Entry>> saved = new TreeMap<>();

		for (Map.Entry<UUID, Map<UUID, String>> ownerEntry : WHITELISTS.entrySet()) {
			List<Entry> entries = new ArrayList<>();

			ownerEntry.getValue().entrySet().stream()
					.sorted(Comparator.comparing(entry -> entry.getValue().toLowerCase()))
					.forEach(entry -> entries.add(new Entry(entry.getKey().toString(), entry.getValue())));

			if (!entries.isEmpty()) {
				saved.put(ownerEntry.getKey().toString(), entries);
			}
		}

		try {
			Files.createDirectories(filePath.getParent());

			try (Writer writer = Files.newBufferedWriter(filePath)) {
				GSON.toJson(saved, FILE_TYPE, writer);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to save SimpleTPA instant accept whitelist", exception);
		}
	}

	public static synchronized boolean allows(ServerPlayer owner, ServerPlayer requester) {
		return WHITELISTS.getOrDefault(owner.getUUID(), Map.of()).containsKey(requester.getUUID());
	}

	public static synchronized boolean add(ServerPlayer owner, ServerPlayer requester) {
		return WHITELISTS.computeIfAbsent(owner.getUUID(), ignored -> new HashMap<>())
				.put(requester.getUUID(), requester.getScoreboardName()) == null;
	}

	public static synchronized boolean remove(ServerPlayer owner, ServerPlayer requester) {
		Map<UUID, String> allowedPlayers = WHITELISTS.get(owner.getUUID());

		if (allowedPlayers == null) {
			return false;
		}

		boolean removed = allowedPlayers.remove(requester.getUUID()) != null;

		if (allowedPlayers.isEmpty()) {
			WHITELISTS.remove(owner.getUUID());
		}

		return removed;
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

	private record Entry(String uuid, String name) {
	}
}

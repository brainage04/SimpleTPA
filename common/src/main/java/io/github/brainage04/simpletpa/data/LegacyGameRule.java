package io.github.brainage04.simpletpa.data;

import io.github.brainage04.simpletpa.SimpleTPA;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Carries a world's value of {@code simpletpa:allow_instant_tpa_accepting}, the name of {@code
 * simpletpa:auto_accept} up to v1.1.0, over to the current rule.
 *
 * <p>Vanilla decodes saved game rules by registry name and drops a name it does not know
 * (GameRuleMap.java:19-20, SavedDataStorage.java:93-94), and the server's constructor then gives
 * the current rule its default (MinecraftServer.java:340, GameRules.java:99-110). The old value
 * survives only in the file, which the end of the server's start rewrites
 * (DedicatedServer.java:275, client IntegratedServer.java:107), so this runs before that, when the
 * server starts.
 */
public final class LegacyGameRule {
    public static final Identifier LEGACY_ID =
            Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, "allow_instant_tpa_accepting");

    private LegacyGameRule() {}

    public static void migrate(MinecraftServer server) {
        migrate(
                server.getDataStorage(),
                server.getWorldPath(LevelResource.DATA),
                server.getGameRules());
    }

    /**
     * Sets {@code rules}' auto-accept rule to the legacy value saved in {@code dataFolder}, when
     * the saved game rules have a legacy value and no current one.
     */
    public static void migrate(SavedDataStorage storage, Path dataFolder, GameRules rules) {
        Path file = GameRuleMap.TYPE.id().withSuffix(".dat").resolveAgainst(dataFolder);
        if (!Files.exists(file)) {
            return;
        }

        CompoundTag saved;
        try {
            saved =
                    storage.readTagFromDisk(
                            file,
                            GameRuleMap.TYPE.dataFixType(),
                            SharedConstants.getCurrentVersion().dataVersion().version());
        } catch (IOException exception) {
            SimpleTPA.LOGGER.error("Could not read {} to look for {}", file, LEGACY_ID, exception);
            return;
        }

        CompoundTag data = saved.getCompoundOrEmpty("data");
        Identifier current = BuiltInRegistries.GAME_RULE.getKey(SimpleTPA.AUTO_ACCEPT);
        Tag legacy = data.get(LEGACY_ID.toString());
        if (legacy == null || data.contains(current.toString())) {
            return;
        }

        SimpleTPA.AUTO_ACCEPT
                .valueCodec()
                .parse(NbtOps.INSTANCE, legacy)
                .ifSuccess(
                        value -> {
                            rules.set(SimpleTPA.AUTO_ACCEPT, value, null);
                            SimpleTPA.LOGGER.info(
                                    "Set {} to {}, the value this world saved for {}",
                                    current,
                                    value,
                                    LEGACY_ID);
                        })
                .ifError(
                        error ->
                                SimpleTPA.LOGGER.error(
                                        "Could not read {} in {}: {}",
                                        LEGACY_ID,
                                        file,
                                        error.message()));
    }
}

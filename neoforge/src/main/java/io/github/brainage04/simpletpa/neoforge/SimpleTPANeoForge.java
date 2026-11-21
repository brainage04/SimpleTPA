package io.github.brainage04.simpletpa.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.platform.ServerPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(SimpleTPA.MOD_ID)
public final class SimpleTPANeoForge implements ServerPlatform {
    public SimpleTPANeoForge(IEventBus modEventBus) {
        modEventBus.addListener(
                (RegisterEvent event) ->
                        event.register(
                                Registries.GAME_RULE,
                                helper -> SimpleTPA.RULES.registerAll(helper::register)));
        modEventBus.addListener((FMLCommonSetupEvent event) -> SimpleTPA.initialize(this));
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public void registerCommands(
            java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> registration.accept(event.getDispatcher()));
    }

    @Override
    public void registerEndServerTick(java.util.function.Consumer<MinecraftServer> callback) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (ServerTickEvent.Post event) -> callback.accept(event.getServer()));
    }

    @Override
    public void registerServerStarting(java.util.function.Consumer<MinecraftServer> callback) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (ServerAboutToStartEvent event) -> callback.accept(event.getServer()));
    }

    @Override
    public void registerServerStarted(java.util.function.Consumer<MinecraftServer> callback) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (ServerStartedEvent event) -> callback.accept(event.getServer()));
    }

    @Override
    public void registerServerStopping(java.util.function.Consumer<MinecraftServer> callback) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (ServerStoppingEvent event) -> callback.accept(event.getServer()));
    }

    @Override
    public void registerPlayerJoin(java.util.function.Consumer<ServerPlayer> callback) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        callback.accept(player);
                    }
                });
    }
}

package io.github.brainage04.simpletpa.fabric;

import com.mojang.brigadier.CommandDispatcher;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.platform.ServerPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class SimpleTPAFabric implements ModInitializer, ServerPlatform {
	@Override
	public void onInitialize() { SimpleTPA.initialize(this); }
	@Override public String loaderName() { return "Fabric"; }
	@Override public GameRule<Boolean> registerInstantAcceptGameRule() { return GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, "allow_instant_tpa_accepting")); }
	@Override public void registerCommands(java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registration) { CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registration.accept(dispatcher)); }
	@Override public void registerEndServerTick(java.util.function.Consumer<MinecraftServer> callback) { ServerTickEvents.END_SERVER_TICK.register(callback::accept); }
	@Override public void registerServerStarted(java.util.function.Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STARTED.register(callback::accept); }
	@Override public void registerServerStopping(java.util.function.Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STOPPING.register(callback::accept); }
}

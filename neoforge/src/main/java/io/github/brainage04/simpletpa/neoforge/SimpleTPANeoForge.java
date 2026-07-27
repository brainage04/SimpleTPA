package io.github.brainage04.simpletpa.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.platform.ServerPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(SimpleTPA.MOD_ID)
public final class SimpleTPANeoForge implements ServerPlatform {
	private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, SimpleTPA.MOD_ID);
	private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> ALLOW_INSTANT_TPA_ACCEPTING = GAME_RULES.register("allow_instant_tpa_accepting", () -> new GameRule<>(
			GameRuleCategory.PLAYER, GameRuleType.BOOL, com.mojang.brigadier.arguments.BoolArgumentType.bool(),
			(visitor, rule) -> visitor.visitBoolean(rule), com.mojang.serialization.Codec.BOOL,
			value -> value ? 1 : 0, true, FeatureFlagSet.of()
	));

	public SimpleTPANeoForge(IEventBus modEventBus) {
		GAME_RULES.register(modEventBus);
		modEventBus.addListener((FMLCommonSetupEvent event) -> SimpleTPA.initialize(this));
	}
	@Override public String loaderName() { return "NeoForge"; }
	@Override public GameRule<Boolean> registerInstantAcceptGameRule() { return ALLOW_INSTANT_TPA_ACCEPTING.get(); }
	@Override public void registerCommands(java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registration) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registration.accept(event.getDispatcher())); }
	@Override public void registerEndServerTick(java.util.function.Consumer<MinecraftServer> callback) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer())); }
	@Override public void registerServerStarted(java.util.function.Consumer<MinecraftServer> callback) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callback.accept(event.getServer())); }
	@Override public void registerServerStopping(java.util.function.Consumer<MinecraftServer> callback) { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> callback.accept(event.getServer())); }
}

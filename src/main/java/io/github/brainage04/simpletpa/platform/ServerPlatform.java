package io.github.brainage04.simpletpa.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

public interface ServerPlatform {
	String loaderName();
	GameRule<Boolean> registerInstantAcceptGameRule();
	void registerCommands(java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registration);
	void registerEndServerTick(java.util.function.Consumer<MinecraftServer> callback);
	void registerServerStarted(java.util.function.Consumer<MinecraftServer> callback);
	void registerServerStopping(java.util.function.Consumer<MinecraftServer> callback);
}

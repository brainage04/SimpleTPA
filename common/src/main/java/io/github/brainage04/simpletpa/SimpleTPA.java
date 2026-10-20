package io.github.brainage04.simpletpa;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.simpletpa.command.TPAutoAcceptCommand;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.command.core.ModCommands;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.event.ModTickEvents;
import io.github.brainage04.simpletpa.platform.ServerPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleTPA {
	public static final String MOD_ID = "simpletpa";
	public static final String MOD_NAME = "SimpleTPA";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ModFeedback FEEDBACK = ModFeedback.create(MOD_NAME);
	public static GameRule<Boolean> AUTO_ACCEPT;

	private SimpleTPA() {
	}

	public static void initialize(ServerPlatform platform) {
		LOGGER.info("{} initializing on {}...", MOD_NAME, platform.loaderName());
		AUTO_ACCEPT = platform.registerAutoAcceptGameRule();
		platform.registerCommands(ModCommands::register);
		platform.registerEndServerTick(ModTickEvents::onEndServerTick);
		platform.registerServerStarted(InstantTpaWhitelist::load);
		platform.registerServerStopping(server -> {
			try {
				InstantTpaWhitelist.save(server);
			} finally {
				TPRequestCommand.TP_REQUESTS.clear();
				TPAutoAcceptCommand.ACCEPT_ALL_CONFIRMATION.clear();
			}
		});
		ServerModHelpRegistry.register(new ServerModHelpEntry(
				MOD_ID,
				MOD_NAME,
				"Adds player-to-player teleport requests with persistent automatic-approval lists.",
				"/simpletpa help",
				"/gamerule simpletpa:auto_accept"
		));
		LOGGER.info("{} initialized.", MOD_NAME);
	}
}

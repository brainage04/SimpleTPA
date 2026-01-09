package io.github.brainage04.simpletpa;

import io.github.brainage04.simpletpa.command.ModCommands;
import io.github.brainage04.simpletpa.event.ModTickEvents;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTPA implements ModInitializer {
	public static final String MOD_ID = "simpletpa";
	public static final String MOD_NAME = "SimpleTPA";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("%s initializing...".formatted(MOD_NAME));

		ModCommands.initialize();
		ModTickEvents.initialize();

		LOGGER.info("%s initialized.".formatted(MOD_NAME));
	}
}
package io.github.brainage04.simpletpa;

import io.github.brainage04.simpletpa.command.ModCommands;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.event.ModTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTPA implements ModInitializer {
	public static final String MOD_ID = "simpletpa";
	public static final String MOD_NAME = "SimpleTPA";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final GameRule<Boolean> ALLOW_INSTANT_TPA_ACCEPTING = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.PLAYER)
			.buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "allow_instant_tpa_accepting"));

	@Override
	public void onInitialize() {
		LOGGER.info("%s initializing...".formatted(MOD_NAME));

		ModCommands.initialize();
		ModTickEvents.initialize();
		ServerLifecycleEvents.SERVER_STARTED.register(InstantTpaWhitelist::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(InstantTpaWhitelist::save);

		LOGGER.info("%s initialized.".formatted(MOD_NAME));
	}
}

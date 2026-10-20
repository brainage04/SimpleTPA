package io.github.brainage04.simpletpa.neoforge;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.SimpleTPAGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the shared GameTests as NeoForge test functions. Each function needs a matching
 * {@code data/<mod_id>/test_instance/<name>.json} in this source set's resources.
 */
@EventBusSubscriber(modid = SimpleTPA.MOD_ID)
public final class NeoForgeServerGameTests {
	private NeoForgeServerGameTests() {
	}

	@SubscribeEvent
	public static void registerTestFunctions(RegisterEvent event) {
		register(event, "accept_flows", SimpleTPAGameTests::acceptFlows);
		register(event, "tpa_alias_accept_flow", SimpleTPAGameTests::tpaAliasAcceptFlow);
		register(event, "tpa_tab_completion", SimpleTPAGameTests::tpaTabCompletion);
		register(event, "deny_flows", SimpleTPAGameTests::denyFlows);
		register(event, "instant_auto_accept_flow", SimpleTPAGameTests::instantAutoAcceptFlow);
		register(event, "request_buttons", SimpleTPAGameTests::requestButtons);
		register(event, "tpauto_alias", SimpleTPAGameTests::tpautoAlias);
		register(event, "accept_all_needs_confirmation", SimpleTPAGameTests::acceptAllNeedsConfirmation);
		register(event, "accept_all_confirmation_expires", SimpleTPAGameTests::acceptAllConfirmationExpires);
		register(event, "accept_all_teleports", SimpleTPAGameTests::acceptAllTeleports);
		register(event, "accept_all_toggle_off", SimpleTPAGameTests::acceptAllToggleOff);
		register(event, "accept_all_gamerule_off", SimpleTPAGameTests::acceptAllGameruleOff);
		register(event, "accept_all_persistence", SimpleTPAGameTests::acceptAllPersistence);
		register(event, "unreadable_whitelist_is_not_overwritten", SimpleTPAGameTests::unreadableWhitelistIsNotOverwritten);
		register(event, "whitelist_from_previous_version_loads", SimpleTPAGameTests::whitelistFromPreviousVersionLoads);
		register(event, "add_wildcard_rejected", SimpleTPAGameTests::addWildcardRejected);
		register(event, "combined_server_help", SimpleTPAGameTests::combinedServerHelp);
		register(event, "gamerule_registration", SimpleTPAGameTests::gameruleRegistration);
	}

	private static void register(RegisterEvent event, String path, Consumer<GameTestHelper> function) {
		event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, path), () -> function);
	}
}

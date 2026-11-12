package io.github.brainage04.simpletpa.neoforge;

import io.github.brainage04.simpletpa.SimpleTPA;
import io.github.brainage04.simpletpa.SimpleTPAGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Each function needs a matching {@code data/<mod_id>/test_instance/<name>.json} in this source
 * set's resources.
 */
@EventBusSubscriber(modid = SimpleTPA.MOD_ID)
public final class NeoForgeServerGameTests {
    private NeoForgeServerGameTests() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        register(event, "accept_flows", SimpleTPAGameTests::acceptFlows);
        register(event, "tpa_alias_accept_flow", SimpleTPAGameTests::tpaAliasAcceptFlow);
        register(event, "tpa_tab_completion", SimpleTPAGameTests::tpaTabCompletion);
        register(event, "deny_flows", SimpleTPAGameTests::denyFlows);
        register(event, "requests_expire", SimpleTPAGameTests::requestsExpire);
        register(event, "named_deny_flow", SimpleTPAGameTests::namedDenyFlow);
        register(
                event,
                "response_needs_one_online_request",
                SimpleTPAGameTests::responseNeedsOneOnlineRequest);
        register(event, "instant_auto_accept_flow", SimpleTPAGameTests::instantAutoAcceptFlow);
        register(
                event,
                "auto_accept_answers_pending_request",
                SimpleTPAGameTests::autoAcceptAnswersPendingRequest);
        register(
                event,
                "auto_accept_waits_for_blocked_target",
                SimpleTPAGameTests::autoAcceptWaitsForBlockedTarget);
        register(
                event,
                "accept_waits_for_blocked_target",
                SimpleTPAGameTests::acceptWaitsForBlockedTarget);
        register(
                event,
                "accept_waits_for_sender_who_cannot_move",
                SimpleTPAGameTests::acceptWaitsForSenderWhoCannotMove);
        register(
                event, "teleports_when_nobody_falls", SimpleTPAGameTests::teleportsWhenNobodyFalls);
        register(event, "request_buttons", SimpleTPAGameTests::requestButtons);
        register(event, "tpauto_alias", SimpleTPAGameTests::tpautoAlias);
        register(event, "remove_offline_player", SimpleTPAGameTests::removeOfflinePlayer);
        register(
                event,
                "whitelist_names_follow_renames",
                SimpleTPAGameTests::whitelistNamesFollowRenames);
        register(
                event,
                "remove_prefers_online_player",
                SimpleTPAGameTests::removePrefersOnlinePlayer);
        register(
                event,
                "ambiguous_name_is_not_removed",
                SimpleTPAGameTests::ambiguousNameIsNotRemoved);
        register(
                event,
                "accept_all_needs_confirmation",
                SimpleTPAGameTests::acceptAllNeedsConfirmation);
        register(
                event,
                "accept_all_confirmation_expires",
                SimpleTPAGameTests::acceptAllConfirmationExpires);
        register(event, "accept_all_teleports", SimpleTPAGameTests::acceptAllTeleports);
        register(event, "accept_all_toggle_off", SimpleTPAGameTests::acceptAllToggleOff);
        register(event, "accept_all_gamerule_off", SimpleTPAGameTests::acceptAllGameruleOff);
        register(event, "accept_all_persistence", SimpleTPAGameTests::acceptAllPersistence);
        register(
                event,
                "unreadable_whitelist_is_not_overwritten",
                SimpleTPAGameTests::unreadableWhitelistIsNotOverwritten);
        register(
                event,
                "whitelist_from_previous_version_loads",
                SimpleTPAGameTests::whitelistFromPreviousVersionLoads);
        register(
                event,
                "whitelist_not_saved_before_load",
                SimpleTPAGameTests::whitelistNotSavedBeforeLoad);
        register(event, "add_wildcard_rejected", SimpleTPAGameTests::addWildcardRejected);
        register(event, "combined_server_help", SimpleTPAGameTests::combinedServerHelp);
        register(event, "gamerule_registration", SimpleTPAGameTests::gameruleRegistration);
        register(event, "legacy_game_rule_migrates", SimpleTPAGameTests::legacyGameRuleMigrates);
        register(
                event,
                "legacy_game_rule_yields_to_current_rule",
                SimpleTPAGameTests::legacyGameRuleYieldsToCurrentRule);
        register(
                event,
                "legacy_game_rule_survives_corrupt_file",
                SimpleTPAGameTests::legacyGameRuleSurvivesCorruptFile);
        register(
                event,
                "brainagelib_dependency_stops_below_next_major",
                NeoForgeServerGameTests::brainageLibDependencyStopsBelowNextMajor);
    }

    private static void brainageLibDependencyStopsBelowNextMajor(GameTestHelper helper) {
        VersionRange range =
                ModList.get()
                        .getModContainerById(SimpleTPA.MOD_ID)
                        .orElseThrow()
                        .getModInfo()
                        .getDependencies()
                        .stream()
                        .filter(dependency -> dependency.getModId().equals("brainagelib"))
                        .findFirst()
                        .orElseThrow()
                        .getVersionRange();
        ArtifactVersion built =
                ModList.get()
                        .getModContainerById("brainagelib")
                        .orElseThrow()
                        .getModInfo()
                        .getVersion();
        if (!range.containsVersion(built)) {
            helper.fail("the BrainageLib range " + range + " rejects the built " + built);
        } else if (range.containsVersion(new DefaultArtifactVersion("3.0.0"))) {
            helper.fail("the BrainageLib range " + range + " accepts 3.0.0");
        } else {
            helper.succeed();
        }
    }

    private static void register(
            RegisterEvent event, String path, Consumer<GameTestHelper> function) {
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, path),
                () -> function);
    }
}

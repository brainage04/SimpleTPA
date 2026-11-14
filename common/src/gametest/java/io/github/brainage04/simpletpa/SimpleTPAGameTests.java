package io.github.brainage04.simpletpa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.simpletpa.command.Landing;
import io.github.brainage04.simpletpa.command.TPAutoAcceptCommand;
import io.github.brainage04.simpletpa.command.TPRequestCommand;
import io.github.brainage04.simpletpa.data.InstantTpaWhitelist;
import io.github.brainage04.simpletpa.data.LegacyGameRule;
import io.github.brainage04.simpletpa.event.ModTickEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SimpleTPAGameTests {
    public static final int ACCEPT_ALL_EXPIRY_MAX_TICKS =
            TPAutoAcceptCommand.CONFIRM_WINDOW_TICKS + 20;
    public static final String AUTO_ACCEPT_OFF_ENVIRONMENT = "simpletpa:auto_accept_off";
    private static int playerPairCounter;
    private static final Map<GameTestHelper, List<GameTestPlayer>> OPEN_PLAYERS =
            new IdentityHashMap<>();
    private static final Set<GameTestHelper> FINISHED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private SimpleTPAGameTests() {}

    public static void acceptFlows(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10.25, 10, 10.75));
                    players[1].setYRot(135.0F);
                    players[1].setXRot(20.0F);
                    command(players[0], "tprequest " + players[1].getScoreboardName());
                    command(players[1], "tpaccept");
                    at(
                            helper,
                            2,
                            () -> {
                                ServerPlayer mover = players[0];
                                ServerPlayer target = players[1];
                                if (mover.position().equals(target.position())
                                        && mover.getYRot() == target.getYRot()
                                        && mover.getXRot() == target.getXRot()) succeed(helper);
                                else
                                    fail(
                                            helper,
                                            "accepted request did not put the sender on the receiver, facing their way: "
                                                    + mover.position()
                                                    + " "
                                                    + mover.getYRot()
                                                    + "/"
                                                    + mover.getXRot());
                            });
                });
    }

    public static void tpaAliasAcceptFlow(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10, 10, 10));
                    command(players[0], "tpa " + players[1].getScoreboardName());
                    command(players[1], "tpaccept");
                    at(
                            helper,
                            2,
                            () -> {
                                if (players[0].blockPosition().equals(players[1].blockPosition()))
                                    succeed(helper);
                                else fail(helper, "/tpa request did not teleport after /tpaccept");
                            });
                });
    }

    public static void tpaTabCompletion(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
                    String target = players[1].getScoreboardName();
                    for (String literal : new String[] {"tpa ", "tprequest "}) {
                        var parse =
                                dispatcher.parse(literal, players[0].createCommandSourceStack());
                        boolean suggested =
                                dispatcher.getCompletionSuggestions(parse).join().getList().stream()
                                        .anyMatch(
                                                suggestion -> suggestion.getText().equals(target));
                        if (!suggested) {
                            fail(
                                    helper,
                                    "/"
                                            + literal.trim()
                                            + " did not suggest online player "
                                            + target);
                            return;
                        }
                    }
                    succeed(helper);
                });
    }

    /**
     * A request older than the lifetime expires and both players are told; a younger one stays. The
     * requests are backdated instead of waiting a minute.
     */
    public static void requestsExpire(GameTestHelper helper) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        pair(helper, pairs::add);
        pair(helper, pairs::add);
        ServerPlayer[] old = pairs.get(0);
        ServerPlayer[] young = pairs.get(1);
        command(old[0], "tpa " + old[1].getScoreboardName());
        command(young[0], "tpa " + young[1].getScoreboardName());
        backdate(old, ModTickEvents.REQUEST_LIFETIME_TICKS);
        backdate(young, ModTickEvents.REQUEST_LIFETIME_TICKS - 5);
        at(
                helper,
                2,
                () -> {
                    if (pending(old[1], old[0]))
                        fail(helper, "a request older than its lifetime is still open");
                    else if (!pending(young[1], young[0]))
                        fail(helper, "a request expired before its lifetime");
                    else if (!received(
                            old[1],
                            "[SimpleTPA] The TP request that "
                                    + old[0].getScoreboardName()
                                    + " sent you has expired."))
                        fail(helper, "receiver was not told, got " + texts(old[1]));
                    else if (!received(
                            old[0],
                            "[SimpleTPA] The TP request that you sent to "
                                    + old[1].getScoreboardName()
                                    + " has expired."))
                        fail(helper, "sender was not told, got " + texts(old[0]));
                    else succeed(helper);
                });
    }

    private static void backdate(ServerPlayer[] players, int ticks) {
        TPRequestCommand.TP_REQUESTS.replaceAll(
                request ->
                        request.matches(players[1].getUUID(), players[0].getUUID())
                                ? new TPRequestCommand.TPRequest(
                                        request.toId(),
                                        request.toName(),
                                        request.fromId(),
                                        request.fromName(),
                                        request.created() - ticks)
                                : request);
    }

    public static void denyFlows(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10, 10, 10));
                    command(players[0], "tprequest " + players[1].getScoreboardName());
                    command(players[1], "tpdeny");
                    at(
                            helper,
                            2,
                            () -> {
                                if (players[0].blockPosition().equals(new BlockPos(0, 0, 0)))
                                    succeed(helper);
                                else fail(helper, "denied request teleported");
                            });
                });
    }

    public static void namedDenyFlow(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10, 10, 10));
                    command(players[0], "tpa " + players[1].getScoreboardName());
                    command(players[1], "tpdeny " + players[0].getScoreboardName());
                    at(
                            helper,
                            2,
                            () -> {
                                if (!players[0].blockPosition().equals(new BlockPos(0, 0, 0))) {
                                    fail(helper, "/tpdeny <name> teleported");
                                } else if (pending(players[1], players[0])) {
                                    fail(helper, "/tpdeny <name> left the request pending");
                                } else if (!received(
                                        players[0],
                                        "[SimpleTPA] "
                                                + players[1].getScoreboardName()
                                                + " denied your TP request.")) {
                                    fail(helper, "sender was not told, got " + texts(players[0]));
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    /**
     * What /tpaccept and /tpdeny answer without exactly one online request to answer; each names
     * its own command.
     */
    public static void responseNeedsOneOnlineRequest(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer sender = players[0];
                    ServerPlayer receiver = players[1];
                    ServerPlayer secondSender = player(helper, "second" + playerPairCounter);
                    ServerPlayer bystander = player(helper, "bystander" + playerPairCounter);
                    String receiverName = receiver.getScoreboardName();
                    at(
                            helper,
                            1,
                            () -> {
                                command(receiver, "tpaccept");
                                command(bystander, "tpdeny " + receiverName);
                            });
                    at(
                            helper,
                            2,
                            () -> {
                                command(sender, "tpa " + receiverName);
                                command(secondSender, "tpa " + receiverName);
                            });
                    at(helper, 3, () -> command(receiver, "tpaccept"));
                    at(helper, 4, () -> command(receiver, "tpdeny"));
                    at(
                            helper,
                            5,
                            () -> {
                                fixture(secondSender).close();
                                command(receiver, "tpdeny " + sender.getScoreboardName());
                            });
                    at(helper, 6, () -> command(receiver, "tpaccept"));
                    at(
                            helper,
                            8,
                            () -> {
                                String secondName = secondSender.getScoreboardName();
                                List<String> missing =
                                        Stream.of(
                                                        "[SimpleTPA] You have no incoming TP requests!",
                                                        "[SimpleTPA] You have more than 1 incoming TP request, specifically from:",
                                                        "[SimpleTPA] - "
                                                                + sender.getScoreboardName(),
                                                        "[SimpleTPA] - " + secondName,
                                                        "[SimpleTPA] Please specify which one you wish to accept using /tpaccept <name>.",
                                                        "[SimpleTPA] Please specify which one you wish to deny using /tpdeny <name>.",
                                                        "[SimpleTPA] "
                                                                + secondName
                                                                + " is not online!")
                                                .filter(text -> !received(receiver, text))
                                                .toList();
                                if (!missing.isEmpty()) {
                                    fail(
                                            helper,
                                            "receiver was not told "
                                                    + missing
                                                    + ", got "
                                                    + texts(receiver));
                                } else if (!received(
                                        bystander,
                                        "[SimpleTPA] You do not have a TP request from "
                                                + receiverName
                                                + "!")) {
                                    fail(helper, "bystander was not told, got " + texts(bystander));
                                } else if (pending(receiver, sender)) {
                                    fail(helper, "/tpdeny <name> left the request pending");
                                } else if (!pending(receiver, secondSender)) {
                                    fail(helper, "a request was answered without its sender");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    public static void instantAutoAcceptFlow(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(
                            helper,
                            1,
                            () ->
                                    command(
                                            players[1],
                                            "tpautoaccept add " + players[0].getScoreboardName()));
                    at(
                            helper,
                            2,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                            });
                    at(
                            helper,
                            3,
                            () ->
                                    command(
                                            players[0],
                                            "tprequest " + players[1].getScoreboardName()));
                    at(
                            helper,
                            4,
                            () -> {
                                if (players[0].blockPosition().equals(players[1].blockPosition()))
                                    succeed(helper);
                                else fail(helper, "whitelist did not auto accept");
                            });
                });
    }

    /** A request sent before the receiver whitelisted its sender does not block auto-accept. */
    public static void autoAcceptAnswersPendingRequest(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10, 10, 10));
                    at(
                            helper,
                            1,
                            () -> command(players[0], "tpa " + players[1].getScoreboardName()));
                    at(
                            helper,
                            2,
                            () ->
                                    command(
                                            players[1],
                                            "tpautoaccept add " + players[0].getScoreboardName()));
                    at(
                            helper,
                            3,
                            () -> command(players[0], "tpa " + players[1].getScoreboardName()));
                    at(
                            helper,
                            4,
                            () -> {
                                if (!players[0].position().equals(players[1].position()))
                                    fail(
                                            helper,
                                            "a pending request blocked auto-accept, got "
                                                    + texts(players[0]));
                                else if (pending(players[1], players[0]))
                                    fail(helper, "auto-accept left the earlier request open");
                                else succeed(helper);
                            });
                });
    }

    /** A state that stops a player from being teleported to, and the words the mod uses for it. */
    private record Blocked(String reason, BiConsumer<GameTestHelper, ServerPlayer> apply) {}

    private static final Blocked DEAD =
            new Blocked(
                    "dead",
                    (helper, player) -> {
                        fixture(player).markLoaded();
                        player.kill(helper.getLevel());
                    });
    private static final Blocked IN_END_CREDITS =
            new Blocked("not in a world", (helper, player) -> player.showEndCredits());
    private static final Blocked SPECTATOR =
            new Blocked(
                    "in spectator mode",
                    (helper, player) -> fixture(player).gameMode(GameType.SPECTATOR));
    private static final Blocked GLIDING =
            new Blocked("gliding", (helper, player) -> player.startFallFlying());
    private static final Blocked FLYING =
            new Blocked(
                    "flying",
                    (helper, player) -> {
                        fixture(player).gameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                    });
    private static final Blocked INSIDE_A_BLOCK =
            new Blocked(
                    "somewhere unsafe",
                    (helper, player) -> {
                        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
                        player.setPos(
                                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
                    });
    private static final Blocked ON_MAGMA =
            new Blocked(
                    "somewhere unsafe",
                    (helper, player) -> {
                        helper.setBlock(new BlockPos(5, 1, 5), Blocks.MAGMA_BLOCK);
                        player.setPos(
                                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
                    });

    /**
     * A whitelisted requester is not put on a receiver who cannot be teleported to; the request
     * waits for /tpaccept instead.
     */
    public static void autoAcceptWaitsForBlockedTarget(GameTestHelper helper) {
        checkBlocked(
                helper,
                List.of(DEAD, IN_END_CREDITS, SPECTATOR, GLIDING, FLYING, INSIDE_A_BLOCK, ON_MAGMA),
                (blocked, players) -> {
                    InstantTpaWhitelist.add(players[1], players[0]);
                    blocked.apply().accept(helper, players[1]);
                    command(players[0], "tpa " + players[1].getScoreboardName());
                },
                (blocked, players) ->
                        players[1].getScoreboardName()
                                + " is "
                                + blocked.reason()
                                + ", so your TP request was not accepted automatically.",
                players -> players[0]);
    }

    /** /tpaccept leaves the request open while the receiver cannot be teleported to. */
    public static void acceptWaitsForBlockedTarget(GameTestHelper helper) {
        checkBlocked(
                helper,
                List.of(SPECTATOR, GLIDING, FLYING, INSIDE_A_BLOCK, ON_MAGMA),
                (blocked, players) -> {
                    command(players[0], "tpa " + players[1].getScoreboardName());
                    blocked.apply().accept(helper, players[1]);
                    command(players[1], "tpaccept");
                },
                (blocked, players) ->
                        players[0].getScoreboardName()
                                + " can't teleport to you while you are "
                                + blocked.reason()
                                + ". Their TP request is still open.",
                players -> players[1]);
    }

    /** /tpaccept leaves the request open while the requester cannot be moved. */
    public static void acceptWaitsForSenderWhoCannotMove(GameTestHelper helper) {
        checkBlocked(
                helper,
                List.of(DEAD, IN_END_CREDITS),
                (blocked, players) -> {
                    command(players[0], "tpa " + players[1].getScoreboardName());
                    blocked.apply().accept(helper, players[0]);
                    command(players[1], "tpaccept");
                },
                (blocked, players) ->
                        players[0].getScoreboardName()
                                + " could not be teleported. Their TP request is still open.",
                players -> players[1]);
    }

    /** What a requester and target are doing, in a case where the requester takes no fall. */
    private record NoFall(String name, BiConsumer<ServerPlayer, ServerPlayer> setUp) {}

    /**
     * A target in the air does not stop a requester who can fly, and a flying flag still set while
     * the target rides something is not flying. The whitelisted requester is moved at once. Joined
     * players start on one spot, so each target is moved away first; otherwise a refused teleport
     * would leave the requester on the target too.
     */
    public static void teleportsWhenNobodyFalls(GameTestHelper helper) {
        List<NoFall> cases =
                List.of(
                        new NoFall(
                                "a creative requester to a flying target",
                                (from, to) -> {
                                    fixture(from).gameMode(GameType.CREATIVE);
                                    FLYING.apply().accept(helper, to);
                                    to.setPos(
                                            Vec3.atBottomCenterOf(
                                                    helper.absolutePos(new BlockPos(1, 5, 1))));
                                }),
                        new NoFall(
                                "a creative requester to a gliding target",
                                (from, to) -> {
                                    fixture(from).gameMode(GameType.CREATIVE);
                                    GLIDING.apply().accept(helper, to);
                                    to.setPos(
                                            Vec3.atBottomCenterOf(
                                                    helper.absolutePos(new BlockPos(5, 5, 5))));
                                }),
                        new NoFall(
                                "a survival requester to a target riding with flight on",
                                (from, to) -> {
                                    FLYING.apply().accept(helper, to);
                                    to.startRiding(
                                            helper.spawn(
                                                    EntityTypes.MINECART, new BlockPos(3, 2, 3)),
                                            true,
                                            false);
                                }));
        List<ServerPlayer[]> pairs = new ArrayList<>();
        cases.forEach(ignored -> pair(helper, pairs::add));
        at(
                helper,
                1,
                () -> {
                    for (int i = 0; i < cases.size(); i++) {
                        ServerPlayer from = pairs.get(i)[0];
                        ServerPlayer to = pairs.get(i)[1];
                        cases.get(i).setUp().accept(from, to);
                        if (from.position().equals(to.position())) {
                            fail(helper, cases.get(i).name() + " started on the target");
                            return;
                        }
                        InstantTpaWhitelist.add(to, from);
                        command(from, "tpa " + to.getScoreboardName());
                        if (!from.position().equals(to.position())) {
                            fail(
                                    helper,
                                    cases.get(i).name() + " was not moved, got " + texts(from));
                            return;
                        }
                    }
                    succeed(helper);
                });
    }

    /** A harnessed happy ghast at {@code relativePos}, staying where it is put. */
    private static HappyGhast happyGhast(GameTestHelper helper, BlockPos relativePos) {
        HappyGhast ghast = helper.spawn(EntityTypes.HAPPY_GHAST, relativePos);
        ghast.setNoAi(true);
        ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.pick(DyeColor.WHITE)));
        return ghast;
    }

    /**
     * A stone floor around the structure, so a test owns the drop below the spot it measures. The
     * test level is ordinary terrain, so its own ground would be at a height a test cannot predict.
     */
    private static void floorBelow(GameTestHelper helper, int relativeY) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, relativeY, z), Blocks.STONE);
            }
        }
    }

    /**
     * Puts {@code player} at {@code position} on a stone block, so the arrival spot has no drop.
     * The drop check refuses a spot in the air above a drop that would hurt, and the test level's
     * own ground under these coordinates is far below.
     */
    private static void standOnStone(GameTestHelper helper, ServerPlayer player, Vec3 position) {
        player.setPos(position);
        helper.getLevel()
                .setBlockAndUpdate(
                        BlockPos.containing(position).below(), Blocks.STONE.defaultBlockState());
    }

    /**
     * A survival requester teleported to a player riding a harnessed happy ghast arrives on the
     * ghast's top rather than in the rider's seat, where the client would not hold them
     * (HappyGhast.java:576-585), and the long floor below does not refuse the request.
     */
    public static void teleportsOntoHappyGhast(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer from = players[0];
                    ServerPlayer to = players[1];
                    at(
                            helper,
                            1,
                            () -> {
                                HappyGhast ghast = happyGhast(helper, new BlockPos(1, 2, 1));
                                to.startRiding(ghast, true, false);
                                if (to.getVehicle() != ghast) {
                                    fail(helper, "the target did not get on the ghast");
                                    return;
                                }
                                double top = ghast.getBoundingBox().maxY;
                                AABB seat =
                                        from.getBoundingBox()
                                                .move(to.position().subtract(from.position()));
                                if (!Landing.dropHurts(to.level(), from, seat)) {
                                    fail(helper, "the drop from the rider's seat was too short");
                                    return;
                                }
                                InstantTpaWhitelist.add(to, from);
                                command(from, "tpa " + to.getScoreboardName());
                                AABB box = ghast.getBoundingBox();
                                Vec3 expected =
                                        new Vec3(
                                                (box.minX + box.maxX) / 2.0,
                                                top,
                                                (box.minZ + box.maxZ) / 2.0);
                                if (from.position().distanceToSqr(expected) > 1.0E-8) {
                                    fail(
                                            helper,
                                            "the requester arrived at "
                                                    + from.position()
                                                    + " instead of the ghast's top, "
                                                    + expected
                                                    + ", got "
                                                    + texts(from));
                                    return;
                                }
                                succeed(helper);
                            });
                });
    }

    /**
     * A target in the air above a drop that would hurt is refused while the requester's safety
     * check is on, and the same request goes through once they turn it off.
     */
    public static void refusesLongDrop(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer from = players[0];
                    ServerPlayer to = players[1];
                    Vec3 start = from.position();
                    at(
                            helper,
                            1,
                            () -> {
                                floorBelow(helper, 1);
                                to.setPos(
                                        Vec3.atBottomCenterOf(
                                                helper.absolutePos(new BlockPos(1, 6, 1))));
                                InstantTpaWhitelist.add(to, from);
                                command(from, "tpa " + to.getScoreboardName());
                            });
                    at(
                            helper,
                            2,
                            () -> {
                                String expected =
                                        "[SimpleTPA] "
                                                + to.getScoreboardName()
                                                + " is in the air above a long drop, so your TP request was not accepted automatically.";
                                if (!from.position().equals(start)) {
                                    fail(helper, "teleported into a long drop");
                                    return;
                                }
                                if (!pending(to, from)) {
                                    fail(helper, "the request closed over a long drop");
                                    return;
                                }
                                if (!received(from, expected)) {
                                    fail(
                                            helper,
                                            "not told \"" + expected + "\", got " + texts(from));
                                    return;
                                }
                                command(from, "tpasafety off");
                                command(to, "tpaccept");
                            });
                    at(
                            helper,
                            3,
                            () -> {
                                String off =
                                        "[SimpleTPA] Your TP requests go through while the player you teleport to is in the air above a long drop, so you will fall when you arrive.";
                                if (!received(from, off)) {
                                    fail(helper, "no safety-off message, got " + texts(from));
                                    return;
                                }
                                if (!from.position().equals(to.position())) {
                                    fail(
                                            helper,
                                            "the request did not go through once the check was off, got "
                                                    + texts(from));
                                    return;
                                }
                                succeed(helper);
                            });
                });
    }

    /**
     * Short drops pass: a target sneaking at the edge of a block, and a target over the air two
     * blocks above the floor. Both are shorter than the game's safe fall distance
     * (Attributes.SAFE_FALL_DISTANCE).
     */
    public static void acceptsShortDrop(GameTestHelper helper) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        pair(helper, pairs::add);
        pair(helper, pairs::add);
        at(
                helper,
                1,
                () -> {
                    floorBelow(helper, 1);
                    helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
                    Vec3 ledge = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 4, 1)));
                    ServerPlayer edgeFrom = pairs.get(0)[0];
                    ServerPlayer edgeTo = pairs.get(0)[1];
                    edgeTo.setShiftKeyDown(true);
                    edgeTo.setPos(ledge.x + 1.02, ledge.y, ledge.z);
                    ServerPlayer airFrom = pairs.get(1)[0];
                    ServerPlayer airTo = pairs.get(1)[1];
                    airTo.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 4, 2))));
                    for (ServerPlayer[] pair : pairs) {
                        InstantTpaWhitelist.add(pair[1], pair[0]);
                        command(pair[0], "tpa " + pair[1].getScoreboardName());
                    }
                    for (ServerPlayer[] pair : pairs) {
                        if (!pair[0].position().equals(pair[1].position())) {
                            fail(
                                    helper,
                                    "a short drop was refused: "
                                            + pair[1].position()
                                            + " got "
                                            + texts(pair[0]));
                            return;
                        }
                    }
                    succeed(helper);
                });
    }

    /**
     * A request that crosses dimensions judges the drop against the target's level. The target
     * stands on a stone block in the Nether with nothing but air below it in the Overworld at the
     * same coordinates, so reading the requester's level would refuse the request as a long drop.
     */
    public static void teleportsBetweenDimensions(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer from = players[0];
                    ServerPlayer to = players[1];
                    at(
                            helper,
                            1,
                            () -> {
                                ServerLevel nether =
                                        helper.getLevel().getServer().getLevel(Level.NETHER);
                                if (nether == null) {
                                    fail(helper, "the test server has no Nether");
                                    return;
                                }
                                BlockPos spot = helper.absolutePos(new BlockPos(1, 6, 1));
                                Vec3 seat = Vec3.atBottomCenterOf(spot);
                                // Nothing within the check's reach below the same coordinates in
                                // the requester's level, so only the target's level can say the
                                // drop is short.
                                for (int below = 1; below <= 5; below++) {
                                    helper.setBlock(new BlockPos(1, 6 - below, 1), Blocks.AIR);
                                }
                                if (!to.teleportTo(
                                        nether, seat.x, seat.y, seat.z, Set.of(), 0.0F, 0.0F,
                                        false)) {
                                    fail(helper, "the target could not be put in the Nether");
                                    return;
                                }
                                nether.setBlockAndUpdate(
                                        spot.below(), Blocks.STONE.defaultBlockState());
                                InstantTpaWhitelist.add(to, from);
                                command(from, "tpa " + to.getScoreboardName());
                                if (!from.position().equals(to.position())) {
                                    fail(
                                            helper,
                                            "a drop in the target's own level was refused, got "
                                                    + texts(from));
                                    return;
                                }
                                succeed(helper);
                            });
                });
    }

    /**
     * The drop check floors the damage after the fall damage multiplier, as the game does
     * (LivingEntity.java:1856-1857): at a multiplier of 0.5 a four block drop deals floor((4 + 1e-6
     * - 3) * 0.5) = 0 damage, while flooring first would read 1 * 0.5 and refuse.
     */
    public static void smallFallMultiplierAcceptsAHurtlessDrop(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer from = players[0];
                    ServerPlayer to = players[1];
                    at(
                            helper,
                            1,
                            () -> {
                                AttributeInstance multiplier =
                                        from.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
                                if (multiplier == null) {
                                    fail(helper, "the requester has no fall damage multiplier");
                                    return;
                                }
                                multiplier.setBaseValue(0.5);
                                floorBelow(helper, 1);
                                to.setPos(
                                        Vec3.atBottomCenterOf(
                                                helper.absolutePos(new BlockPos(1, 6, 1))));
                                InstantTpaWhitelist.add(to, from);
                                command(from, "tpa " + to.getScoreboardName());
                                if (!from.position().equals(to.position())) {
                                    fail(
                                            helper,
                                            "a drop that deals no damage was refused, got "
                                                    + texts(from));
                                    return;
                                }
                                succeed(helper);
                            });
                });
    }

    /**
     * Drops the game does not hurt for go through, and Slow Falling counts only while it lasts.
     * Each target is four blocks above the stone floor, a drop that refuses on its own: over water,
     * over a cobweb (tagged as resetting fall damage), over powder snow, and above the plain floor
     * for a requester with a long Slow Falling. A requester whose Slow Falling ends at once is
     * still refused.
     */
    public static void resettingLandingsAndSlowFallingAcceptADrop(GameTestHelper helper) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            pair(helper, pairs::add);
        }
        List<Vec3> starts = pairs.stream().map(players -> players[0].position()).toList();
        at(
                helper,
                1,
                () -> {
                    floorBelow(helper, 1);
                    helper.setBlock(new BlockPos(0, 2, 0), Blocks.WATER);
                    helper.setBlock(new BlockPos(0, 3, 2), Blocks.COBWEB);
                    helper.setBlock(new BlockPos(2, 2, 0), Blocks.POWDER_SNOW);
                    BlockPos[] targets = {
                        new BlockPos(0, 6, 0),
                        new BlockPos(0, 6, 2),
                        new BlockPos(2, 6, 0),
                        new BlockPos(2, 6, 2),
                        new BlockPos(1, 6, 1)
                    };
                    pairs.get(3)[0].addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
                    pairs.get(4)[0].addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1));
                    for (int index = 0; index < pairs.size(); index++) {
                        ServerPlayer from = pairs.get(index)[0];
                        ServerPlayer to = pairs.get(index)[1];
                        to.setPos(Vec3.atBottomCenterOf(helper.absolutePos(targets[index])));
                        InstantTpaWhitelist.add(to, from);
                        command(from, "tpa " + to.getScoreboardName());
                    }
                    String[] cases = {
                        "a drop into water",
                        "a drop through a cobweb",
                        "a drop into powder snow",
                        "a drop with a long Slow Falling"
                    };
                    for (int index = 0; index < cases.length; index++) {
                        ServerPlayer from = pairs.get(index)[0];
                        if (!from.position().equals(pairs.get(index)[1].position())) {
                            fail(helper, cases[index] + " was refused, got " + texts(from));
                            return;
                        }
                    }
                    ServerPlayer shortSlow = pairs.get(4)[0];
                    ServerPlayer shortSlowTarget = pairs.get(4)[1];
                    String refusal =
                            "[SimpleTPA] "
                                    + shortSlowTarget.getScoreboardName()
                                    + " is in the air above a long drop, so your TP request was not accepted automatically.";
                    if (!shortSlow.position().equals(starts.get(4))
                            || !received(shortSlow, refusal)) {
                        fail(
                                helper,
                                "a drop with a Slow Falling that ends at once was not refused, got "
                                        + shortSlow.position()
                                        + " "
                                        + texts(shortSlow));
                        return;
                    }
                    succeed(helper);
                });
    }

    /**
     * A fall that goes on below a resetting block counts again from that block's bottom, and the
     * drop check's Slow Falling and powder snow edges. Above the stone floor: a target three blocks
     * above a single powder snow block with four blocks of air below it (refused, the fall below
     * deals one damage); a target over water three blocks deep (accepted); a six block drop for a
     * requester with infinite Slow Falling (accepted); a five block drop onto powder snow for a
     * requester in leather boots who stands above its top, so it holds them (accepted). Last, a
     * target at the top of the world with no floor within the check's 128 block scan, for a
     * requester with a long Slow Falling (refused).
     */
    public static void fallBelowResetsAndSlowFallingEdges(GameTestHelper helper) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            pair(helper, pairs::add);
        }
        at(
                helper,
                1,
                () -> {
                    floorBelow(helper, 1);
                    helper.setBlock(new BlockPos(0, 6, 0), Blocks.POWDER_SNOW);
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(0, y, 2), Blocks.WATER);
                    }
                    helper.setBlock(new BlockPos(2, 2, 2), Blocks.POWDER_SNOW);
                    ServerLevel level = helper.getLevel();
                    BlockPos column = helper.absolutePos(new BlockPos(1, 0, 1));
                    BlockPos top = new BlockPos(column.getX(), level.getMaxY() - 2, column.getZ());
                    for (int below = 0; below <= 130; below++) {
                        if (!level.getBlockState(top.below(below)).isAir()) {
                            fail(
                                    helper,
                                    "the test needs 130 blocks of air below "
                                            + top
                                            + ", found a block at "
                                            + top.below(below));
                            return;
                        }
                    }
                    Vec3[] targets = {
                        Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 10, 0))),
                        Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 8, 2))),
                        Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 8, 0))),
                        Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 8, 2))),
                        Vec3.atBottomCenterOf(top)
                    };
                    pairs.get(2)[0].addEffect(
                            new MobEffectInstance(
                                    MobEffects.SLOW_FALLING, MobEffectInstance.INFINITE_DURATION));
                    ServerPlayer booted = pairs.get(3)[0];
                    booted.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                    standOnStone(
                            helper,
                            booted,
                            Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 4, 5))));
                    pairs.get(4)[0].addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
                    List<Vec3> requesters =
                            pairs.stream().map(players -> players[0].position()).toList();
                    for (int index = 0; index < pairs.size(); index++) {
                        ServerPlayer from = pairs.get(index)[0];
                        ServerPlayer to = pairs.get(index)[1];
                        to.setPos(targets[index]);
                        InstantTpaWhitelist.add(to, from);
                        command(from, "tpa " + to.getScoreboardName());
                    }
                    String[] cases = {
                        "a fall that goes on four blocks below a single powder snow block",
                        "a drop into deep water",
                        "a drop with an infinite Slow Falling",
                        "a drop onto powder snow in leather boots",
                        "a drop with a long Slow Falling and no floor within 128 blocks"
                    };
                    boolean[] refused = {true, false, false, false, true};
                    List<String> failures = new ArrayList<>();
                    for (int index = 0; index < pairs.size(); index++) {
                        ServerPlayer from = pairs.get(index)[0];
                        ServerPlayer to = pairs.get(index)[1];
                        if (!refused[index]) {
                            if (!from.position().equals(to.position())) {
                                failures.add(cases[index] + " was refused, got " + texts(from));
                            }
                            continue;
                        }
                        String refusal =
                                "[SimpleTPA] "
                                        + to.getScoreboardName()
                                        + " is in the air above a long drop, so your TP request was not accepted automatically.";
                        if (!from.position().equals(requesters.get(index))
                                || !received(from, refusal)) {
                            failures.add(
                                    cases[index]
                                            + " was not refused, got "
                                            + from.position()
                                            + " "
                                            + texts(from));
                        }
                    }
                    if (!failures.isEmpty()) {
                        fail(helper, String.join("; ", failures));
                        return;
                    }
                    succeed(helper);
                });
    }

    /**
     * A creative requester skips the landing checks a survival one is held to: they are put in the
     * air above a long drop, and on a spot the spot checks call unsafe.
     */
    public static void creativeSkipsLandingChecks(GameTestHelper helper) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            pair(helper, pairs::add);
        }
        List<Vec3> starts = pairs.stream().map(players -> players[0].position()).toList();
        at(
                helper,
                1,
                () -> {
                    floorBelow(helper, 1);
                    helper.setBlock(new BlockPos(5, 1, 5), Blocks.MAGMA_BLOCK);
                    for (int index = 0; index < pairs.size(); index++) {
                        ServerPlayer to = pairs.get(index)[1];
                        if (index < 2) {
                            to.setPos(
                                    Vec3.atBottomCenterOf(
                                            helper.absolutePos(new BlockPos(1, 6, 1))));
                        } else {
                            to.setPos(
                                    Vec3.atBottomCenterOf(
                                            helper.absolutePos(new BlockPos(5, 2, 5))));
                        }
                        if (index == 1 || index == 3) {
                            fixture(pairs.get(index)[0]).gameMode(GameType.CREATIVE);
                        }
                        InstantTpaWhitelist.add(to, pairs.get(index)[0]);
                        command(pairs.get(index)[0], "tpa " + to.getScoreboardName());
                    }
                });
        at(
                helper,
                2,
                () -> {
                    String drop =
                            " is in the air above a long drop, so your TP request was not accepted automatically.";
                    String unsafe =
                            " is somewhere unsafe, so your TP request was not accepted automatically.";
                    String[][] refusals = {
                        {drop, "a survival requester over a long drop was not refused"},
                        {null, "a creative requester over a long drop was refused"},
                        {unsafe, "a survival requester on a dangerous block was not refused"},
                        {null, "a creative requester on a dangerous block was refused"}
                    };
                    for (int index = 0; index < pairs.size(); index++) {
                        ServerPlayer from = pairs.get(index)[0];
                        boolean moved = !from.position().equals(starts.get(index));
                        String refusal = refusals[index][0];
                        if ((refusal == null) != moved) {
                            fail(
                                    helper,
                                    refusals[index][1]
                                            + ", got "
                                            + from.position()
                                            + " "
                                            + texts(from));
                            return;
                        }
                        if (refusal != null
                                && !received(
                                        from,
                                        "[SimpleTPA] "
                                                + pairs.get(index)[1].getScoreboardName()
                                                + refusal)) {
                            fail(helper, "not told \"" + refusal + "\", got " + texts(from));
                            return;
                        }
                    }
                    succeed(helper);
                });
    }

    /** /tpasafety shows the default, turns the check off and on, and says which it is. */
    public static void safetyCommandShowsAndSets(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    ServerPlayer from = players[0];
                    at(
                            helper,
                            1,
                            () -> {
                                command(from, "tpasafety");
                                if (!received(
                                        from,
                                        "[SimpleTPA] Your TP requests wait while the player you teleport to is in the air above a long drop.")) {
                                    fail(helper, "the default is not on, got " + texts(from));
                                    return;
                                }
                                fixture(from).clearMessages();
                                command(from, "tpasafety off");
                                if (!received(
                                        from,
                                        "[SimpleTPA] Your TP requests go through while the player you teleport to is in the air above a long drop, so you will fall when you arrive.")) {
                                    fail(helper, "no safety-off message, got " + texts(from));
                                    return;
                                }
                                fixture(from).clearMessages();
                                command(from, "tpasafety");
                                if (!received(
                                        from,
                                        "[SimpleTPA] Your TP requests do not wait while the player you teleport to is in the air above a long drop.")) {
                                    fail(helper, "the choice was not shown, got " + texts(from));
                                    return;
                                }
                                fixture(from).clearMessages();
                                command(from, "tpasafety on");
                                if (!received(
                                        from,
                                        "[SimpleTPA] Your TP requests wait while the player you teleport to is in the air above a long drop.")) {
                                    fail(helper, "no safety-on message, got " + texts(from));
                                    return;
                                }
                                fixture(from).clearMessages();
                                command(from, "tpasafety on");
                                Outbound sent = outbound(from);
                                if (!sent.messages()
                                        .equals(
                                                List.of(
                                                        "[SimpleTPA] Your TP requests wait while the player you teleport to is in the air above a long drop."))) {
                                    fail(
                                            helper,
                                            "turning on what is on must show it once, got "
                                                    + sent.messages());
                                    return;
                                }
                                if (sent.clicks() != 1) {
                                    fail(
                                            helper,
                                            "turning on what is on must click once, got "
                                                    + sent.clicks());
                                    return;
                                }
                                succeed(helper);
                            });
                });
    }

    private record Outbound(List<String> messages, int clicks) {}

    /**
     * The chat messages and UI clicks sent to {@code player} since their connection was last
     * drained. Reading the connection takes the messages away from {@link GameTestPlayer#messages}.
     */
    private static Outbound outbound(ServerPlayer player) {
        EmbeddedChannel channel = fixture(player).channel();
        channel.runPendingTasks();
        channel.flushOutbound();
        List<String> messages = new ArrayList<>();
        int clicks = 0;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay)) {
                if (!overlay) {
                    messages.add(content.getString());
                }
            } else if (packet instanceof ClientboundSoundPacket sound
                    && sound.getSound().value() == SoundEvents.UI_BUTTON_CLICK.value()) {
                clicks++;
            }
            ReferenceCountUtil.release(packet);
        }
        return new Outbound(messages, clicks);
    }

    /**
     * One sender-receiver pair per state. {@code act} runs in tick 1; a tick later the sender must
     * not have moved, the request must still be open and {@code toldWho} must have been sent {@code
     * message}.
     */
    private static void checkBlocked(
            GameTestHelper helper,
            List<Blocked> states,
            BiConsumer<Blocked, ServerPlayer[]> act,
            BiFunction<Blocked, ServerPlayer[], String> message,
            Function<ServerPlayer[], ServerPlayer> toldWho) {
        List<ServerPlayer[]> pairs = new ArrayList<>();
        states.forEach(ignored -> pair(helper, pairs::add));
        List<Vec3> starts = pairs.stream().map(players -> players[0].position()).toList();
        at(
                helper,
                1,
                () -> {
                    for (int i = 0; i < states.size(); i++) {
                        act.accept(states.get(i), pairs.get(i));
                    }
                });
        at(
                helper,
                2,
                () -> {
                    for (int i = 0; i < states.size(); i++) {
                        Blocked blocked = states.get(i);
                        ServerPlayer[] players = pairs.get(i);
                        String expected = "[SimpleTPA] " + message.apply(blocked, players);
                        ServerPlayer told = toldWho.apply(players);
                        if (!players[0].position().equals(starts.get(i))) {
                            fail(helper, "teleported while " + blocked.reason());
                            return;
                        }
                        if (!pending(players[1], players[0])) {
                            fail(helper, "the request closed while " + blocked.reason());
                            return;
                        }
                        if (!received(told, expected)) {
                            fail(helper, "not told \"" + expected + "\", got " + texts(told));
                            return;
                        }
                    }
                    succeed(helper);
                });
    }

    public static void requestButtons(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    String sender = players[0].getScoreboardName();
                    UUID senderId = players[0].getUUID();
                    players[0].setPos(new Vec3(0, 0, 0));
                    standOnStone(helper, players[1], new Vec3(10, 10, 10));
                    command(players[0], "tpa " + players[1].getScoreboardName());
                    at(
                            helper,
                            2,
                            () -> {
                                Component notice =
                                        messages(players[1]).stream()
                                                .filter(
                                                        message ->
                                                                message.getString()
                                                                        .contains(
                                                                                "has sent you a TP request"))
                                                .findFirst()
                                                .orElse(null);
                                if (notice == null) {
                                    fail(helper, "receiver got no request notice");
                                    return;
                                }
                                String expectedText =
                                        "[SimpleTPA] "
                                                + sender
                                                + " has sent you a TP request. Use /tpaccept to accept, or /tpdeny to deny. [ACCEPT] [DENY]";
                                if (!notice.getString().equals(expectedText)) {
                                    fail(helper, "notice text was \"" + notice.getString() + "\"");
                                    return;
                                }
                                String accept =
                                        checkButton(
                                                notice,
                                                "[ACCEPT]",
                                                ChatFormatting.GREEN,
                                                "/tpaccept " + senderId,
                                                "Click to accept " + sender + "'s TP request");
                                String deny =
                                        checkButton(
                                                notice,
                                                "[DENY]",
                                                ChatFormatting.RED,
                                                "/tpdeny " + senderId,
                                                "Click to deny " + sender + "'s TP request");
                                if (accept != null || deny != null) {
                                    fail(helper, accept != null ? accept : deny);
                                    return;
                                }
                                List<String> offered = suggestions(players[1], "tpaccept ");
                                if (!offered.equals(List.of(sender))) {
                                    fail(helper, "/tpaccept suggested " + offered);
                                    return;
                                }
                                // Run what the [ACCEPT] button would send, without the leading
                                // slash like the client does.
                                command(players[1], "tpaccept " + senderId);
                            });
                    at(
                            helper,
                            4,
                            () -> {
                                if (players[0].blockPosition().equals(players[1].blockPosition()))
                                    succeed(helper);
                                else fail(helper, "[ACCEPT] button command did not teleport");
                            });
                });
    }

    public static void tpautoAlias(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
                    var subcommands =
                            dispatcher
                                    .getCompletionSuggestions(
                                            dispatcher.parse(
                                                    "tpauto ",
                                                    players[1].createCommandSourceStack()))
                                    .join()
                                    .getList()
                                    .stream()
                                    .map(suggestion -> suggestion.getText())
                                    .collect(Collectors.toSet());
                    if (!subcommands.containsAll(Set.of("*", "list", "add", "remove", "clear"))) {
                        fail(helper, "/tpauto suggested " + subcommands);
                        return;
                    }
                    boolean playerSuggested =
                            dispatcher
                                    .getCompletionSuggestions(
                                            dispatcher.parse(
                                                    "tpauto add ",
                                                    players[1].createCommandSourceStack()))
                                    .join()
                                    .getList()
                                    .stream()
                                    .anyMatch(
                                            suggestion ->
                                                    suggestion
                                                            .getText()
                                                            .equals(
                                                                    players[0]
                                                                            .getScoreboardName()));
                    if (!playerSuggested) {
                        fail(
                                helper,
                                "/tpauto add did not suggest " + players[0].getScoreboardName());
                        return;
                    }
                    at(
                            helper,
                            1,
                            () ->
                                    command(
                                            players[1],
                                            "tpauto add " + players[0].getScoreboardName()));
                    at(
                            helper,
                            2,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                                command(players[0], "tpa " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            4,
                            () -> {
                                if (!InstantTpaWhitelist.list(players[1])
                                        .equals(List.of(players[0].getScoreboardName()))) {
                                    fail(helper, "/tpauto add did not whitelist the sender");
                                } else if (!players[0]
                                        .blockPosition()
                                        .equals(players[1].blockPosition())) {
                                    fail(helper, "/tpauto whitelist did not auto accept");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    /** A whitelisted player who went offline can be removed by name, in any case. */
    public static void removeOfflinePlayer(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    String friend = players[0].getScoreboardName();
                    at(helper, 1, () -> command(players[1], "tpautoaccept add " + friend));
                    at(
                            helper,
                            2,
                            () -> {
                                fixture(players[0]).close();
                                List<String> offered =
                                        suggestions(players[1], "tpautoaccept remove ");
                                if (!offered.equals(List.of(friend))) {
                                    fail(helper, "/tpautoaccept remove suggested " + offered);
                                    return;
                                }
                                command(
                                        players[1],
                                        "tpautoaccept remove " + friend.toUpperCase(Locale.ROOT));
                            });
                    at(
                            helper,
                            3,
                            () -> {
                                if (!InstantTpaWhitelist.list(players[1]).isEmpty())
                                    fail(
                                            helper,
                                            "an offline player was not removed, got "
                                                    + texts(players[1]));
                                else if (!received(
                                        players[1],
                                        "[SimpleTPA] "
                                                + friend
                                                + " was removed from your auto-accept whitelist."))
                                    fail(helper, "owner was not told, got " + texts(players[1]));
                                else succeed(helper);
                            });
                });
    }

    /** A whitelisted player who joins under a new name is listed under that name. */
    public static void whitelistNamesFollowRenames(GameTestHelper helper) {
        int id = ++playerPairCounter;
        ServerPlayer owner = player(helper, "owner" + id);
        UUID friendId = UUID.randomUUID();
        ServerPlayer friend = player(helper, "carl" + id, friendId);
        InstantTpaWhitelist.add(owner, friend);
        fixture(friend).close();
        player(helper, "bob" + id, friendId);
        at(
                helper,
                1,
                () -> {
                    List<String> listed = InstantTpaWhitelist.list(owner);
                    if (!listed.equals(List.of("bob" + id)))
                        fail(helper, "the whitelist still lists " + listed);
                    else succeed(helper);
                });
    }

    /**
     * After two renames, {@code remove <name>} takes the online player with that name, not the
     * offline player whose entry was stored under it. The join hook gives both entries the name
     * {@code bob} before the removal, so a remove that tried stored names first would meet an
     * ambiguous name and fail this test whatever order the whitelist keeps its entries in.
     */
    public static void removePrefersOnlinePlayer(GameTestHelper helper) {
        int id = ++playerPairCounter;
        String bob = "bob" + id;
        ServerPlayer owner = player(helper, "owner" + id);
        ServerPlayer formerBob = player(helper, bob);
        UUID nowBobId = UUID.randomUUID();
        ServerPlayer carl = player(helper, "carl" + id, nowBobId);
        InstantTpaWhitelist.add(owner, formerBob);
        InstantTpaWhitelist.add(owner, carl);
        fixture(formerBob).close();
        fixture(carl).close();
        ServerPlayer nowBob = player(helper, bob, nowBobId);
        at(helper, 1, () -> command(owner, "tpautoaccept remove " + bob));
        at(
                helper,
                2,
                () -> {
                    List<String> listed = InstantTpaWhitelist.list(owner);
                    if (InstantTpaWhitelist.allows(owner, nowBob))
                        fail(
                                helper,
                                "the online " + bob + " is still whitelisted: " + texts(owner));
                    else if (listed.size() != 1)
                        fail(helper, "remove did not take exactly one entry: " + listed);
                    else if (!received(
                            owner,
                            "[SimpleTPA] " + bob + " was removed from your auto-accept whitelist."))
                        fail(helper, "owner was not told, got " + texts(owner));
                    else succeed(helper);
                });
    }

    /**
     * Two offline players stored under one name: {@code remove <name>} takes neither and names both
     * UUIDs, and {@code list} and the suggestions tell the two apart.
     */
    public static void ambiguousNameIsNotRemoved(GameTestHelper helper) {
        int id = ++playerPairCounter;
        String bob = "bob" + id;
        ServerPlayer owner = player(helper, "owner" + id);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ServerPlayer first = player(helper, bob, firstId);
        ServerPlayer second = player(helper, "carl" + id, secondId);
        InstantTpaWhitelist.add(owner, first);
        InstantTpaWhitelist.add(owner, second);
        fixture(first).close();
        fixture(second).close();
        fixture(player(helper, bob, secondId)).close();
        List<String> ids =
                Stream.of(firstId, secondId)
                        .map(UUID::toString)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
        at(helper, 1, () -> command(owner, "tpautoaccept remove " + bob));
        at(
                helper,
                2,
                () -> {
                    List<String> listed = InstantTpaWhitelist.list(owner);
                    List<String> offered = suggestions(owner, "tpautoaccept remove ");
                    if (listed.size() != 2)
                        fail(helper, "remove took an entry of an ambiguous name: " + listed);
                    else if (!listed.equals(
                            ids.stream().map(uuid -> bob + " (" + uuid + ")").toList()))
                        fail(helper, "list does not tell the two apart: " + listed);
                    else if (!offered.equals(ids)) fail(helper, "remove suggested " + offered);
                    else if (texts(owner).stream()
                            .noneMatch(
                                    text ->
                                            text.contains("more than one")
                                                    && text.contains(ids.get(0))
                                                    && text.contains(ids.get(1))))
                        fail(helper, "owner was not given the UUIDs, got " + texts(owner));
                    else succeed(helper);
                });
    }

    public static void acceptAllNeedsConfirmation(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(helper, 1, () -> command(players[1], "tpauto"));
                    at(
                            helper,
                            2,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                                command(players[0], "tpa " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            4,
                            () -> {
                                if (InstantTpaWhitelist.acceptsAll(players[1])) {
                                    fail(helper, "a single /tpauto turned accept-all on");
                                } else if (!received(
                                                players[1],
                                                "[SimpleTPA] Warning: accept-all lets anyone on the server teleport to you at any time without asking.")
                                        || !received(
                                                players[1],
                                                "[SimpleTPA] Run /tpauto again within 30 seconds to turn it on.")) {
                                    fail(helper, "no accept-all warning, got " + texts(players[1]));
                                } else if (!players[0]
                                        .blockPosition()
                                        .equals(new BlockPos(0, 0, 0))) {
                                    fail(helper, "unconfirmed accept-all teleported the requester");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    public static void acceptAllConfirmationExpires(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    int secondRun = 2 + TPAutoAcceptCommand.CONFIRM_WINDOW_TICKS + 2;
                    at(helper, 2, () -> command(players[1], "tpautoaccept *"));
                    at(
                            helper,
                            secondRun,
                            () -> {
                                fixture(players[1]).clearMessages();
                                command(players[1], "tpautoaccept *");
                            });
                    at(
                            helper,
                            secondRun + 2,
                            () -> {
                                if (InstantTpaWhitelist.acceptsAll(players[1]))
                                    fail(helper, "a run after the window confirmed accept-all");
                                else if (!received(
                                        players[1],
                                        "[SimpleTPA] Run /tpautoaccept * again within 30 seconds to turn it on."))
                                    fail(
                                            helper,
                                            "late run did not warn again, got "
                                                    + texts(players[1]));
                                else succeed(helper);
                            });
                });
    }

    public static void acceptAllTeleports(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(helper, 1, () -> command(players[1], "tpautoaccept"));
                    at(helper, 2, () -> command(players[1], "tpautoaccept"));
                    at(
                            helper,
                            3,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                                command(players[0], "tpa " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            5,
                            () -> {
                                if (!received(
                                        players[1],
                                        "[SimpleTPA] Accept-all is now on. Anyone on the server can teleport to you without asking. Run /tpautoaccept again to turn it off.")) {
                                    fail(
                                            helper,
                                            "no accept-all confirmation, got " + texts(players[1]));
                                } else if (!InstantTpaWhitelist.list(players[1]).isEmpty()) {
                                    fail(helper, "accept-all changed the whitelist");
                                } else if (!players[0]
                                        .blockPosition()
                                        .equals(players[1].blockPosition())) {
                                    fail(
                                            helper,
                                            "accept-all did not teleport a requester who is not whitelisted");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    public static void acceptAllToggleOff(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(helper, 1, () -> command(players[1], "tpauto *"));
                    at(helper, 2, () -> command(players[1], "tpauto *"));
                    at(helper, 3, () -> command(players[1], "tpauto *"));
                    at(
                            helper,
                            4,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                                command(players[0], "tpa " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            6,
                            () -> {
                                if (InstantTpaWhitelist.acceptsAll(players[1])) {
                                    fail(helper, "third run did not turn accept-all off");
                                } else if (!received(
                                        players[1],
                                        "[SimpleTPA] Accept-all is now off. Only players on your auto-accept whitelist can teleport to you without asking.")) {
                                    fail(
                                            helper,
                                            "no accept-all off message, got " + texts(players[1]));
                                } else if (!players[0]
                                        .blockPosition()
                                        .equals(new BlockPos(0, 0, 0))) {
                                    fail(
                                            helper,
                                            "requester teleported after accept-all was turned off");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    public static void acceptAllGameruleOff(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(helper, 1, () -> command(players[1], "tpauto"));
                    at(helper, 2, () -> command(players[1], "tpauto"));
                    at(
                            helper,
                            3,
                            () -> {
                                players[0].setPos(new Vec3(0, 0, 0));
                                standOnStone(helper, players[1], new Vec3(10, 10, 10));
                                command(players[0], "tpa " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            5,
                            () -> {
                                if (helper.getLevel()
                                        .getServer()
                                        .getGameRules()
                                        .get(SimpleTPA.AUTO_ACCEPT)) {
                                    fail(
                                            helper,
                                            "test environment did not set simpletpa:auto_accept to false");
                                } else if (!InstantTpaWhitelist.acceptsAll(players[1])) {
                                    fail(helper, "accept-all was not turned on");
                                } else if (!received(
                                        players[1],
                                        "[SimpleTPA] It has no effect while the simpletpa:auto_accept gamerule is false.")) {
                                    fail(helper, "no gamerule note, got " + texts(players[1]));
                                } else if (!players[0].blockPosition().equals(new BlockPos(0, 0, 0))
                                        || !pending(players[1], players[0])) {
                                    fail(helper, "accept-all bypassed simpletpa:auto_accept false");
                                } else {
                                    succeed(helper);
                                }
                            });
                });
    }

    /** Everything runs in one server tick, so other tests' entries round-trip unchanged. */
    public static void acceptAllPersistence(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    at(
                            helper,
                            1,
                            () -> {
                                command(players[1], "tpautoaccept");
                                command(players[1], "tpautoaccept");
                                command(
                                        players[0],
                                        "tpautoaccept add " + players[1].getScoreboardName());
                            });
                    at(
                            helper,
                            3,
                            () -> {
                                var server = helper.getLevel().getServer();
                                Path file =
                                        server.getWorldPath(LevelResource.ROOT)
                                                .resolve("simpletpa-instant-accept-whitelist.json");
                                UUID legacyOwner = UUID.randomUUID();
                                UUID legacyAllowed = UUID.randomUUID();
                                JsonObject saved;
                                try {
                                    InstantTpaWhitelist.save();
                                    saved =
                                            JsonParser.parseString(Files.readString(file))
                                                    .getAsJsonObject();
                                    JsonArray legacy = new JsonArray();
                                    JsonObject entry = new JsonObject();
                                    entry.addProperty("uuid", legacyAllowed.toString());
                                    entry.addProperty("name", "legacyFriend");
                                    legacy.add(entry);
                                    saved.add(legacyOwner.toString(), legacy);
                                    Files.writeString(file, saved.toString());
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }

                                JsonArray acceptAllEntries =
                                        saved.getAsJsonArray(players[1].getUUID().toString());
                                if (acceptAllEntries == null
                                        || acceptAllEntries.size() != 1
                                        || !acceptAllEntries
                                                .get(0)
                                                .getAsJsonObject()
                                                .get("uuid")
                                                .getAsString()
                                                .equals(InstantTpaWhitelist.ACCEPT_ALL_MARKER)) {
                                    fail(helper, "accept-all was saved as " + acceptAllEntries);
                                    return;
                                }

                                InstantTpaWhitelist.setAcceptAll(players[1], false);
                                InstantTpaWhitelist.clear(players[0]);
                                InstantTpaWhitelist.load(server);
                                InstantTpaWhitelist.save();

                                ServerPlayer legacyPlayer =
                                        player(
                                                helper,
                                                "legacyOwner" + playerPairCounter,
                                                legacyOwner);
                                if (!InstantTpaWhitelist.acceptsAll(players[1]))
                                    fail(helper, "accept-all did not survive a reload");
                                else if (InstantTpaWhitelist.acceptsAll(players[0]))
                                    fail(
                                            helper,
                                            "reload turned accept-all on for a whitelist owner");
                                else if (!InstantTpaWhitelist.list(players[0])
                                        .equals(List.of(players[1].getScoreboardName())))
                                    fail(helper, "whitelist did not survive a reload");
                                else if (!InstantTpaWhitelist.list(legacyPlayer)
                                        .equals(List.of("legacyFriend")))
                                    fail(helper, "legacy whitelist entry did not load");
                                else succeed(helper);
                            });
                });
    }

    /** Runs in one tick and restores the file afterwards. */
    public static void whitelistFromPreviousVersionLoads(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var server = helper.getLevel().getServer();
                    Path file =
                            server.getWorldPath(LevelResource.ROOT)
                                    .resolve("simpletpa-instant-accept-whitelist.json");
                    UUID friend = UUID.fromString("00000000-0000-4000-8000-000000000001");
                    String owner = players[0].getUUID().toString();
                    String acceptAllOwner = players[1].getUUID().toString();
                    String whitelistEntry =
                            "\""
                                    + owner
                                    + "\": [\n    {\n      \"uuid\": \""
                                    + friend
                                    + "\",\n      \"name\": \"legacyFriend\"\n    }\n  ]";
                    String acceptAllEntry =
                            "\""
                                    + acceptAllOwner
                                    + "\": [\n    {\n      \"uuid\": \"*\",\n      \"name\": \"everyone\"\n    }\n  ]";
                    String previous =
                            "{\n  "
                                    + (owner.compareTo(acceptAllOwner) < 0
                                            ? whitelistEntry + ",\n  " + acceptAllEntry
                                            : acceptAllEntry + ",\n  " + whitelistEntry)
                                    + "\n}";
                    try {
                        InstantTpaWhitelist.save();
                        byte[] original = Files.readAllBytes(file);
                        try {
                            Files.writeString(file, previous);
                            InstantTpaWhitelist.load(server);
                            if (!InstantTpaWhitelist.list(players[0])
                                    .equals(List.of("legacyFriend"))) {
                                fail(
                                        helper,
                                        "the previous version's whitelist did not load: "
                                                + InstantTpaWhitelist.list(players[0]));
                                return;
                            }
                            if (!InstantTpaWhitelist.acceptsAll(players[1])) {
                                fail(helper, "the previous version's accept-all did not load");
                                return;
                            }
                            InstantTpaWhitelist.save();
                            if (!Files.readString(file).equals(previous)) {
                                fail(
                                        helper,
                                        "saving unchanged settings changed the file to "
                                                + Files.readString(file));
                                return;
                            }
                        } finally {
                            Files.write(file, original);
                            InstantTpaWhitelist.load(server);
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                    succeed(helper);
                });
    }

    /**
     * Runs in one tick and restores the file afterwards, so other tests' entries round-trip
     * unchanged.
     */
    public static void unreadableWhitelistIsNotOverwritten(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var server = helper.getLevel().getServer();
                    Path file =
                            server.getWorldPath(LevelResource.ROOT)
                                    .resolve("simpletpa-instant-accept-whitelist.json");
                    try {
                        InstantTpaWhitelist.save();
                        byte[] original = Files.readAllBytes(file);
                        try {
                            for (String document :
                                    List.of(
                                            "{\"" + players[0].getUUID() + "\": [",
                                            "{\"schemaVersion\": 2, \"owners\": {}}")) {
                                byte[] bytes =
                                        document.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                Files.write(file, bytes);
                                InstantTpaWhitelist.load(server);
                                if (TPAutoAcceptCommand.add(
                                                players[0].createCommandSourceStack(), players[1])
                                        != -1) {
                                    fail(
                                            helper,
                                            "adding to an unreadable whitelist reported success");
                                    return;
                                }
                                if (!InstantTpaWhitelist.list(players[0])
                                        .equals(List.of(players[1].getScoreboardName()))) {
                                    fail(
                                            helper,
                                            "the unsaved whitelist change did not apply for the session");
                                    return;
                                }
                                if (!received(
                                        players[0],
                                        "[SimpleTPA] "
                                                + players[1].getScoreboardName()
                                                + " can instantly teleport to you for this session, but your auto-accept whitelist could not be saved.")) {
                                    fail(
                                            helper,
                                            "the player was not told the change was not saved: "
                                                    + texts(players[0]));
                                    return;
                                }
                                InstantTpaWhitelist.clear(players[0]);
                                InstantTpaWhitelist.save();
                                if (!java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
                                    fail(
                                            helper,
                                            "an unreadable whitelist was overwritten: " + document);
                                    return;
                                }
                            }
                        } finally {
                            Files.write(file, original);
                            InstantTpaWhitelist.load(server);
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                    succeed(helper);
                });
    }

    /**
     * A Fabric server whose start failed still fires SERVER_STOPPING, without having loaded the
     * whitelist. Runs in one tick and loads the file again afterwards.
     */
    public static void whitelistNotSavedBeforeLoad(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var server = helper.getLevel().getServer();
                    Path file =
                            server.getWorldPath(LevelResource.ROOT)
                                    .resolve("simpletpa-instant-accept-whitelist.json");
                    try {
                        InstantTpaWhitelist.add(players[0], players[1]);
                        InstantTpaWhitelist.save();
                        byte[] saved = Files.readAllBytes(file);
                        InstantTpaWhitelist.unload();
                        try {
                            if (InstantTpaWhitelist.save()) {
                                fail(helper, "a whitelist that was never loaded was saved");
                                return;
                            }
                            if (!java.util.Arrays.equals(Files.readAllBytes(file), saved)) {
                                fail(
                                        helper,
                                        "a save before load changed the file to "
                                                + Files.readString(file));
                                return;
                            }
                        } finally {
                            InstantTpaWhitelist.load(server);
                            InstantTpaWhitelist.clear(players[0]);
                            InstantTpaWhitelist.save();
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                    succeed(helper);
                });
    }

    public static void addWildcardRejected(GameTestHelper helper) {
        pair(
                helper,
                players -> {
                    var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
                    var parse =
                            dispatcher.parse(
                                    "tpautoaccept add *", players[1].createCommandSourceStack());
                    if (!parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
                        fail(helper, "/tpautoaccept add * parsed as a command");
                        return;
                    }
                    command(players[1], "tpautoaccept add *");
                    command(players[1], "tpauto add *");
                    at(
                            helper,
                            2,
                            () -> {
                                if (InstantTpaWhitelist.acceptsAll(players[1])
                                        || !InstantTpaWhitelist.list(players[1]).isEmpty())
                                    fail(helper, "add * changed auto-accept settings");
                                else succeed(helper);
                            });
                });
    }

    public static void combinedServerHelp(GameTestHelper helper) {
        if (ServerModHelpRegistry.entries().stream()
                .anyMatch(
                        entry ->
                                entry.modId().equals(SimpleTPA.MOD_ID)
                                        && entry.helpCommand().equals("/simpletpa help")))
            succeed(helper);
        else fail(helper, "missing help");
    }

    public static void gameruleRegistration(GameTestHelper helper) {
        GameRule<?> registered =
                BuiltInRegistries.GAME_RULE.getValue(
                        Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID, "auto_accept"));
        if (SimpleTPA.AUTO_ACCEPT != null && registered == SimpleTPA.AUTO_ACCEPT) succeed(helper);
        else fail(helper, "simpletpa:auto_accept gamerule is not registered");
    }

    /**
     * A world saved by v1.1.0 with {@code simpletpa:allow_instant_tpa_accepting} false keeps
     * auto-accept off, once: after the start's save, the current rule decides.
     */
    public static void legacyGameRuleMigrates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dataFolder = null;
        try {
            dataFolder = Files.createTempDirectory("simpletpa-game-rules");
            CompoundTag rules = new CompoundTag();
            rules.putBoolean(LegacyGameRule.LEGACY_ID.toString(), false);
            writeGameRules(dataFolder, rules);

            try (SavedDataStorage storage = savedData(server, dataFolder)) {
                GameRules loaded = gameRules(server, storage);
                if (!loaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "the game kept the legacy value without the migration");
                    return;
                }
                LegacyGameRule.migrate(storage, dataFolder, loaded);
                if (loaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "a world with the legacy rule false loaded with auto-accept on");
                    return;
                }
                storage.saveAndJoin();
            }
            try (SavedDataStorage storage = savedData(server, dataFolder)) {
                GameRules reloaded = gameRules(server, storage);
                LegacyGameRule.migrate(storage, dataFolder, reloaded);
                if (reloaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "the migrated value was not saved");
                    return;
                }
                reloaded.set(SimpleTPA.AUTO_ACCEPT, true, null);
                storage.saveAndJoin();
            }
            try (SavedDataStorage storage = savedData(server, dataFolder)) {
                GameRules reloaded = gameRules(server, storage);
                LegacyGameRule.migrate(storage, dataFolder, reloaded);
                if (!reloaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "the legacy value overrode auto-accept after the migration");
                    return;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            deleteTree(dataFolder);
        }
        succeed(helper);
    }

    /** A world that saved both names keeps the current rule's value. */
    public static void legacyGameRuleYieldsToCurrentRule(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dataFolder = null;
        try {
            dataFolder = Files.createTempDirectory("simpletpa-game-rules");
            CompoundTag rules = new CompoundTag();
            rules.putBoolean(LegacyGameRule.LEGACY_ID.toString(), false);
            rules.putBoolean(
                    BuiltInRegistries.GAME_RULE.getKey(SimpleTPA.AUTO_ACCEPT).toString(), true);
            writeGameRules(dataFolder, rules);

            try (SavedDataStorage storage = savedData(server, dataFolder)) {
                GameRules loaded = gameRules(server, storage);
                LegacyGameRule.migrate(storage, dataFolder, loaded);
                if (!loaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "the legacy value overrode the saved current rule");
                    return;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            deleteTree(dataFolder);
        }
        succeed(helper);
    }

    /**
     * A game_rules.dat cut short in the middle of its gzip stream, as a crash during a save can
     * leave it, starts the world with default rules in vanilla, and the migration must not stop
     * that start.
     */
    public static void legacyGameRuleSurvivesCorruptFile(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dataFolder = null;
        try {
            dataFolder = Files.createTempDirectory("simpletpa-game-rules");
            CompoundTag rules = new CompoundTag();
            rules.putBoolean(LegacyGameRule.LEGACY_ID.toString(), false);
            for (int i = 0; i < 200; i++) {
                rules.putString("simpletpa:filler_" + i, UUID.randomUUID().toString());
            }
            Path file = writeGameRules(dataFolder, rules);
            byte[] whole = Files.readAllBytes(file);
            Files.write(file, Arrays.copyOf(whole, whole.length / 2));

            try (SavedDataStorage storage = savedData(server, dataFolder)) {
                GameRules loaded = gameRules(server, storage);
                try {
                    LegacyGameRule.migrate(storage, dataFolder, loaded);
                } catch (RuntimeException exception) {
                    fail(helper, "the migration threw " + exception);
                    return;
                }
                if (!loaded.get(SimpleTPA.AUTO_ACCEPT)) {
                    fail(helper, "a corrupt file changed auto-accept");
                    return;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            deleteTree(dataFolder);
        }
        succeed(helper);
    }

    /** Writes {@code rules} as the game rules saved in {@code dataFolder}, as vanilla does. */
    private static Path writeGameRules(Path dataFolder, CompoundTag rules) throws IOException {
        Path file = GameRuleMap.TYPE.id().withSuffix(".dat").resolveAgainst(dataFolder);
        Files.createDirectories(file.getParent());
        CompoundTag saved = new CompoundTag();
        saved.put("data", rules);
        NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(saved), file);
        return file;
    }

    // The server makes its own storage with this level-less constructor (MinecraftServer.java:325).
    // It is the only one Fabric and vanilla have, and the common source set compiles against both.
    // deprecated-api: only NeoForge deprecates it, for one taking a level (SavedDataStorage patch)
    @SuppressWarnings("deprecation")
    private static SavedDataStorage savedData(MinecraftServer server, Path dataFolder) {
        return new SavedDataStorage(dataFolder, server.getFixerUpper(), server.registryAccess());
    }

    /** Opens the game rules as the server's constructor does (MinecraftServer.java:340). */
    private static GameRules gameRules(MinecraftServer server, SavedDataStorage storage) {
        return new GameRules(
                server.getWorldData().enabledFeatures(), storage.computeIfAbsent(GameRuleMap.TYPE));
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String checkButton(
            Component message, String label, ChatFormatting color, String command, String hover) {
        Component button = find(message, label);
        if (button == null) return "no " + label + " button";
        var style = button.getStyle();
        if (!TextColor.fromLegacyFormat(color).equals(style.getColor()))
            return label + " colour was " + style.getColor();
        if (!(style.getClickEvent() instanceof ClickEvent.RunCommand(String run))
                || !run.equals(command)) return label + " click event was " + style.getClickEvent();
        if (!(style.getHoverEvent() instanceof HoverEvent.ShowText(Component text))
                || !text.getString().equals(hover))
            return label + " hover was " + style.getHoverEvent();
        return null;
    }

    private static Component find(Component component, String text) {
        if (component.getSiblings().isEmpty() && component.getString().equals(text))
            return component;
        for (Component sibling : component.getSiblings()) {
            Component found = find(sibling, text);
            if (found != null) return found;
        }
        return null;
    }

    private static List<Component> messages(ServerPlayer player) {
        return fixture(player).messages();
    }

    private static boolean received(ServerPlayer player, String text) {
        return fixture(player).received(text);
    }

    private static boolean pending(ServerPlayer receiver, ServerPlayer sender) {
        return TPRequestCommand.TP_REQUESTS.stream()
                .anyMatch(request -> request.matches(receiver.getUUID(), sender.getUUID()));
    }

    private static List<String> texts(ServerPlayer player) {
        return fixture(player).messageTexts();
    }

    /** What the server suggests to {@code player} at the end of {@code input}. */
    private static List<String> suggestions(ServerPlayer player, String input) {
        var dispatcher = player.level().getServer().getCommands().getDispatcher();
        return dispatcher
                .getCompletionSuggestions(
                        dispatcher.parse(input, player.createCommandSourceStack()))
                .join()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .toList();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return player(helper, name, UUID.randomUUID());
    }

    private static ServerPlayer player(GameTestHelper helper, String name, UUID id) {
        GameTestPlayer fixture =
                GameTestPlayer.join(helper, new GameProfile(id, name)).gameMode(GameType.SURVIVAL);
        synchronized (OPEN_PLAYERS) {
            OPEN_PLAYERS.computeIfAbsent(helper, ignored -> new ArrayList<>()).add(fixture);
        }
        return fixture.player();
    }

    private static GameTestPlayer fixture(ServerPlayer player) {
        synchronized (OPEN_PLAYERS) {
            return OPEN_PLAYERS.values().stream()
                    .flatMap(List::stream)
                    .filter(fixture -> fixture.player() == player)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            player.getScoreboardName()
                                                    + " is not a joined test player"));
        }
    }

    private static void command(ServerPlayer player, String command) {
        fixture(player).command(command);
    }

    private static void pair(GameTestHelper helper, Consumer<ServerPlayer[]> body) {
        int id = ++playerPairCounter;
        body.accept(
                new ServerPlayer[] {
                    player(helper, "sender" + id), player(helper, "receiver" + id)
                });
    }

    /**
     * Runs {@code step} at {@code tick} unless the test has already ended. Vanilla still runs a
     * failed test's later steps (GameTestInfo.java:93-104,122-135), and an error they throw would
     * replace the first failure's message.
     */
    private static void at(GameTestHelper helper, int tick, Runnable step) {
        helper.runAtTickTime(
                tick,
                () -> {
                    synchronized (OPEN_PLAYERS) {
                        if (FINISHED.contains(helper)) {
                            return;
                        }
                    }
                    try {
                        step.run();
                    } catch (RuntimeException exception) {
                        finish(helper);
                        throw exception;
                    }
                });
    }

    private static void succeed(GameTestHelper helper) {
        finish(helper);
        helper.succeed();
    }

    private static void fail(GameTestHelper helper, String message) {
        finish(helper);
        helper.fail(message);
    }

    private static void finish(GameTestHelper helper) {
        List<GameTestPlayer> players;
        synchronized (OPEN_PLAYERS) {
            FINISHED.add(helper);
            players = OPEN_PLAYERS.remove(helper);
        }
        if (players != null) {
            players.forEach(GameTestPlayer::close);
        }
    }
}

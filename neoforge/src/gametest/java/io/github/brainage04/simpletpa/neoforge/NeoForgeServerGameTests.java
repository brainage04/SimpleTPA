package io.github.brainage04.simpletpa.neoforge;

import com.mojang.authlib.GameProfile;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.simpletpa.SimpleTPA;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid = SimpleTPA.MOD_ID)
public final class NeoForgeServerGameTests {
 private record TestCase(String path, Consumer<GameTestHelper> function) {}
 private static int playerPairCounter;
 private static final List<TestCase> TESTS=List.of(new TestCase("accept_flows",NeoForgeServerGameTests::accept),new TestCase("deny_flows",NeoForgeServerGameTests::deny),new TestCase("instant_auto_accept_flow",NeoForgeServerGameTests::instant),new TestCase("combined_server_help",NeoForgeServerGameTests::help),new TestCase("gamerule_registration",NeoForgeServerGameTests::gamerule));
 @SubscribeEvent public static void registerTestFunctions(RegisterEvent e){for(TestCase t:TESTS)e.register(BuiltInRegistries.TEST_FUNCTION.key(),Identifier.fromNamespaceAndPath(SimpleTPA.MOD_ID,t.path()),t::function);}
 private static ServerPlayer player(GameTestHelper h,String n){ServerLevel l=h.getLevel();var c=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),n),false);ServerPlayer p=new ServerPlayer(l.getServer(),l,c.gameProfile(),c.clientInformation()){public GameType gameMode(){return GameType.SPECTATOR;}};Connection x=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(x);l.getServer().getPlayerList().placeNewPlayer(x,p,c);return p;}
 private static void cmd(ServerPlayer p,String c){p.connection.handleChatCommand(new ServerboundChatCommandPacket(c));}
 private static void pair(GameTestHelper h,Consumer<ServerPlayer[]> body){int id=++playerPairCounter;body.accept(new ServerPlayer[]{player(h,"sender"+id),player(h,"receiver"+id)});}
 private static void accept(GameTestHelper h){pair(h,p->{p[0].setPos(new Vec3(0,0,0));p[1].setPos(new Vec3(10,10,10));cmd(p[0],"tprequest "+p[1].getScoreboardName());cmd(p[1],"tpaccept");h.runAtTickTime(2,()->{if(p[0].blockPosition().equals(p[1].blockPosition()))h.succeed();else h.fail("accepted request did not teleport");});});}
 private static void deny(GameTestHelper h){pair(h,p->{p[0].setPos(new Vec3(0,0,0));p[1].setPos(new Vec3(10,10,10));cmd(p[0],"tprequest "+p[1].getScoreboardName());cmd(p[1],"tpdeny");h.runAtTickTime(2,()->{if(p[0].blockPosition().equals(new net.minecraft.core.BlockPos(0,0,0)))h.succeed();else h.fail("denied request teleported");});});}
 private static void help(GameTestHelper h){if(ServerModHelpRegistry.entries().stream().anyMatch(x->x.modId().equals(SimpleTPA.MOD_ID)&&x.helpCommand().equals("/simpletpa help")))h.succeed();else h.fail("missing help");}
 private static void instant(GameTestHelper h){pair(h,p->{h.runAtTickTime(1,()->cmd(p[1],"tpautoaccept add "+p[0].getScoreboardName()));h.runAtTickTime(2,()->{p[0].setPos(new Vec3(0,0,0));p[1].setPos(new Vec3(10,10,10));});h.runAtTickTime(3,()->cmd(p[0],"tprequest "+p[1].getScoreboardName()));h.runAtTickTime(4,()->{if(p[0].blockPosition().equals(p[1].blockPosition()))h.succeed();else h.fail("whitelist did not auto accept");});});}
 private static void gamerule(GameTestHelper h){if(SimpleTPA.ALLOW_INSTANT_TPA_ACCEPTING!=null)h.succeed();else h.fail("missing gamerule");}
}

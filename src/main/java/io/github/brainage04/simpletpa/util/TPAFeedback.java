package io.github.brainage04.simpletpa.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class TPAFeedback {
	private TPAFeedback() {
	}

	public static void click(ServerPlayer player) {
		player.connection.send(new ClientboundSoundPacket(
				SoundEvents.UI_BUTTON_CLICK,
				SoundSource.UI,
				player.getX(),
				player.getY(),
				player.getZ(),
				0.7F,
				1.0F,
				0L
		));
	}

	public static void neutral(ServerPlayer player, String message, Object... args) {
		player.sendSystemMessage(message(ChatFormatting.YELLOW, message, args));
	}

	public static void success(ServerPlayer player, String message, Object... args) {
		player.sendSystemMessage(message(ChatFormatting.GREEN, message, args));
	}

	public static void error(ServerPlayer player, String message, Object... args) {
		player.sendSystemMessage(message(ChatFormatting.RED, message, args));
	}

	public static void fail(CommandSourceStack source, String message, Object... args) {
		source.sendFailure(message(ChatFormatting.RED, message, args));
	}

	private static Component message(ChatFormatting formatting, String message, Object... args) {
		return Component.literal("[SimpleTPA] ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(message.formatted(args)).withStyle(formatting));
	}
}

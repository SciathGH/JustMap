package ru.bulldog.justmap.mixins.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.util.telemetry.TelemetrySender;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.map.MapGameRules;
import ru.bulldog.justmap.map.data.MapDataProvider;
import ru.bulldog.justmap.map.multiworld.WorldKey;
import ru.bulldog.justmap.map.waypoint.Waypoint;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 100)
public abstract class ClientPlayNetworkHandlerMixin {

	@Final
	@Shadow
	private MinecraftClient client;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void onConnect(MinecraftClient client, Screen screen, ClientConnection connection, GameProfile profile, TelemetrySender telemetrySender, CallbackInfo cinfo) {
		MapDataProvider.getMultiworldManager().onServerConnect();
	}

	@Inject(method = "onPlayerSpawnPosition", at = @At("TAIL"))
	public void onPlayerSpawnPosition(PlayerSpawnPositionS2CPacket packet, CallbackInfo cinfo) {
		JustMap.LOGGER.debug("World spawn position set to {}", packet.getPos().toShortString());
		MapDataProvider.getMultiworldManager().onWorldSpawnPosChanged(packet.getPos());
	}

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	public void onGameMessage(ChatMessageS2CPacket chatMessageS2CPacket, CallbackInfo cinfo) {
		if (chatMessageS2CPacket.serializedParameters().equals(JustMap.MESSAGE_ID)) {
			String pref = "§0§0", suff = "§f§f";
			String message = chatMessageS2CPacket.message().getContent().getString().replaceAll("[&$]", "§");

			if (message.contains(pref) && message.contains(suff)) {
				int start = message.indexOf(pref) + 4;
				int end = message.indexOf(suff);

				MapGameRules.parseCommand(message.substring(start, end));

				if (message.matches("^§0§0.+§f§f$")) {
					cinfo.cancel();
				}
			}
		}
	}

	@Inject(method = "onHealthUpdate", at = @At("TAIL"))
	public void onHealthUpdate(HealthUpdateS2CPacket healthUpdateS2CPacket, CallbackInfo cinfo) {
		float health = healthUpdateS2CPacket.getHealth();
		if (health <= 0.0F) {
			WorldKey world = MapDataProvider.getMultiworldManager().getCurrentWorldKey();
			BlockPos playerPos = this.client.player.getBlockPos();
			Waypoint.createOnDeath(world, playerPos);
		}
	}
}

package ru.bulldog.justmap.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.util.math.ChunkPos;

import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.map.data.RegionPos;

public class ClientNetworkHandler extends NetworkHandler {
	private final Map<Integer, Consumer<?>> responseListeners = new HashMap<>();
	private boolean serverReady = false;
	private Random random;

	public void registerPacketsListeners() {
		ClientPlayNetworking.registerGlobalReceiver(INIT_PACKET_ID, (client, handler, buf, responseSender) -> {
			long seed = buf.readLong();
			this.random = new Random(seed);
			this.serverReady = true;
			JustMap.LOGGER.info("Networking successfully initialized.");
		});
		ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, (client, handler, buf, responseSender) -> {
			ByteBuf packetData = buf.copy();
			PacketType packetType = PacketType.get(packetData.readByte());
			switch (packetType) {
			case SLIME_CHUNK_PACKET: {
				client.execute(() -> this.onChunkHasSlimeResponse(packetData));
				break;
			}
			case GET_IMAGE_PACKET: {
				client.execute(() -> this.onRegionImageResponse(packetData));
				break;
			}
			}
		});
	}

	public boolean canRequestData() {
		return serverReady;
	}

	public void requestChunkHasSlime(ChunkPos chunkPos, Consumer<Boolean> responseConsumer) {
		if (!canServerReceive()) return;
		int packet_id = this.registerResponseConsumer(responseConsumer);
		PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
		data.writeByte(PacketType.SLIME_CHUNK_PACKET.ordinal());
		data.writeInt(packet_id);
		data.writeInt(chunkPos.x);
		data.writeInt(chunkPos.z);
		CustomPayloadC2SPacket packet = new CustomPayloadC2SPacket(CHANNEL_ID, data);
		this.sendToServer(packet);
	}

	private void onChunkHasSlimeResponse(ByteBuf data) {
		int packet_id = data.readInt();
		boolean result = data.readBoolean();
		if (responseListeners.containsKey(packet_id)) {
			@SuppressWarnings("unchecked")
			Consumer<Boolean> responseConsumer = (Consumer<Boolean>) responseListeners.get(packet_id);
			responseConsumer.accept(result);
		}
	}

	public void requestRegionImage(RegionPos regionPos, Consumer<byte[]> responseConsumer) {
		this.registerResponseConsumer(responseConsumer);
	}

	private void onRegionImageResponse(ByteBuf data) {

	}

	private int registerResponseConsumer(Consumer<?> responseConsumer) {
		int request_id = random.nextInt();
		this.responseListeners.put(request_id, responseConsumer);
		return request_id;
	}
}

package ru.bulldog.justmap.mixins.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.bulldog.justmap.client.render.WaypointRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {

	@Final
	@Shadow
	private Camera camera;

	@Final
	@Shadow
	MinecraftClient client;

	@Final
	@Shadow
	private BufferBuilderStorage buffers;

	@Shadow
	protected abstract double getFov(Camera camera, float f, boolean bl);

	@Inject(method = "render", at = @At("RETURN"))
	public void renderHUD(float f, long l, boolean bl, CallbackInfo ci) {
		DrawContext context = new DrawContext(this.client, this.buffers.getEntityVertexConsumers());
		WaypointRenderer.renderHUD(context, f, (float) this.getFov(camera, f, true));
	}
}

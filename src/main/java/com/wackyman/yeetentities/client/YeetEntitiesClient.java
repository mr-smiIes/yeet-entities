package com.wackyman.yeetentities.client;

import com.wackyman.yeetentities.network.YeetNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class YeetEntitiesClient implements ClientModInitializer {
    private static int heldEntityId = -1;

    public static void setHeldEntity(int id) { heldEntityId = id; }

    @Override
    public void onInitializeClient() {
        YeetNetwork.registerClient();
        WorldRenderEvents.AFTER_ENTITIES.register(context -> renderHeld(context.matrixStack(), context.consumers(), context.camera().getPos(), context.tickDelta()));
    }

    private static void renderHeld(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cameraPos, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (heldEntityId == -1 || client.world == null || client.player == null) return;
        Entity entity = client.world.getEntityById(heldEntityId);
        if (entity == null || !entity.isAlive()) return;

        Vector3f right = new Vector3f(1, 0, 0);
        client.gameRenderer.getCamera().getRotation().transform(right);
        Vec3d pos = client.player.getEyePos().add(right.x() * 0.48, -0.35, right.z() * 0.48).add(client.player.getRotationVec(tickDelta).multiply(0.35));
        matrices.push();
        matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        float scale = Math.max(0.35f, Math.min(1.0f, 1.2f / Math.max(entity.getWidth(), entity.getHeight())));
        matrices.scale(scale, scale, scale);
        client.getEntityRenderDispatcher().render(entity, 0, 0, 0, entity.getYaw(), tickDelta, matrices, consumers, 15728880);
        matrices.pop();
    }
}

package com.wackyman.yeetentities.client;

import com.wackyman.yeetentities.network.YeetNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

public class YeetEntitiesClient implements ClientModInitializer {

    private static int heldEntityId = -1;
    private static boolean wasRightClickDown = false;

    public static void setHeldEntity(int id) {
        heldEntityId = id;
    }

    public static boolean isHoldingEntity() {
        return heldEntityId != -1;
    }

    @Override
    public void onInitializeClient() {

        YeetNetwork.registerClient();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null || client.world == null) {
                wasRightClickDown = false;
                return;
            }

            boolean rightClickDown =
                    GLFW.glfwGetMouseButton(
                            client.getWindow().getHandle(),
                            GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    ) == GLFW.GLFW_PRESS;

            // Only trigger once per click, not every frame while held.
            if (rightClickDown && !wasRightClickDown && heldEntityId != -1) {
                YeetNetwork.sendYeet();

                // Prevent vanilla right-click from being treated as a block/item use
                // during the same click.
                client.options.useKey.setPressed(false);
            }

            wasRightClickDown = rightClickDown;

            // Keep the use key from repeatedly interacting with blocks/items
            // while we're carrying something.
            if (heldEntityId != -1) {
                client.options.useKey.setPressed(false);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                renderHeld(
                        context.matrixStack(),
                        context.consumers(),
                        context.camera().getPos(),
                        context.tickDelta()
                )
        );
    }

    private static void renderHeld(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Vec3d cameraPos,
            float tickDelta
    ) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (heldEntityId == -1 ||
                client.world == null ||
                client.player == null) {
            return;
        }

        Entity entity = client.world.getEntityById(heldEntityId);

        if (entity == null || !entity.isAlive()) {
            return;
        }

        /*
         * Position approximately at the player's right hand.
         *
         * We intentionally render the entity ourselves instead of relying
         * on its normal world renderer.
         */

        Vector3f right = new Vector3f(1, 0, 0);
        client.gameRenderer.getCamera().getRotation().transform(right);

        Vec3d forward = client.player.getRotationVec(tickDelta);

        Vec3d pos = client.player.getEyePos()
                .add(right.x() * 0.55, -0.55, right.z() * 0.55)
                .add(forward.multiply(0.25));

        matrices.push();

        matrices.translate(
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z
        );

        float biggestDimension =
                Math.max(entity.getWidth(), entity.getHeight());

        float scale =
                Math.max(
                        0.25f,
                        Math.min(0.85f, 1.0f / Math.max(biggestDimension, 1.0f))
                );

        matrices.scale(scale, scale, scale);

        client.getEntityRenderDispatcher().render(
                entity,
                0,
                0,
                0,
                entity.getYaw(),
                tickDelta,
                matrices,
                consumers,
                15728880
        );

        matrices.pop();
    }
}

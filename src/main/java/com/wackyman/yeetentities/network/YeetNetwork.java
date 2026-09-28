package com.wackyman.yeetentities.network;

import com.wackyman.yeetentities.YeetEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class YeetNetwork {

    public static final Identifier HELD_ENTITY =
            new Identifier(YeetEntities.MOD_ID, "held_entity");

    public static final Identifier YEET =
            new Identifier(YeetEntities.MOD_ID, "yeet");

    private YeetNetwork() {
    }

    // Server -> Client: tell the client which entity is being held.
    public static void sendHeld(ServerPlayerEntity player, int entityId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        ServerPlayNetworking.send(player, HELD_ENTITY, buf);
    }

    // Client -> Server: request a throw.
    public static void sendYeet() {
        ClientPlayNetworking.send(YEET, PacketByteBufs.empty());
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(
                YEET,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> YeetEntities.yeetFromNetwork(player));
                }
        );
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                HELD_ENTITY,
                (client, handler, buf, responseSender) -> {
                    int id = buf.readInt();

                    client.execute(() ->
                            com.wackyman.yeetentities.client.YeetEntitiesClient.setHeldEntity(id)
                    );
                }
        );
    }
}

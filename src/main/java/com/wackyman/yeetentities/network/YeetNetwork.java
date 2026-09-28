package com.wackyman.yeetentities.network;

import com.wackyman.yeetentities.YeetEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class YeetNetwork {
    public static final Identifier HELD_ENTITY = new Identifier(YeetEntities.MOD_ID, "held_entity");

    public static void sendHeld(net.minecraft.server.network.ServerPlayerEntity player, int entityId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        ServerPlayNetworking.send(player, HELD_ENTITY, buf);
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(HELD_ENTITY, (client, handler, buf, responseSender) -> {
            int id = buf.readInt();
            client.execute(() -> com.wackyman.yeetentities.client.YeetEntitiesClient.setHeldEntity(id));
        });
    }
}

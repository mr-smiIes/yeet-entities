package com.wackyman.yeetentities;

import com.wackyman.yeetentities.config.YeetConfig;
import com.wackyman.yeetentities.network.YeetNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class YeetEntities implements ModInitializer {
    public static final String MOD_ID = "yeetentities";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String THROWN_KEY = "YeetThrown";
    private static final Map<UUID, Vec3d> LAST_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HELD_ENTITIES = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        YeetConfig.load();

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && hand == Hand.MAIN_HAND && player.isSneaking() && player instanceof ServerPlayerEntity serverPlayer) {
                if (!canPickUp(player, entity)) return ActionResult.PASS;
                pickup(serverPlayer, entity);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity serverPlayer) {
                if (getHeldEntity(serverPlayer) != null) {
                    yeet(serverPlayer);
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> YeetNetwork.sendHeld(handler.player, getHeldId(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { LAST_POSITIONS.remove(handler.player.getUuid()); HELD_ENTITIES.remove(handler.player.getUuid()); });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Entity held = getHeldEntity(player);
                if (held != null) {
                    held.setPos(player.getX(), player.getY() + 1.2, player.getZ());
                    held.setVelocity(Vec3d.ZERO);
                    held.setNoGravity(true);
                    held.setInvisible(true);
                } else if (getHeldId(player) != -1) {
                    clearHeld(player);
                }
            }
            for (ServerWorld world : server.getWorlds()) tickThrown(world);
        });
    }

    private static boolean canPickUp(PlayerEntity player, Entity entity) {
        YeetConfig c = YeetConfig.get();
        if (entity == player || entity.isRemoved() || entity.isSpectator()) return false;
        if (!c.allowPlayers && entity instanceof PlayerEntity) return false;
        if (player.squaredDistanceTo(entity) > c.pickupRange * c.pickupRange) return false;
        if (entity.getCommandTags().contains("yeet_held")) return false;
        return getHeldEntity((ServerPlayerEntity) player) == null;
    }

    private static void pickup(ServerPlayerEntity player, Entity entity) {
        entity.getCommandTags().add("yeet_held");
        entity.setInvisible(true);
        entity.setNoGravity(true);
        entity.setVelocity(Vec3d.ZERO);
        entity.setPos(player.getX(), player.getY() + 1.2, player.getZ());
        HELD_ENTITIES.put(player.getUuid(), entity.getId());
        YeetNetwork.sendHeld(player, entity.getId());
        if (YeetConfig.get().playSounds) player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    private static void yeet(ServerPlayerEntity player) {
        Entity entity = getHeldEntity(player);
        if (entity == null) { clearHeld(player); return; }

        clearHeld(player);
        entity.getCommandTags().remove("yeet_held");
        entity.setInvisible(false);
        entity.setNoGravity(YeetConfig.get().preserveNoGravity);
        Vec3d direction = player.getRotationVec(1.0f).normalize();
        Vec3d start = player.getEyePos().add(direction.multiply(0.7));
        entity.refreshPositionAndAngles(start.x, start.y - entity.getHeight() * 0.35, start.z, entity.getYaw(), entity.getPitch());
        entity.setVelocity(direction.multiply(YeetConfig.get().throwSpeed));
        entity.getCommandTags().add(THROWN_KEY);
        LAST_POSITIONS.put(entity.getUuid(), entity.getPos());
        if (YeetConfig.get().playSounds) player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.9f, 1.2f);
    }

    private static void tickThrown(ServerWorld world) {
        YeetConfig c = YeetConfig.get();
        for (Entity entity : world.iterateEntities()) {
            if (!entity.getCommandTags().contains(THROWN_KEY)) continue;
            Vec3d oldPos = LAST_POSITIONS.getOrDefault(entity.getUuid(), entity.getPos());
            Vec3d newPos = entity.getPos();
            HitResult blockHit = world.raycast(new RaycastContext(oldPos, newPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
            if (c.killOnBlockHit && blockHit.getType() == HitResult.Type.BLOCK) {
                impact(world, entity, blockHit.getPos());
                kill(entity, world);
                continue;
            }

            Box search = entity.getBoundingBox().expand(0.25);
            List<Entity> hits = world.getOtherEntities(entity, search, e -> e.isAlive() && !e.getCommandTags().contains(THROWN_KEY));
            if (!hits.isEmpty()) {
                Entity target = hits.get(0);
                if (c.killTargetOnEntityHit) kill(target, world);
                if (c.killThrownEntityOnEntityHit) kill(entity, world);
                impact(world, entity, target.getPos());
                continue;
            }
            LAST_POSITIONS.put(entity.getUuid(), newPos);
        }
    }

    private static void impact(ServerWorld world, Entity entity, Vec3d pos) {
        if (YeetConfig.get().playSounds) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.7f, 1.5f, world.getRandom().nextLong());
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, pos.x, pos.y, pos.z, YeetConfig.get().impactParticles, 0.35, 0.35, 0.35, 0.2);
        LAST_POSITIONS.remove(entity.getUuid());
    }

    private static void kill(Entity entity, ServerWorld world) {
        entity.getCommandTags().remove(THROWN_KEY);
        entity.setInvisible(false);
        entity.damage(world.getDamageSources().generic(), Float.MAX_VALUE);
        if (!entity.isRemoved()) entity.discard();
    }

    private static Entity getHeldEntity(ServerPlayerEntity player) {
        int id = getHeldId(player);
        if (id == -1) return null;
        return player.getServerWorld().getEntityById(id);
    }

    private static int getHeldId(ServerPlayerEntity player) {
        return HELD_ENTITIES.getOrDefault(player.getUuid(), -1);
    }

    private static void clearHeld(ServerPlayerEntity player) {
        Entity entity = getHeldEntity(player);
        if (entity != null) entity.getCommandTags().remove("yeet_held");
        HELD_ENTITIES.remove(player.getUuid());
        YeetNetwork.sendHeld(player, -1);
    }
}

package com.wackyman.yeetentities;

import com.wackyman.yeetentities.config.YeetConfig;
import com.wackyman.yeetentities.network.YeetNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YeetEntities implements ModInitializer {

    public static final String MOD_ID = "yeetentities";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String THROWN_KEY = "YeetThrown";
    private static final String HELD_KEY = "yeet_held";

    private static final Map<UUID, Vec3d> LAST_POSITIONS =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> HELD_ENTITIES =
            new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {

        YeetConfig.load();

        /*
         * Register the client -> server YEET packet.
         */
        YeetNetwork.registerServer();

        /*
         * SHIFT + LEFT CLICK ENTITY
         *
         * Picks the entity up.
         */
        AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {

                    if (!world.isClient
                            && hand == Hand.MAIN_HAND
                            && player.isSneaking()
                            && player instanceof ServerPlayerEntity serverPlayer) {

                        if (!canPickUp(player, entity)) {
                            return ActionResult.PASS;
                        }

                        pickup(serverPlayer, entity);

                        return ActionResult.SUCCESS;
                    }

                    return ActionResult.PASS;
                }
        );

        /*
         * RIGHT CLICK ENTITY WHILE HOLDING SOMETHING
         *
         * This prevents Minecraft from interacting with the entity
         * and throws the held entity instead.
         */
        UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {

                    if (!world.isClient
                            && hand == Hand.MAIN_HAND
                            && player instanceof ServerPlayerEntity serverPlayer
                            && getHeldEntity(serverPlayer) != null) {

                        yeet(serverPlayer);

                        return ActionResult.SUCCESS;
                    }

                    return ActionResult.PASS;
                }
        );

        /*
         * RIGHT CLICK BLOCK WHILE HOLDING AN ENTITY
         *
         * Prevents chests, doors, buttons, etc. from activating
         * while carrying an entity.
         */
        UseBlockCallback.EVENT.register(
                (player, world, hand, hitResult) -> {

                    if (!world.isClient
                            && hand == Hand.MAIN_HAND
                            && player instanceof ServerPlayerEntity serverPlayer
                            && getHeldEntity(serverPlayer) != null) {

                        yeet(serverPlayer);

                        return ActionResult.SUCCESS;
                    }

                    return ActionResult.PASS;
                }
        );

        /*
         * Tell the client which entity is currently being held
         * when they join the world.
         */
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        YeetNetwork.sendHeld(
                                handler.player,
                                getHeldId(handler.player)
                        )
        );

        /*
         * Clean up when a player disconnects.
         */
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {

                    LAST_POSITIONS.remove(
                            handler.player.getUuid()
                    );

                    HELD_ENTITIES.remove(
                            handler.player.getUuid()
                    );
                }
        );

        /*
         * Server tick.
         */
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            /*
             * Keep held entities attached to their player.
             */
            for (ServerPlayerEntity player :
                    server.getPlayerManager().getPlayerList()) {

                Entity held = getHeldEntity(player);

                if (held != null) {

                    held.setPos(
                            player.getX(),
                            player.getY() + 1.0,
                            player.getZ()
                    );

                    held.setVelocity(Vec3d.ZERO);

                    held.setNoGravity(true);

                    

                } else if (getHeldId(player) != -1) {

                    clearHeld(player);
                }
            }

            /*
             * Check every thrown entity for collisions.
             */
            for (ServerWorld world : server.getWorlds()) {
                tickThrown(world);
            }
        });
    }

    /*
     * Called by the network packet when the client right-clicks.
     */
    public static void yeetFromNetwork(ServerPlayerEntity player) {

        if (getHeldEntity(player) != null) {
            yeet(player);
        }
    }

    /*
     * Determines whether an entity can be picked up.
     */
    private static boolean canPickUp(
            PlayerEntity player,
            Entity entity) {

        YeetConfig c = YeetConfig.get();

        if (entity == player) {
            return false;
        }

        if (entity.isRemoved()) {
            return false;
        }

        if (entity.isSpectator()) {
            return false;
        }

        if (!c.allowPlayers && entity instanceof PlayerEntity) {
            return false;
        }

        if (player.squaredDistanceTo(entity)
                > c.pickupRange * c.pickupRange) {

            return false;
        }

        if (entity.getCommandTags().contains(HELD_KEY)) {
            return false;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }

        return getHeldEntity(serverPlayer) == null;
    }

    /*
     * Pick an entity up.
     */
    private static void pickup(
            ServerPlayerEntity player,
            Entity entity) {

        entity.getCommandTags().add(HELD_KEY);

        entity.setNoGravity(true);


        entity.setVelocity(Vec3d.ZERO);

        entity.setPos(
                player.getX(),
                player.getY() + 1.0,
                player.getZ()
        );

        HELD_ENTITIES.put(
                player.getUuid(),
                entity.getId()
        );

        YeetNetwork.sendHeld(
                player,
                entity.getId()
        );

        if (YeetConfig.get().playSounds) {

            player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                    SoundCategory.PLAYERS,
                    0.7f,
                    1.4f
            );
        }
    }

    /*
     * Throw the currently held entity.
     */
    private static void yeet(
            ServerPlayerEntity player) {

        Entity entity = getHeldEntity(player);

        if (entity == null) {

            clearHeld(player);

            return;
        }

        /*
         * Remove the held state.
         */
        clearHeld(player);

        entity.getCommandTags().remove(HELD_KEY);

        /*
         * Turn collision back on.
         */
        

        entity.setNoGravity(
                YeetConfig.get().preserveNoGravity
        );

        /*
         * Direction the player is looking.
         */
        Vec3d direction =
                player.getRotationVec(1.0f).normalize();

        /*
         * Start slightly in front of the player.
         */
        Vec3d start =
                player.getEyePos()
                        .add(direction.multiply(0.8));

        entity.refreshPositionAndAngles(
                start.x,
                start.y - entity.getHeight() * 0.35,
                start.z,
                entity.getYaw(),
                entity.getPitch()
        );

        /*
         * YEET SPEED.
         */
        entity.setVelocity(
                direction.multiply(
                        YeetConfig.get().throwSpeed
                )
        );

        /*
         * Mark it as thrown.
         */
        entity.getCommandTags().add(THROWN_KEY);

        LAST_POSITIONS.put(
                entity.getUuid(),
                entity.getPos()
        );

        if (YeetConfig.get().playSounds) {

            player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                    SoundCategory.PLAYERS,
                    0.9f,
                    1.2f
            );
        }
    }

    /*
     * Check thrown entities for block/entity collisions.
     */
    private static void tickThrown(ServerWorld world) {

        YeetConfig c = YeetConfig.get();

        for (Entity entity : world.iterateEntities()) {

            if (!entity.getCommandTags().contains(THROWN_KEY)) {
                continue;
            }

            /*
             * Position from the previous tick.
             */
            Vec3d oldPos =
                    LAST_POSITIONS.getOrDefault(
                            entity.getUuid(),
                            entity.getPos()
                    );

            /*
             * Current position.
             */
            Vec3d newPos = entity.getPos();

            /*
             * Check for blocks between the two positions.
             */
            HitResult blockHit =
                    world.raycast(
                            new RaycastContext(
                                    oldPos,
                                    newPos,
                                    RaycastContext.ShapeType.COLLIDER,
                                    RaycastContext.FluidHandling.NONE,
                                    entity
                            )
                    );

            /*
             * HIT BLOCK
             */
            if (c.killOnBlockHit
                    && blockHit.getType() == HitResult.Type.BLOCK) {

                impact(
                        world,
                        entity,
                        blockHit.getPos()
                );

                kill(
                        entity,
                        world
                );

                continue;
            }

            /*
             * HIT ENTITY
             */
            Box search =
                    entity.getBoundingBox().expand(0.25);

            List<Entity> hits =
                    world.getOtherEntities(
                            entity,
                            search,
                            e ->
                                    e.isAlive()
                                            && !e.getCommandTags()
                                            .contains(THROWN_KEY)
                                            && !e.getCommandTags()
                                            .contains(HELD_KEY)
                    );

            if (!hits.isEmpty()) {

                Entity target = hits.get(0);

                /*
                 * Kill the target.
                 */
                if (c.killTargetOnEntityHit) {

                    kill(
                            target,
                            world
                    );
                }

                /*
                 * Kill the thrown entity.
                 */
                if (c.killThrownEntityOnEntityHit) {

                    kill(
                            entity,
                            world
                    );
                }

                impact(
                        world,
                        entity,
                        target.getPos()
                );

                continue;
            }

            /*
             * Remember this position for the next tick.
             */
            LAST_POSITIONS.put(
                    entity.getUuid(),
                    newPos
            );
        }
    }

    /*
     * Impact effects.
     */
    private static void impact(
            ServerWorld world,
            Entity entity,
            Vec3d pos) {

        if (YeetConfig.get().playSounds) {

            world.playSound(
                    null,
                    pos.x,
                    pos.y,
                    pos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.HOSTILE,
                    0.7f,
                    1.5f,
                    world.getRandom().nextLong()
            );
        }

        world.spawnParticles(
                net.minecraft.particle.ParticleTypes.CRIT,
                pos.x,
                pos.y,
                pos.z,
                YeetConfig.get().impactParticles,
                0.35,
                0.35,
                0.35,
                0.2
        );

        LAST_POSITIONS.remove(
                entity.getUuid()
        );
    }

    /*
     * Kill an entity.
     */
    private static void kill(
            Entity entity,
            ServerWorld world) {

        entity.getCommandTags().remove(THROWN_KEY);

        entity.getCommandTags().remove(HELD_KEY);

        

        entity.setNoGravity(false);

        /*
         * Try normal damage first.
         */
        entity.damage(
                world.getDamageSources().generic(),
                Float.MAX_VALUE
        );

        /*
         * Some entities cannot be killed through normal damage,
         * so make absolutely sure the thrown entity disappears.
         */
        if (!entity.isRemoved()) {
            entity.discard();
        }

        LAST_POSITIONS.remove(
                entity.getUuid()
        );
    }

    /*
     * Get the entity being held by a player.
     */
    private static Entity getHeldEntity(
            ServerPlayerEntity player) {

        int id = getHeldId(player);

        if (id == -1) {
            return null;
        }

        return player.getServerWorld()
                .getEntityById(id);
    }

    /*
     * Get the held entity ID.
     */
    private static int getHeldId(
            ServerPlayerEntity player) {

        return HELD_ENTITIES.getOrDefault(
                player.getUuid(),
                -1
        );
    }

    /*
     * Stop holding the current entity.
     */
    private static void clearHeld(
            ServerPlayerEntity player) {

        Entity entity =
                getHeldEntity(player);

        if (entity != null) {

            entity.getCommandTags()
                    .remove(HELD_KEY);

            

            entity.setNoGravity(false);
        }

        HELD_ENTITIES.remove(
                player.getUuid()
        );

        YeetNetwork.sendHeld(
                player,
                -1
        );
    }
}

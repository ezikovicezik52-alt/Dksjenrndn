package com.invokerskills.mod;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Здесь живут все 10 заклинаний. Индекс combo соответствует таблице
 * в InvokerSkillsClient (порядок нажатий не важен, считаем количество орбов):
 *
 *  0 = ZZZ  Ледяная волна
 *  1 = ZZX  Морозный шаг (невидимость + скорость)
 *  2 = ZZY  Ледяная стена
 *  3 = ZXX  ЭМИ (снимает баффы с врагов)
 *  4 = ZXY  Торнадо
 *  5 = ZYY  Ускорение (баф на себя)
 *  6 = XXX  Дух кузни (призыв союзника)
 *  7 = XXY  Метеор
 *  8 = XYY  Удар молнии
 *  9 = YYY  Оглушающий взрыв
 */
public class SpellCaster {

    private static final String[] NAMES = {
            "Ледяная волна (ZZZ)",
            "Морозный шаг (ZZX)",
            "Ледяная стена (ZZY)",
            "ЭМИ (ZXX)",
            "Торнадо (ZXY)",
            "Ускорение (ZYY)",
            "Дух кузни (XXX)",
            "Метеор (XXY)",
            "Удар молнии (XYY)",
            "Оглушающий взрыв (YYY)"
    };

    public static void cast(ServerPlayerEntity player, int comboId) {
        if (player == null || comboId < 0 || comboId > 9) return;
        ServerWorld world = player.getServerWorld();
        player.sendMessage(Text.literal("§b[Invoker] §f" + NAMES[comboId]), true);

        switch (comboId) {
            case 0 -> iceNova(player, world);
            case 1 -> ghostWalk(player);
            case 2 -> iceWall(player, world);
            case 3 -> emp(player, world);
            case 4 -> tornado(player, world);
            case 5 -> alacrity(player);
            case 6 -> forgeSpirit(player, world);
            case 7 -> meteor(player, world);
            case 8 -> sunStrike(player, world);
            case 9 -> deafeningBlast(player, world);
        }
    }

    private static List<LivingEntity> nearby(ServerWorld world, ServerPlayerEntity player, double radius) {
        Box box = player.getBoundingBox().expand(radius);
        return world.getEntitiesByClass(LivingEntity.class, box, e -> e != player);
    }

    private static void iceNova(ServerPlayerEntity player, ServerWorld world) {
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 80, 3, 1.5, 3, 0.02);
        for (LivingEntity e : nearby(world, player, 5)) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            e.damage(player.getDamageSources().magic(), 3.0f);
        }
    }

    private static void ghostWalk(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 140, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 140, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 2));
    }

    private static void iceWall(ServerPlayerEntity player, ServerWorld world) {
        Direction facing = player.getHorizontalFacing();
        Direction side = facing.rotateYClockwise();
        BlockPos base = player.getBlockPos().offset(facing, 2);
        for (int w = -2; w <= 2; w++) {
            for (int h = 0; h < 3; h++) {
                BlockPos pos = base.offset(side, w).up(h);
                if (world.getBlockState(pos).isAir()) {
                    world.setBlockState(pos, Blocks.PACKED_ICE.getDefaultState());
                    TickScheduler.scheduleRevert(world, pos, 160); // ~8 секунд
                }
            }
        }
    }

    private static void emp(ServerPlayerEntity player, ServerWorld world) {
        for (LivingEntity e : nearby(world, player, 6)) {
            for (var effect : List.copyOf(e.getStatusEffects())) {
                e.removeStatusEffect(effect.getEffectType());
            }
            e.damage(player.getDamageSources().magic(), 2.0f);
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 60, 3, 1, 3, 0.1);
    }

    private static void tornado(ServerPlayerEntity player, ServerWorld world) {
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 100, 3, 2, 3, 0.05);
        for (LivingEntity e : nearby(world, player, 6)) {
            Vec3d dir = e.getPos().subtract(player.getPos()).normalize();
            e.setVelocity(dir.x * 0.6, 1.1, dir.z * 0.6);
            e.velocityModified = true;
        }
    }

    private static void alacrity(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 2));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 200, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 1));
    }

    private static void forgeSpirit(ServerPlayerEntity player, ServerWorld world) {
        Entity golem = EntityType.IRON_GOLEM.create(world, SpawnReason.MOB_SUMMONED);
        if (golem != null) {
            golem.refreshPositionAndAngles(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
            world.spawnEntity(golem);
        }
    }

    private static void meteor(ServerPlayerEntity player, ServerWorld world) {
        BlockHitResult hit = raycast(player, world, 40);
        Vec3d pos = hit.getPos();
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 1, pos.z, 100, 1.5, 1.5, 1.5, 0.05);
        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.8f);
        Box box = new Box(pos.x - 3, pos.y - 1, pos.z - 3, pos.x + 3, pos.y + 4, pos.z + 3);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, le -> true)) {
            e.damage(player.getDamageSources().magic(), 8.0f);
            e.setOnFireFor(4);
        }
    }

    private static void sunStrike(ServerPlayerEntity player, ServerWorld world) {
        BlockHitResult hit = raycast(player, world, 60);
        Vec3d pos = hit.getPos();
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(pos);
            bolt.setChanneler(player);
            world.spawnEntity(bolt);
        }
    }

    private static void deafeningBlast(ServerPlayerEntity player, ServerWorld world) {
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1, player.getZ(), 5, 1, 1, 1, 0);
        for (LivingEntity e : nearby(world, player, 6)) {
            Vec3d dir = e.getPos().subtract(player.getPos()).normalize();
            e.setVelocity(dir.x * 1.2, 0.4, dir.z * 1.2);
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
        }
    }

    private static BlockHitResult raycast(ServerPlayerEntity player, ServerWorld world, double range) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(range));
        return world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
    }
}

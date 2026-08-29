package com.invokerskills.mod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Простой планировщик: "через N тиков верни этот блок в воздух".
 * Используется, например, для временной ледяной стены.
 */
public class TickScheduler {

    private record Job(ServerWorld world, BlockPos pos, int ticksLeft) {}

    private static final List<Job> jobs = new ArrayList<>();

    public static void scheduleRevert(ServerWorld world, BlockPos pos, int delayTicks) {
        jobs.add(new Job(world, pos, delayTicks));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (jobs.isEmpty()) return;
            List<Job> next = new ArrayList<>();
            for (Job job : jobs) {
                if (job.ticksLeft() <= 0) {
                    job.world().setBlockState(job.pos(), Blocks.AIR.getDefaultState());
                } else {
                    next.add(new Job(job.world(), job.pos(), job.ticksLeft() - 1));
                }
            }
            jobs.clear();
            jobs.addAll(next);
        });
    }
}

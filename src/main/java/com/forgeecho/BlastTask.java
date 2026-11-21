package com.forgeecho;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves a huge crater in expanding shells over several ticks so the server
 * doesn't freeze, then scatters sculk around the edge.
 */
public final class BlastTask {
    /** Crater radius in blocks. 40 = 80 blocks wide = 5 chunks. Lower this if it lags. */
    public static final int RADIUS = 40;
    /** The outer part of the crater where blocks are only partly destroyed. */
    public static final int RAGGED_EDGE = 6;
    /** How far past the crater sculk can spread. */
    public static final int SCULK_BAND = 8;
    /** Blocks of radius processed each tick (bigger = faster but laggier). */
    public static final int STEP = 3;

    private static final int DESTROY_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<BlastTask> TASKS = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos center;
    private final RandomSource random;
    private int inner = 0;

    private BlastTask(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;
        this.random = level.getRandom();
    }

    public static void start(ServerLevel level, BlockPos center) {
        TASKS.add(new BlastTask(level, center));
    }

    public static void tickAll() {
        if (TASKS.isEmpty()) return;
        TASKS.removeIf(BlastTask::tick);
    }

    public static void clear() {
        TASKS.clear();
    }

    /** @return true when finished */
    private boolean tick() {
        int max = RADIUS + SCULK_BAND;
        int outer = Math.min(inner + STEP, max + 1);
        processShell(inner, outer);
        shellEffects((inner + outer) / 2.0);
        inner = outer;
        return inner > max;
    }

    private void processShell(int lo, int hi) {
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        int minY = Math.max(level.getMinY(), cy - hi);
        int maxY = Math.min(level.getMaxY(), cy + hi);
        long lo2 = (long) lo * lo;
        long hi2 = (long) hi * hi;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -hi; dx <= hi; dx++) {
            for (int dz = -hi; dz <= hi; dz++) {
                long dxz2 = (long) dx * dx + (long) dz * dz;
                if (dxz2 >= hi2) continue;
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - cy;
                    long d2 = dxz2 + (long) dy * dy;
                    if (d2 < lo2 || d2 >= hi2) continue;

                    pos.set(cx + dx, y, cz + dz);
                    if (!level.hasChunkAt(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, pos) < 0) continue; // bedrock etc.

                    double d = Math.sqrt((double) d2);
                    if (d <= RADIUS - RAGGED_EDGE) {
                        destroy(pos);
                    } else if (d <= RADIUS) {
                        if (random.nextFloat() < 0.65f) {
                            destroy(pos);
                        } else if (random.nextFloat() < 0.6f) {
                            sculkify(pos, state);
                        }
                    } else if (random.nextFloat() < 0.25f) {
                        sculkify(pos, state);
                    }
                }
            }
        }
    }

    private void destroy(BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), DESTROY_FLAGS);
    }

    private void sculkify(BlockPos pos, BlockState state) {
        if (!state.canOcclude() || !state.getFluidState().isEmpty()) return;
        level.setBlock(pos, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private void shellEffects(double radius) {
        if (radius > RADIUS) return;
        DustParticleOptions cyan = new DustParticleOptions(0x00FFFF, 3.0f);
        for (int i = 0; i < 10; i++) {
            double x = random.nextGaussian(), y = random.nextGaussian(), z = random.nextGaussian();
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len == 0) continue;
            double px = center.getX() + 0.5 + x / len * radius;
            double py = center.getY() + 0.5 + y / len * radius;
            double pz = center.getZ() + 0.5 + z / len * radius;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 1, 0, 0, 0, 0);
            level.sendParticles(cyan, px, py, pz, 30, 2, 2, 2, 0);
        }
    }
}

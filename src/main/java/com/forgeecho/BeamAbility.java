package com.forgeecho;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BeamAbility {
    /** How far the beam reaches, in blocks. */
    public static final double RANGE = 128.0;
    /** Damage per hit (20 = 10 hearts). Sonic boom damage ignores armor. */
    public static final float DAMAGE = 200.0f;
    /** How close to the beam line a player must be to get hit. */
    public static final double BEAM_RADIUS = 2.5;

    private BeamAbility() {}

    public static void fire(ServerLevel level, ServerPlayer user) {
        Vec3 start = user.getEyePosition();
        Vec3 dir = user.getLookAngle().normalize();
        Vec3 end = start.add(dir.scale(RANGE));

        BlockHitResult hit = level.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;
        Vec3 stop = hitBlock ? hit.getLocation() : end;
        double length = start.distanceTo(stop);

        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.8f);

        drawBeam(level, start, dir, length);
        damagePlayers(level, user, start, stop, dir);

        if (hitBlock) {
            impact(level, hit);
        }
    }

    private static void drawBeam(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        Vec3 ref = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(ref).normalize();
        Vec3 up = right.cross(dir).normalize();

        DustParticleOptions cyan = new DustParticleOptions(0x00FFFF, 1.6f);
        DustParticleOptions blue = new DustParticleOptions(0x1E50FF, 1.6f);

        int i = 0;
        for (double d = 1.5; d < length; d += 0.75, i++) {
            Vec3 c = start.add(dir.scale(d));
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            }
            // two spiralling strands of dust around the beam
            for (int strand = 0; strand < 2; strand++) {
                double angle = d * 1.2 + strand * Math.PI;
                Vec3 off = right.scale(Math.cos(angle) * 1.3).add(up.scale(Math.sin(angle) * 1.3));
                level.sendParticles(strand == 0 ? cyan : blue,
                    c.x + off.x, c.y + off.y, c.z + off.z, 1, 0, 0, 0, 0);
            }
            // loose glow around the core
            level.sendParticles(cyan, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
        }
    }

    private static void damagePlayers(ServerLevel level, ServerPlayer user, Vec3 start, Vec3 stop, Vec3 dir) {
        AABB box = new AABB(start, stop).inflate(BEAM_RADIUS);
        for (Player target : level.getEntitiesOfClass(Player.class, box)) {
            if (target == user) continue; // never hurt the caster
            if (distanceToSegment(target.getBoundingBox().getCenter(), start, stop) <= BEAM_RADIUS) {
                target.hurtServer(level, level.damageSources().sonicBoom(user), DAMAGE);
            }
        }
    }

    private static void impact(ServerLevel level, BlockHitResult hit) {
        Vec3 p = hit.getLocation();
        BlockPos center = hit.getBlockPos();

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 8, 6, 6, 6, 0);
        level.sendParticles(new DustParticleOptions(0x00FFFF, 3.0f), p.x, p.y, p.z, 400, 8, 8, 8, 0);
        level.sendParticles(new DustParticleOptions(0x1E50FF, 3.0f), p.x, p.y, p.z, 400, 8, 8, 8, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 12, 4, 4, 4, 0);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0f, 0.6f);

        BlastTask.start(level, center);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}

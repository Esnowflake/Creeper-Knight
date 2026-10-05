package dev.creeperknight.ai;

import dev.creeperknight.KnightLogic;
import dev.creeperknight.KnightSettings;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Vanilla navigation chooses the route; this control keeps boosted movement on that route. */
public final class KnightMoveControl extends MoveControl {
    private final Creeper creeper;
    public KnightMoveControl(Creeper creeper) { super(creeper); this.creeper = creeper; }

    @Override public void tick() {
        if (!KnightLogic.isKnight(creeper) || creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) * Math.max(1, speedModifier) <= 0.5) {
            super.tick(); return;
        }
        boolean stopForFuse = !KnightSettings.get().instantExplosion && !KnightSettings.get().chaseDuringFuse
            && creeper.getSwellDir() > 0 && !creeper.isIgnited();
        if (stopForFuse) {
            operation = Operation.WAIT;
            Vec3 velocity = creeper.getDeltaMovement();
            creeper.setDeltaMovement(0, velocity.y, 0);
            creeper.setXxa(0);
            super.tick(); return;
        }
        if (!closeOnTarget() && operation == Operation.MOVE_TO) lookAheadOnStraightPath();
        if (operation != Operation.MOVE_TO && operation != Operation.JUMPING) { super.tick(); return; }

        double dx = wantedX - creeper.getX(), dz = wantedZ - creeper.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-4) { super.tick(); return; }
        Vec3 direction = new Vec3(dx / distance, 0, dz / distance);
        // A fixed vanilla turn limit and sideways momentum create an orbit at large speed multipliers.
        creeper.setYRot((float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90);
        Vec3 velocity = creeper.getDeltaMovement();
        double carry = Mth.clamp(velocity.dot(direction), 0, distance * 0.25);
        creeper.setDeltaMovement(direction.x * carry, velocity.y, direction.z * carry);
        super.tick(); // Retain vanilla jump, collision and vertical movement behavior.
        // Brake for the next corner or exact target, rather than shooting past it and turning back.
        creeper.setSpeed((float)Math.min(creeper.getSpeed(), distance * 0.5));
    }

    private boolean closeOnTarget() {
        var target = creeper.getTarget();
        if (!KnightLogic.targetAllowed(target)) return false;
        Vec3 delta = target.position().subtract(creeper.position());
        if (delta.horizontalDistanceSqr() > 16 || Math.abs(delta.y) > 0.5 || !creeper.hasLineOfSight(target)
            || !creeper.level().noCollision(creeper, creeper.getBoundingBox().expandTowards(delta))) return false;
        // Update every movement tick, including after navigation ends at the target's block centre.
        setWantedPosition(target.getX(), target.getY(), target.getZ(), Math.max(1, speedModifier));
        return true;
    }

    private void lookAheadOnStraightPath() {
        Path path = creeper.getNavigation().getPath();
        if (path == null || path.isDone() || !creeper.onGround()) return;
        int first = path.getNextNodeIndex();
        if (first + 1 >= path.getNodeCount()) return;
        var base = path.getNode(first); var next = path.getNode(first + 1);
        int dx = next.x - base.x, dz = next.z - base.z;
        if (base.y != Mth.floor(creeper.getY()) || next.y != base.y || dx == 0 && dz == 0) return;
        double lookAhead = Math.max(2, creeper.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedModifier * 3);
        int selected = first;
        for (int i = first + 1; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (node.y != base.y || node.x != base.x + dx * (i - first) || node.z != base.z + dz * (i - first)) break;
            Vec3 point = path.getEntityPosAtNode(creeper, i);
            Vec3 delta = point.subtract(creeper.position());
            if (delta.horizontalDistanceSqr() > lookAhead * lookAhead
                || !creeper.level().noCollision(creeper, creeper.getBoundingBox().expandTowards(delta))) break;
            selected = i;
        }
        if (selected != first) {
            path.setNextNodeIndex(selected);
            Vec3 point = path.getNextEntityPos(creeper);
            setWantedPosition(point.x, wantedY, point.z, speedModifier);
        }
    }
}

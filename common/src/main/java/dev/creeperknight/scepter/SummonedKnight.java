package dev.creeperknight.scepter;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Only immutable identifiers and a world-time deadline are persisted, never a player entity reference. */
public record SummonedKnight(UUID target, long expiresAt) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("Target", target); tag.putLong("ExpiresAt", expiresAt); return tag;
    }
    public static SummonedKnight load(CompoundTag tag) {
        return tag.hasUUID("Target") && tag.contains("ExpiresAt", 4) ? new SummonedKnight(tag.getUUID("Target"), tag.getLong("ExpiresAt")) : null;
    }
    public interface Access {
        SummonedKnight creeperknight$getSummon();
        void creeperknight$setSummon(SummonedKnight data);
    }
}

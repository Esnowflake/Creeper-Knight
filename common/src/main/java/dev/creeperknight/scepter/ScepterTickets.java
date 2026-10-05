package dev.creeperknight.scepter;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Short-lived entity-ticking tickets keep the countdown running even after both players leave. */
public final class ScepterTickets extends SavedData {
    private static final TicketType<UUID> TYPE = TicketType.create("creeperknight_scepter", Comparator.<UUID>naturalOrder(), 60);
    private record Entry(ResourceKey<Level> dimension, ChunkPos chunk, long deadline) {}
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, Integer> missingSince = new HashMap<>();
    private static ScepterTickets current;
    private ScepterTickets() {}
    private static ScepterTickets get(MinecraftServer server) {
        if (current == null) current = server.overworld().getDataStorage().computeIfAbsent(ScepterTickets::load, ScepterTickets::new, "creeperknight_scepter_tickets");
        return current;
    }
    private static ScepterTickets load(CompoundTag root) {
        var data = new ScepterTickets();
        for (var item : root.getList("Summons", 10)) {
            CompoundTag tag = (CompoundTag)item;
            if (!tag.hasUUID("UUID")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension != null) data.entries.put(tag.getUUID("UUID"), new Entry(ResourceKey.create(Registries.DIMENSION, dimension), new ChunkPos(tag.getLong("Chunk")), tag.getLong("Deadline")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        entries.forEach((uuid, entry) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("UUID", uuid); tag.putString("Dimension", entry.dimension().location().toString());
            tag.putLong("Chunk", entry.chunk().toLong()); tag.putLong("Deadline", entry.deadline()); list.add(tag);
        });
        root.put("Summons", list); return root;
    }
    public static void track(Creeper creeper) {
        if (!(creeper.level() instanceof ServerLevel level) || ScepterLogic.data(creeper) == null || !creeper.isAlive()) return;
        ScepterTickets data = get(level.getServer());
        Entry next = new Entry(level.dimension(), creeper.chunkPosition(), ScepterLogic.data(creeper).expiresAt());
        Entry previous = data.entries.put(creeper.getUUID(), next);
        if (!next.equals(previous)) {
            // Add before removing the previous ticket when the knight crosses a chunk boundary.
            level.getChunkSource().addRegionTicket(TYPE, next.chunk(), 2, creeper.getUUID());
            if (previous != null && (!previous.dimension().equals(next.dimension()) || !previous.chunk().equals(next.chunk()))) removeTicket(level.getServer(), creeper.getUUID(), previous);
            data.setDirty();
        }
        data.missingSince.remove(creeper.getUUID());
    }
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        ScepterTickets data = get(server);
        for (var iterator = data.entries.entrySet().iterator(); iterator.hasNext();) {
            var pair = iterator.next(); UUID uuid = pair.getKey(); Entry entry = pair.getValue();
            ServerLevel level = server.getLevel(entry.dimension());
            var entity = level == null ? null : level.getEntity(uuid);
            boolean missingTooLong = entity == null && server.getTickCount() - data.missingSince.computeIfAbsent(uuid, k -> server.getTickCount()) > 100;
            if (level == null || missingTooLong || entity != null && (!(entity instanceof Creeper creeper) || !creeper.isAlive() || ScepterLogic.data(creeper) == null)) {
                removeTicket(server, uuid, entry); iterator.remove(); data.missingSince.remove(uuid); data.setDirty(); continue;
            }
            level.getChunkSource().addRegionTicket(TYPE, entry.chunk(), 2, uuid);
            if (entity instanceof Creeper creeper) ScepterLogic.tickDeadline(creeper);
        }
    }
    private static void removeTicket(MinecraftServer server, UUID uuid, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level != null) level.getChunkSource().removeRegionTicket(TYPE, entry.chunk(), 2, uuid);
    }
    public static void clear() { current = null; }
}

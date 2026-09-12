package com.radiance.compat.dh;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhTerrainRenderer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Transfers one opaque DH VBO per frame; native publication is atomic across the selected set. */
public final class RadianceDhTerrainRenderer implements IDhTerrainRenderer {
    public static final RadianceDhTerrainRenderer INSTANCE = new RadianceDhTerrainRenderer();
    private Object level;
    private long epoch, revision;
    private boolean coverageLimited;
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private static final class Entry {
        final LodBufferContainer container;
        final long revision;
        final List<LodWaterParts.Part> parts;
        long sourceBytes;
        int sent;
        Entry(LodBufferContainer container, long revision) {
            this.container = container; this.revision = revision;
            parts = LodWaterParts.capture(container);
            for (var part : parts) sourceBytes += part.snapshot().vertices().remaining();
            if (parts.size() > 64) throw new IllegalStateException("DH section exceeds bridge part limit");
        }
    }
    public synchronized void clearJavaState() { level = null; entries.clear(); }

    @Override public synchronized void render(RenderParams params, boolean opaquePass,
            SortedArraySet<LodBufferContainer> sections, IProfilerWrapper profiler) {
        if (!opaquePass) return;
        if (level != params.clientLevelWrapper) {
            level = params.clientLevelWrapper; entries.clear();
            NativeLodBridge.configure(RadianceDhConfig.current.liveBytes(), RadianceDhConfig.current.queueBytes());
            NativeLodBridge.beginWorld(++epoch);
            System.out.println("[Radiance DH] Native LOD world opened, epoch=" + epoch);
        }
        // Complete one immutable selection before accepting a new quadtree cut. This keeps
        // changing parents/children and closed DH wrappers from canceling in-flight uploads.
        if (NativeLodBridge.selectionPublished(epoch) && sections.size() > 0) {
            List<LodBufferContainer> ordered = new ArrayList<>();
            for (int i = 0; i < sections.size(); i++) ordered.add(sections.get(i));
            double cameraX = Math.floor(params.exactCameraPosition.x / 16.0) * 16.0;
            double cameraZ = Math.floor(params.exactCameraPosition.z / 16.0) * 16.0;
            // DH's comparator-only SortedArraySet.add does not sort. Never truncate its
            // traversal order, and never use camera direction for RT scene visibility.
            ordered.sort(Comparator.comparingDouble((LodBufferContainer c) -> distance(c, cameraX, cameraZ))
                    .thenComparingLong(c -> c.pos));
            Map<Long, Entry> next = new LinkedHashMap<>();
            LodSelectionBudget budget = new LodSelectionBudget(RadianceDhConfig.current.selectedBytes());
            boolean limited = ordered.size() > 2048;
            boolean incomplete = false;
            for (LodBufferContainer section : ordered) {
                if (!section.buffersUploaded) { incomplete = true; break; }
                if (next.size() == 2048) { limited = true; break; }
                Entry entry = entries.get(section.pos);
                if (entry == null || entry.container != section) entry = new Entry(section, ++revision);
                if (!budget.include(entry.sourceBytes)) { limited = true; continue; }
                next.put(section.pos, entry);
            }
            // A transient incomplete DH list must not publish a hole while wrappers upload.
            if (!incomplete) {
                if (limited != coverageLimited) {
                    System.out.println(limited
                            ? "[Radiance DH] Partial LOD coverage: configured source or section limit reached."
                            : "[Radiance DH] Requested LOD selection fits configured limits.");
                    coverageLimited = limited;
                }
                entries.clear(); entries.putAll(next);
                long[] ids = new long[entries.size()], revisions = new long[entries.size()];
                int cursor = 0;
                for (var entry : entries.entrySet()) {
                    ids[cursor] = entry.getKey(); revisions[cursor++] = entry.getValue().revision;
                }
                NativeLodBridge.select(epoch, ids, revisions);
            }
        }
        for (Entry entry : entries.values()) {
            int count = Math.max(1, entry.parts.size());
            if (entry.sent >= count) continue;
            var container = entry.container;
            CapturedLodBuffer.Snapshot snapshot = entry.parts.isEmpty()
                    ? new CapturedLodBuffer.Snapshot(0, ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN))
                    : entry.parts.get(entry.sent).snapshot();
            ByteBuffer packet = LodPacket.encode(epoch, container.pos, entry.revision,
                    container.minCornerBlockPos.getX(), container.minCornerBlockPos.getY(),
                    container.minCornerBlockPos.getZ(), DhSectionPos.getBlockWidth(container.pos),
                    entry.sent, count, snapshot, entry.parts.isEmpty() ? LodPacket.OPAQUE : entry.parts.get(entry.sent).flags());
            if (NativeLodBridge.upload(packet)) entry.sent++;
            break;
        }
    }
    private static double distance(LodBufferContainer c, double x, double z) {
        double half = DhSectionPos.getBlockWidth(c.pos) * 0.5;
        double dx = c.minCornerBlockPos.getX() + half - x;
        double dz = c.minCornerBlockPos.getZ() + half - z;
        return dx * dx + dz * dz;
    }
}

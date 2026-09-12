package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

    public static final ChunkBuilder.ChunkData PROCESSED = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    public static final ChunkBuilder.ChunkData TERRAIN_EMPTY = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, ChunkBuilder.BuiltChunk> rebuildQueue = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> forcedRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    // Snapshot creation must stay on the render thread on NeoForge. Bound both
    // that work and queued snapshots rather than draining a whole terrain burst.
    private static final long SNAPSHOT_BUDGET_NS = 2_000_000L;
    private static final int MAX_REBUILDS_PER_FRAME = 8;
    private static final AtomicInteger inFlightRebuilds = new AtomicInteger();
    private static final java.util.Set<Integer> inFlightIndices = ConcurrentHashMap.newKeySet();
    private static BuiltChunkStorage currentStorage = null;
    private static boolean pendingRebuildAll = false;
    private static int numChunkRebuildThreads = getChunkRebuildThreadCount();
    private static final int numImportantChunkRebuildThreads = 1;
    private static int numNormalChunkRebuildThreads = Math.max(1,
        numChunkRebuildThreads - numImportantChunkRebuildThreads);
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<BlockBufferAllocatorStorage>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(BlockBufferAllocatorStorage::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord) {
        clear();
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
    }

    public static void updateSectionPos(ChunkSectionPos sectionPos) {
        updateSectionPosNative(sectionPos.getSectionX(), sectionPos.getSectionY(),
            sectionPos.getSectionZ());
    }

    public static void setStorage(BuiltChunkStorage storage) {
        currentStorage = storage;
        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal = RenderLayer.getBlockLayers()
            .stream()
            .mapToInt(RenderLayer::getExpectedBufferSize)
            .sum();
        int memoryLimited = Math.max(1,
            (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4) - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(2,
            Math.min(userThreads, Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final BlockBufferAllocatorStorage s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        numChunkRebuildThreads = getChunkRebuildThreadCount();
        numNormalChunkRebuildThreads = Math.max(1,
            numChunkRebuildThreads - numImportantChunkRebuildThreads);
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        inFlightIndices.clear();
        forcedRebuildIndices.clear();
        rebuildTasks.clear();
        currentStorage = null;
        pendingRebuildAll = false;
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void rebuildAll() {
        if (currentStorage == null || currentStorage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        queueRebuildAll(currentStorage);
    }

    private static void queueRebuildAll(BuiltChunkStorage storage) {
        if (storage == null || storage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        for (ChunkBuilder.BuiltChunk builtChunk : storage.chunks) {
            if (builtChunk == null) {
                continue;
            }
            forcedRebuildIndices.add(builtChunk.index);
            builtChunk.scheduleRebuild(true);
            enqueueRebuild(builtChunk);
        }
    }

    public static void rebuild(Camera camera) {
        if (rebuildQueue.isEmpty()
            || inFlightRebuilds.get() >= numChunkRebuildThreads * 2) return;

        BlockPos blockPos = camera.getBlockPos();
        // Bring nearby terrain in first; deferred entries remain in the queue.
        List<ChunkBuilder.BuiltChunk> candidates = new ArrayList<>(rebuildQueue.values());
        candidates.sort(Comparator.comparingDouble(chunk ->
            chunk.getOrigin().getSquaredDistance(blockPos)));
        ChunkRendererRegionBuilder regionBuilder = new ChunkRendererRegionBuilder();
        long started = System.nanoTime();
        int submitted = 0;
        int importantSubmitted = 0;
        for (ChunkBuilder.BuiltChunk builtChunk : candidates) {
            if (submitted >= MAX_REBUILDS_PER_FRAME
                || inFlightRebuilds.get() >= numChunkRebuildThreads * 2
                || (submitted > 0 && System.nanoTime() - started >= SNAPSHOT_BUDGET_NS)) break;

            // Preserve a newer dirty request until this slot finishes its current snapshot.
            if (inFlightIndices.contains(builtChunk.index)) continue;
            if (!builtChunk.needsRebuild()) {
                rebuildQueue.remove(builtChunk.index, builtChunk);
                forcedRebuildIndices.remove(builtChunk.index);
                continue;
            }
            // A missing client chunk is not a completed empty section. Keep its
            // request queued until the server's terrain arrives.
            ChunkBuilder candidateBuilder = ((IChunkBuilderBuiltChunkExt) builtChunk).radiance$getChunkBuilder();
            BlockPos candidateOrigin = builtChunk.getOrigin();
            if (!((IChunkBuilderExt) candidateBuilder).radiance$getWorld().isChunkLoaded(
                    candidateOrigin.getX() >> 4, candidateOrigin.getZ() >> 4)) continue;
            boolean forced = forcedRebuildIndices.contains(builtChunk.index);
            if (!forced && !builtChunk.shouldBuild()) continue;

            // Only edits to existing terrain need a same-frame build. Newly
            // arriving terrain can complete asynchronously, including near us.
            boolean isImportant = importantSubmitted == 0 && builtChunk.needsImportantRebuild()
                && builtChunk.data.get() != ChunkBuilder.ChunkData.EMPTY;
            rebuildQueue.remove(builtChunk.index, builtChunk);
            forcedRebuildIndices.remove(builtChunk.index);
            builtChunk.cancelRebuild();

            ChunkBuilder chunkBuilder =
                ((IChunkBuilderBuiltChunkExt) builtChunk).radiance$getChunkBuilder();
            BlockPos buildOrigin = builtChunk.getOrigin().toImmutable();
            ChunkRendererRegion region = regionBuilder.build(
                ((IChunkBuilderExt) chunkBuilder).radiance$getWorld(),
                ChunkSectionPos.from(buildOrigin));

            inFlightIndices.add(builtChunk.index);
            inFlightRebuilds.incrementAndGet();
            Runnable build = () -> {
                try {
                    rebuildSingle(builtChunk, buildOrigin, region, isImportant);
                } finally {
                    inFlightIndices.remove(builtChunk.index);
                    inFlightRebuilds.decrementAndGet();
                }
            };
            try {
                if (isImportant) {
                    rebuildTasks.add(importantChunkRebuildExecutor.submit(build));
                    importantSubmitted++;
                } else {
                    backgroundChunkRebuildExecutor.execute(build);
                }
                submitted++;
            } catch (java.util.concurrent.RejectedExecutionException e) {
                inFlightIndices.remove(builtChunk.index);
                inFlightRebuilds.decrementAndGet();
                builtChunk.scheduleRebuild(true);
                throw e;
            }
        }
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void rebuildSingle(ChunkBuilder.BuiltChunk builtChunk,
        BlockPos buildOrigin, ChunkRendererRegion chunkRendererRegion, boolean important) {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            ChunkBuilder chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            if (chunkRendererRegion == null) {
                synchronized (builtChunk) {
                    if (!buildOrigin.equals(builtChunk.getOrigin())) return;
                    publishEmptySection(builtChunk.index, buildOrigin);
                    builtChunk.data.set(ChunkBuilder.ChunkData.EMPTY);
                }
                return;
            }

            BlockBufferAllocatorStorage storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, builtChunk, buildOrigin, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rebuildSingle(ChunkRendererRegion chunkRendererRegion,
        ChunkBuilder chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        ChunkBuilder.BuiltChunk builtChunk,
        BlockPos buildOrigin,
        BlockBufferAllocatorStorage storage,
        boolean important) {

        ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(buildOrigin);

        Vec3d vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorter
            vertexSorter =
            VertexSorter.byDistance((float) (vec3d.x - buildOrigin
                    .getX()),
                (float) (vec3d.y - buildOrigin
                    .getY()),
                (float) (vec3d.z - buildOrigin
                    .getZ()));

        SectionBuilder.RenderData renderData =
            ((IChunkBuilderExt) chunkBuilder).radiance$getSectionBuilder()
                .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);

        Map<RenderLayer, BuiltBuffer> buffers = renderData.buffers;
        try {
            // setOrigin uses this same monitor. Publish only into the slot captured
            // with the region snapshot, never a slot recycled as the camera moves.
            synchronized (builtChunk) {
                if (!buildOrigin.equals(builtChunk.getOrigin())) return;
                builtChunk.setNoCullingBlockEntities(renderData.noCullingBlockEntities);

                if (buffers.isEmpty()) {
                    ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                        @Override
                        public List<BlockEntity> getBlockEntities() {
                            return renderData.blockEntities;
                        }

                        @Override
                        public boolean isVisibleThrough(Direction from, Direction to) {
                            return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                        }

                        @Override
                        public boolean isEmpty(RenderLayer layer) {
                            return true;
                        }
                    };
                    builtChunk.data.set(chunkData);
                    builtChunkNum++;

                    publishEmptySection(builtChunk.index, buildOrigin);
                } else {
                    ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                        @Override
                        public List<BlockEntity> getBlockEntities() {
                            return renderData.blockEntities;
                        }

                        @Override
                        public boolean isVisibleThrough(Direction from, Direction to) {
                            return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                        }

                        @Override
                        public boolean isEmpty(RenderLayer layer) {
                            return layer == null || !buffers.containsKey(layer);
                        }
                    };
                    builtChunk.data.set(chunkData);
                    builtChunkNum++;

                    ByteBuffer geometryTypeBB = null;
                    ByteBuffer geometryGroupNameBB = null;
                    ByteBuffer geometryTextureBB = null;
                    ByteBuffer vertexFormatBB = null;
                    ByteBuffer vertexCountBB = null;
                    ByteBuffer verticesBB = null;
                    List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());

                    try {
                        int geometryTypeSize = buffers.size() * Integer.BYTES;
                        geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
                        long geometryTypeAddr = memAddress(geometryTypeBB);
                        int geometryTypeBaseAddr = 0;

                        int geometryGroupNameSize = buffers.size() * Long.BYTES;
                        geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
                        long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
                        int geometryGroupNameBaseAddr = 0;

                        int geometryTextureSize = buffers.size() * Integer.BYTES;
                        geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
                        long geometryTextureAddr = memAddress(geometryTextureBB);
                        int geometryTextureBaseAddr = 0;

                        int vertexFormatSize = buffers.size() * Integer.BYTES;
                        vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                        long vertexFormatAddr = memAddress(vertexFormatBB);
                        int vertexFormatBaseAddr = 0;

                        int vertexCountSize = buffers.size() * Integer.BYTES;
                        vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                        long vertexCountAddr = memAddress(vertexCountBB);
                        int vertexCountBaseAddr = 0;

                        int verticesSize = buffers.size() * Long.BYTES;
                        verticesBB = MemoryUtil.memAlloc(verticesSize);
                        long verticesAddr = memAddress(verticesBB);
                        int verticesBaseAddr = 0;

                        for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
                            RenderLayer renderLayer = entry.getKey();
                            assert renderLayer.getDrawMode() == QUADS;

                            BuiltBuffer vertexBuffer = entry.getValue();
                            BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                                vertexBuffer.getBuffer());
                            assert vertexBuffer.getDrawParameters()
                                .indexCount() == vertexBuffer.getDrawParameters()
                                .vertexCount() / 4 * 6;

                            TextureManager
                                textureManager =
                                MinecraftClient.getInstance()
                                    .getTextureManager();

                            int
                                geometryTypeID =
                                Constants.GeometryTypes.getGeometryType(renderLayer, true)
                                    .getValue();
                            int
                                geometryTextureID =
                                textureManager.getTexture(
                                        ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                                            .orElse(MissingSprite.getMissingSpriteId()))
                                    .getGlId();
                            int vertexFormatID = Constants.VertexFormats.getValue(
                                vertexBuffer.getDrawParameters()
                                    .format());

                            geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                            geometryTypeBaseAddr += Integer.BYTES;

                            ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                            geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                            geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                                memAddress(geometryGroupNameBuffer));
                            geometryGroupNameBaseAddr += Long.BYTES;

                            geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                            geometryTextureBaseAddr += Integer.BYTES;

                            vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                            vertexFormatBaseAddr += Integer.BYTES;

                            vertexCountBB.putInt(vertexCountBaseAddr,
                                vertexBuffer.getDrawParameters()
                                    .vertexCount());
                            vertexCountBaseAddr += Integer.BYTES;

                            verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                            verticesBaseAddr += Long.BYTES;
                        }

                        rebuildSingle(buildOrigin
                                .getX(),
                            buildOrigin
                                .getY(),
                            buildOrigin
                                .getZ(),
                            builtChunk.index,
                            buffers.size(),
                            geometryTypeAddr,
                            geometryGroupNameAddr,
                            geometryTextureAddr,
                            vertexFormatAddr,
                            vertexCountAddr,
                            verticesAddr,
                            important);
                    } finally {
                        if (geometryTypeBB != null) {
                            MemoryUtil.memFree(geometryTypeBB);
                        }
                        if (geometryGroupNameBB != null) {
                            MemoryUtil.memFree(geometryGroupNameBB);
                        }
                        if (geometryTextureBB != null) {
                            MemoryUtil.memFree(geometryTextureBB);
                        }
                        if (vertexFormatBB != null) {
                            MemoryUtil.memFree(vertexFormatBB);
                        }
                        if (vertexCountBB != null) {
                            MemoryUtil.memFree(vertexCountBB);
                        }
                        if (verticesBB != null) {
                            MemoryUtil.memFree(verticesBB);
                        }
                        for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                            MemoryUtil.memFree(geometryGroupNameBuffer);
                        }
                    }
                }

            }
        } finally {
            for (BuiltBuffer buffer : buffers.values()) {
                buffer.close();
            }
        }
    }

    private static void publishEmptySection(int index, BlockPos origin) {
        // Publish through the same versioned native completion path as real geometry.
        // Zero geometry creates no BLAS; it still certifies this exact section as ready.
        rebuildSingle(origin.getX(), origin.getY(), origin.getZ(), index,
            0, 0L, 0L, 0L, 0L, 0L, 0L, true);
    }

    private static native void rebuildSingle(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void relocateSingle(long index, int originX, int originY, int originZ);

    public static native void invalidateSingle(long index);
}

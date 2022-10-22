package org.valkyrienskies.mod.mixin.feature.seamless_copy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk.ChunkCompileTask;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.ChunkRenderDispatcherDuck;

@Mixin(ChunkRenderDispatcher.class)
public abstract class MixinChunkRenderDispatcher implements ChunkRenderDispatcherDuck {

    @Unique
    private final Deque<List<ChunkCompileTask>> linkedTasksQueue = new ArrayDeque<>();
    @Shadow
    @Final
    private Queue<ChunkBufferBuilderPack> freeBuffers;
    @Shadow
    private volatile int freeBufferCount;
    @Shadow
    @Final
    private Executor executor;
    @Shadow
    @Final
    private ProcessorMailbox<Runnable> mailbox;

    @Shadow
    protected abstract void runTask();

    @Override
    public void vs_scheduleLinked(final List<ChunkCompileTask> tasks) {
        linkedTasksQueue.add(tasks);
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/PriorityQueue;poll()Ljava/lang/Object;"
        ),
        method = "runTask",
        cancellable = true
    )
    private void beforeCompileChunks(final CallbackInfo ci) {
        final SeamlessChunksManager manager = SeamlessChunksManager.get();
        if (manager == null) {
            return;
        }

        final List<ChunkCompileTask> tasks = linkedTasksQueue.peek();

        if (tasks != null && freeBufferCount >= tasks.size()) {
            System.out.println("Executing multiple chunk updates at once");
            final List<ChunkBufferBuilderPack> buffers = tasks.stream().map(t -> this.freeBuffers.remove())
                .collect(Collectors.toList());

            linkedTasksQueue.remove();

            final List<CompletableFuture<ChunkRenderDispatcher.ChunkTaskResult>> resultsFutures = new ArrayList<>();

            for (int i = 0; i < tasks.size(); i++) {
                final int j = i;
                resultsFutures.add(CompletableFuture.runAsync(() -> {}, this.executor)
                    .thenCompose(void_ -> tasks.get(j).doTask(buffers.get(j))));
            }

            Util.sequence(resultsFutures).whenComplete((results, throwable) -> {
                if (throwable != null) {
                    final CrashReport crashReport = CrashReport.forThrowable(throwable, "Batching VS assembly chunks");
                    Minecraft.getInstance().delayCrash(Minecraft.getInstance().fillReport(crashReport));
                } else {
                    this.mailbox.tell(() -> {
                        for (int i = 0; i < tasks.size(); i++) {
                            final ChunkRenderDispatcher.ChunkTaskResult result = results.get(i);
                            final ChunkBufferBuilderPack buffer = buffers.get(i);
                            if (result == ChunkRenderDispatcher.ChunkTaskResult.SUCCESSFUL) {
                                buffer.clearAll();
                            } else {
                                buffer.discardAll();
                            }

                            this.freeBuffers.add(buffer);
                            this.freeBufferCount = this.freeBuffers.size();
                        }

                        this.runTask();
                    });
                }
            });

            ci.cancel();
        }
    }

}

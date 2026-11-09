package org.valkyrienskies.mod.common.render.batched;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

public class RenderPortTest {
    static final class RecordingConsumer implements VertexConsumer {
        float x, y, z;
        int vertices, color, light, overlay;
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            vertices++;
            return this;
        }
        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            color = (a << 24) | (r << 16) | (g << 8) | b;
            return this;
        }
        @Override
        public VertexConsumer setUv(float u, float v) { return this; }
        @Override
        public VertexConsumer setUv1(int u, int v) { overlay = u | (v << 16); return this; }
        @Override
        public VertexConsumer setUv2(int u, int v) { light = u | (v << 16); return this; }
        @Override
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    @Test
    public void verifyOffsetConsumer() {
        var delegate = new RecordingConsumer();
        var consumer = new OffsetVertexConsumer().set(delegate, 1024.25, -8.5, 3.75);
        consumer.addVertex(1, 2, 3).setColor(1, 2, 3, 255).setUv(0, 0).setOverlay(7).setLight(11).setNormal(0, 1, 0);
        assertTrue(delegate.vertices == 1 && delegate.x == 1025.25f && delegate.y == -6.5f && delegate.z == 6.75f, "fluent vertex offset");
        assertTrue(delegate.color == 0xff010203 && delegate.overlay == 7 && delegate.light == 11, "fluent attributes");
        consumer.addVertex(2, 3, 4, 0xff123456, 0, 1, 19, 23, 0, 1, 0);
        assertTrue(delegate.vertices == 2 && delegate.x == 1026.25f && delegate.y == -5.5f && delegate.z == 7.75f, "packed vertex offset");
        assertTrue(delegate.color == 0xff123456 && delegate.overlay == 19 && delegate.light == 23, "packed attributes");
    }

    @Test
    public void verifySortedMeshLifecycle() {
        try (var storage = new ByteBufferBuilder(4096)) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            for (float x : new float[]{1, 10}) {
                builder.addVertex(x, 0, 0); builder.addVertex(x, 1, 0);
                builder.addVertex(x, 1, 1); builder.addVertex(x, 0, 1);
            }
            MeshData.SortState sorting;
            try (var mesh = builder.buildOrThrow()) {
                sorting = mesh.sortQuads(storage, VertexSorting.DISTANCE_TO_ORIGIN);
                assertTrue(mesh.drawState().vertexCount() == 8, "mesh vertex count");
                assertTrue(mesh.indexBuffer().order(ByteOrder.nativeOrder()).getShort(0) == 4, "far quad first");
            }
            storage.clear();
            try (var indices = sorting.buildSortedIndexBuffer(storage, VertexSorting.byDistance(20, 0, 0))) {
                assertTrue(indices.byteBuffer().order(ByteOrder.nativeOrder()).getShort(0) == 0, "resort reuses centroids after mesh closes");
            }
            storage.clear();
            var empty = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            assertTrue(empty.build() == null, "empty geometry produces no upload");
        }
    }
}

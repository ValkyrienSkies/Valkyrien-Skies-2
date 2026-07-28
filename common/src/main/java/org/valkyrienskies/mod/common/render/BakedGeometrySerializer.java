package org.valkyrienskies.mod.common.render;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Binary format for a ship's baked geometry, meant to be read by a non-JVM consumer
 * (a website renderer) — no Mojang types (Direction, ResourceLocation, BlockState) survive
 * into the byte stream itself; they're flattened to primitives/strings on write and never
 * reconstructed as Mojang types on read. Strings use a length-prefixed *standard* UTF-8
 * encoding (4-byte int length + raw UTF-8 bytes) rather than DataOutputStream.writeUTF's
 * Java-specific "modified UTF-8" — that distinction matters here specifically because the
 * reader is not Java.
 *
 * Deliberately does NOT serialize BlockState — the .vdex's StructureTemplate NBT is already
 * the source of truth for what block is actually at a position; this format is visual data
 * only (geometry to draw), keyed purely by an opaque palette index.
 *
 * Texture ResourceLocations are also palette-deduplicated (v2): most ships reuse the same
 * handful of textures (mainly atlas sheets) across thousands of baked geometries, so each
 * distinct location is written once up front and geometries reference it by index instead
 * of repeating the full string.
 *
 * Render type names are palette-deduplicated the same way (v3): a ship might have thousands
 * of geometries but only a small handful of distinct RenderTypes (solid, cutout, translucent,
 * etc.), so this is an even bigger win per-entry than the texture palette.
 *
     * FORMAT_VERSION 3 — BlockEntity NBT section: after the geometry-entries table, a trailing
     * section carries each BlockEntity's own NBT (BlockEntity.saveWithFullMetadata), one record per
     * actual instance. Each record's key is the same wire key the geometry palette uses for that
     * entry — the plain canonical BlockState string for non-BE blocks, or
     * canonicalKey(state)+"#"+nbtFingerprint(nbt) for BE blocks (so two same-state tanks with
     * different fluid/level land in distinct palette entries). Per-instance (not deduped per state):
     * two chests share the state part of the key but differ in the fingerprint part and in the
     * carried NBT. The NBT is written with vanilla NbtIo framing (self-delimiting); a consumer
     * joins beInstances[i].wireKey -> entries[j] to attach the data to the right rendered model. A
     * reader that only wants models can stop after entries and skip this section.
 *
 * FORMAT_VERSION 2 — vertex-topology invariant: every BakedGeometry's vertex list is now
 * always a flat, independent triangle list. vertices.size() is guaranteed to be a multiple
 * of 3, and every consecutive group of 3 is one triangle sharing no vertices with any other
 * triangle in the list. Earlier revisions implicitly wrote whatever grouping the source draw
 * call happened to use (a 4-vertex quad in the common case, but sometimes a whole
 * multi-primitive buffer as a single opaque group for block entities) and left it to the
 * reader to guess the boundaries — that guesswork is gone as of this version. Bakery
 * (org.valkyrienskies.mod.common.render.Bakery) is responsible for the QUADS/TRIANGLES/
 * TRIANGLE_STRIP/TRIANGLE_FAN -> flat-triangle expansion before anything reaches this class;
 * this serializer itself doesn't need to know or care what the source primitive mode was.
 */
public class BakedGeometrySerializer {

    public static class BlockStateKeys {

        /**
         * Produces the same string form a website consumer builds from StructureTemplate NBT:
         * "<block registry name>[prop1=val1,prop2=val2,...]" with properties sorted by name for
         * a stable key regardless of iteration order. Must match whatever the website's own
         * NBT-to-key logic does exactly, or lookups will silently miss — this is the one piece
         * of the format that has to be agreed on byte-for-byte between the two implementations,
         * so pin this down with the frontend dev before either side changes it.
         */
        public static String canonicalKey(BlockState state) {
            String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

            Map<String, String> sortedProps = new TreeMap<>();
            for (Property<?> prop : state.getProperties()) {
                sortedProps.put(prop.getName(), getValueString(state, prop));
            }

            if (sortedProps.isEmpty()) {
                return blockName;
            }

            StringBuilder sb = new StringBuilder(blockName).append('[');
            boolean first = true;
            for (var entry : sortedProps.entrySet()) {
                if (!first) sb.append(',');
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return sb.append(']').toString();
        }

        private static <T extends Comparable<T>> String getValueString(BlockState state, Property<T> prop) {
            T value = state.getValue(prop);
            return prop.getName(value); // Property.getName(T) gives the NBT-matching string form
        }
    }

    /**
     * One BlockEntity instance's full NBT, keyed by the same wire key the geometry palette uses for
     * that state, so a consumer can join it back to the rendered model. For non-BE blocks the wire
     * key is the plain canonical BlockState string (see {@link BlockStateKeys#canonicalKey}); for BE
     * blocks it's {@code canonicalKey(state) + "#" + nbtFingerprint(nbt)} (computed in SectionBakery),
     * so two same-state BE blocks with different NBT land in distinct palette entries — each with its
     * own geometry, since a BE's rendered mesh is BlockEntityRenderer-driven (fluid level/type,
     * etc.) and is not a pure function of BlockState. Per-instance (not deduped per state): two
     * chests share the *state* part of the key but differ in the fingerprint part and in the carried
     * NBT.
     */
    public record BlockEntityEntry(String wireKey, CompoundTag nbt) {
    }

    private static final int FORMAT_VERSION = 3;
    private static final int MAGIC = 0x56444558; // "VDEX"

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Walks every geometry in the palette and returns the distinct, non-null texture
     * locations in first-seen order. LinkedHashSet so the palette (and therefore the
     * indices assigned below) is deterministic across repeated bakes of the same ship.
     */
    private static List<ResourceLocation> collectTexturePalette(Map<String, List<Bakery.BakedGeometry>> palette) {
        Set<ResourceLocation> textures = new LinkedHashSet<>();
        for (List<Bakery.BakedGeometry> geomList : palette.values()) {
            for (Bakery.BakedGeometry g : geomList) {
                ResourceLocation tex = g.textureResLoc();
                if (tex != null) {
                    textures.add(tex);
                }
            }
        }
        return new ArrayList<>(textures);
    }

    /**
     * Same idea as collectTexturePalette, for render type names instead. LinkedHashSet for
     * the same determinism reason.
     */
    private static List<String> collectRenderTypePalette(Map<String, List<Bakery.BakedGeometry>> palette) {
        Set<String> renderTypes = new LinkedHashSet<>();
        for (List<Bakery.BakedGeometry> geomList : palette.values()) {
            for (Bakery.BakedGeometry g : geomList) {
                renderTypes.add(g.renderTypeName());
            }
        }
        return new ArrayList<>(renderTypes);
    }

    private static void writeGeometry(
        DataOutputStream out,
        Bakery.BakedGeometry g,
        Map<ResourceLocation, Integer> textureIndices,
        Map<String, Integer> renderTypeIndices
    ) throws IOException {
        List<Bakery.VertexData> verts = g.vertices();
        if (verts.size() % 3 != 0) {
            // Format-v2 invariant: Bakery must hand us a flat triangle list. Catching this here
            // (cheap — just a modulo) turns a silent downstream corruption into a loud failure
            // at the one place that actually knows the invariant was violated.
            throw new IOException(
                "Bread Factory: BakedGeometry vertex count " + verts.size()
                    + " is not a multiple of 3 (expected a flat triangle list) for render type "
                    + g.renderTypeName()
            );
        }

        // renderTypeName() is never null (RenderType itself is never null on a BakedGeometry),
        // so unlike the texture index there's no -1/"none" case to carry here.
        out.writeInt(renderTypeIndices.get(g.renderTypeName()));

        ResourceLocation tex = g.textureResLoc();
        // -1 means "no texture" (mirrors the old null-boolean case); otherwise an index
        // into the texture palette written up front in write().
        out.writeInt(tex != null ? textureIndices.get(tex) : -1);

        Direction cull = g.cullFace();
        out.writeByte(cull != null ? cull.ordinal() : -1);

        out.writeInt(verts.size());
        for (Bakery.VertexData v : verts) {
            out.writeFloat((float) v.x());
            out.writeFloat((float) v.y());
            out.writeFloat((float) v.z());
            out.writeByte(v.r());
            out.writeByte(v.g());
            out.writeByte(v.b());
            out.writeByte(v.a());
            out.writeFloat(v.u());
            out.writeFloat(v.v());
            out.writeShort(v.overlayU());
            out.writeShort(v.overlayV());
            out.writeShort(v.lightmapU());
            out.writeShort(v.lightmapV());
            out.writeFloat(v.normalX());
            out.writeFloat(v.normalY());
            out.writeFloat(v.normalZ());
        }
    }

    private static Bakery.BakedGeometry readGeometry(
        DataInputStream in,
        List<ResourceLocation> texturePalette,
        List<String> renderTypePalette
    ) throws IOException {
        int renderTypeIndex = in.readInt();
        String renderTypeName = renderTypePalette.get(renderTypeIndex);

        int texIndex = in.readInt();
        ResourceLocation tex = texIndex >= 0 ? texturePalette.get(texIndex) : null;

        byte cullOrdinal = in.readByte();
        Direction cull = cullOrdinal >= 0 ? Direction.values()[cullOrdinal] : null;

        int vertCount = in.readInt();
        if (vertCount % 3 != 0) {
            throw new IOException(
                "Bread Factory: vdexgeom vertex count " + vertCount
                    + " is not a multiple of 3 (expected a flat triangle list) for render type "
                    + renderTypeName
            );
        }
        List<Bakery.VertexData> vertices = new ArrayList<>(vertCount);
        for (int i = 0; i < vertCount; i++) {
            double x = in.readFloat();
            double y = in.readFloat();
            double z = in.readFloat();
            int r = in.readByte() & 0xFF;
            int g = in.readByte() & 0xFF;
            int b = in.readByte() & 0xFF;
            int a = in.readByte() & 0xFF;
            float u = in.readFloat();
            float v = in.readFloat();
            short overlayU = in.readShort();
            short overlayV = in.readShort();
            short lightmapU = in.readShort();
            short lightmapV = in.readShort();
            float nx = in.readFloat();
            float ny = in.readFloat();
            float nz = in.readFloat();

            vertices.add(new Bakery.VertexData(x, y, z, r, g, b, a, u, v,
                overlayU, overlayV, lightmapU, lightmapV, nx, ny, nz));
        }

        return new Bakery.BakedGeometry(renderTypeName, cull, tex, vertices);
    }

    /**
     * @param palette canonical BlockState key (BlockStateKeys.canonicalKey) -> baked geometry
     *                for that state. Bake each distinct BlockState across the whole ship once
     *                — LinkedHashMap so file output is deterministic across repeated bakes.
     */
    /**
     * Writes baked geometry and per-instance BlockEntity NBT.
     *
     * @param palette    canonical BlockState key -> baked geometry for that state (deduped per state)
     * @param beInstances per-instance BlockEntity NBT, keyed by the same canonical BlockState string.
     *                   Multiple records may share the same canonicalKey (two chests, same state,
     *                   different contents). Empty list if the ship has no block entities.
     */
    public static void write(OutputStream rawOut, Map<String, List<Bakery.BakedGeometry>> palette,
        List<BlockEntityEntry> beInstances) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(rawOut)))) {

            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);

            // Texture and render-type palettes go first so the reader has them in hand
            // before any geometry (which only carries indices) shows up.
            List<ResourceLocation> texturePalette = collectTexturePalette(palette);
            Map<ResourceLocation, Integer> textureIndices = new LinkedHashMap<>();
            out.writeInt(texturePalette.size());
            for (int i = 0; i < texturePalette.size(); i++) {
                ResourceLocation tex = texturePalette.get(i);
                textureIndices.put(tex, i);
                writeString(out, tex.toString());
            }

            List<String> renderTypePalette = collectRenderTypePalette(palette);
            Map<String, Integer> renderTypeIndices = new LinkedHashMap<>();
            out.writeInt(renderTypePalette.size());
            for (int i = 0; i < renderTypePalette.size(); i++) {
                String renderTypeName = renderTypePalette.get(i);
                renderTypeIndices.put(renderTypeName, i);
                writeString(out, renderTypeName);
            }

            out.writeInt(palette.size());
            for (var entry : palette.entrySet()) {
                writeString(out, entry.getKey());
                List<Bakery.BakedGeometry> geomList = entry.getValue();
                out.writeInt(geomList.size());
                for (Bakery.BakedGeometry g : geomList) {
                    writeGeometry(out, g, textureIndices, renderTypeIndices);
                }
            }

            // v3: per-instance BlockEntity NBT. Each record is the palette wire key (joins to
            // entries[] by exact string) followed by the full saveWithFullMetadata() CompoundTag
            // in vanilla NBT framing.
            out.writeInt(beInstances.size());
            for (BlockEntityEntry be : beInstances) {
                writeString(out, be.wireKey());
                NbtIo.write(be.nbt(), out);
            }
        }
    }

    /** Result of reading a v3 .vdexgeom file. */
    public record VdexGeomReadResult(
        Map<String, List<Bakery.BakedGeometry>> palette,
        List<BlockEntityEntry> beInstances
    ) {}

    public static VdexGeomReadResult read(InputStream rawIn) throws IOException {
        try (DataInputStream in = new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(rawIn)))) {

            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Bread Factory: bad magic number, not a vdex geometry file");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Bread Factory: unsupported baked geometry format version " + version
                    + " (expected " + FORMAT_VERSION + ")");
            }

            int texturePaletteSize = in.readInt();
            List<ResourceLocation> texturePalette = new ArrayList<>(texturePaletteSize);
            for (int i = 0; i < texturePaletteSize; i++) {
                texturePalette.add(new ResourceLocation(readString(in)));
            }

            int renderTypePaletteSize = in.readInt();
            List<String> renderTypePalette = new ArrayList<>(renderTypePaletteSize);
            for (int i = 0; i < renderTypePaletteSize; i++) {
                renderTypePalette.add(readString(in));
            }

            int paletteSize = in.readInt();
            Map<String, List<Bakery.BakedGeometry>> palette = new LinkedHashMap<>(paletteSize);

            for (int i = 0; i < paletteSize; i++) {
                String key = readString(in);
                int geomCount = in.readInt();
                List<Bakery.BakedGeometry> geomList = new ArrayList<>(geomCount);
                for (int g = 0; g < geomCount; g++) {
                    geomList.add(readGeometry(in, texturePalette, renderTypePalette));
                }
                palette.put(key, geomList);
            }

            // v3: per-instance BlockEntity NBT.
            int beCount = in.readInt();
            List<BlockEntityEntry> beInstances = new ArrayList<>(beCount);
            for (int i = 0; i < beCount; i++) {
                String wireKey = readString(in);
                CompoundTag nbt = NbtIo.read(in);
                beInstances.add(new BlockEntityEntry(wireKey, nbt));
            }

            return new VdexGeomReadResult(palette, beInstances);
        }
    }
}

package org.valkyrienskies.mod.forge.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

/**
 * Translates mojmap member names to SRG for @Shadow resolution in Forge 1.20.1 prod.
 *
 * Forge 1.20.1's Mixin RemapperChain is empty, so Mixin's findRemappedMethod /
 * findRemappedField return null and @Shadow methods/fields fall through to the
 * InvalidMixinException at MixinPreProcessorStandard. ForgeGradle mods dodge
 * this because MixinGradle's AP rewrites @Shadow names to SRG at compile time;
 * :common compiles under fabric-loom which doesn't do that rewrite. Instead we
 * feed RemapperChain at runtime from a companion lookup emitted alongside the
 * refmap by generate-common-forge-refmap.gradle.
 *
 * JSON shape:
 *   { "<owner-internal>": {
 *         "methods": { "<mojmapName><desc>": "<srgName>" },
 *         "fields":  { "<mojmapName>": "<srgName>" } } }
 */
public final class VSMixinRemapper implements IRemapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("VSMixinRemapper");
    private static final String RESOURCE_PATH = "/valkyrienskies-shadow-mappings.json";

    private final Map<String, Map<String, String>> methods;
    private final Map<String, Map<String, String>> fields;

    private VSMixinRemapper(final Map<String, Map<String, String>> methods,
                            final Map<String, Map<String, String>> fields) {
        this.methods = methods;
        this.fields = fields;
    }

    public static VSMixinRemapper load() {
        final Map<String, Map<String, String>> methods = new HashMap<>();
        final Map<String, Map<String, String>> fields = new HashMap<>();
        try (InputStream in = VSMixinRemapper.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("{} not found on classpath; @Shadow resolution against SRG prod will fail", RESOURCE_PATH);
                return new VSMixinRemapper(methods, fields);
            }
            final JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (final Map.Entry<String, JsonElement> ownerEntry : root.entrySet()) {
                final JsonObject ownerObj = ownerEntry.getValue().getAsJsonObject();
                if (ownerObj.has("methods")) {
                    final Map<String, String> m = new HashMap<>();
                    for (final Map.Entry<String, JsonElement> e : ownerObj.getAsJsonObject("methods").entrySet()) {
                        m.put(e.getKey(), e.getValue().getAsString());
                    }
                    methods.put(ownerEntry.getKey(), m);
                }
                if (ownerObj.has("fields")) {
                    final Map<String, String> f = new HashMap<>();
                    for (final Map.Entry<String, JsonElement> e : ownerObj.getAsJsonObject("fields").entrySet()) {
                        f.put(e.getKey(), e.getValue().getAsString());
                    }
                    fields.put(ownerEntry.getKey(), f);
                }
            }
            LOGGER.info("Loaded {} owners ({} methods + {} fields) from {}",
                methods.size() + fields.size(),
                methods.values().stream().mapToInt(Map::size).sum(),
                fields.values().stream().mapToInt(Map::size).sum(),
                RESOURCE_PATH);
        } catch (final Exception ex) {
            LOGGER.error("Failed to load {}", RESOURCE_PATH, ex);
        }
        return new VSMixinRemapper(methods, fields);
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String desc) {
        final Map<String, String> forOwner = methods.get(owner);
        if (forOwner == null) {
            return name;
        }
        final String srg = forOwner.get(name + desc);
        return srg != null ? srg : name;
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String desc) {
        final Map<String, String> forOwner = fields.get(owner);
        if (forOwner == null) {
            return name;
        }
        final String srg = forOwner.get(name);
        return srg != null ? srg : name;
    }

    @Override
    public String map(final String typeName) {
        return typeName;
    }

    @Override
    public String unmap(final String typeName) {
        return typeName;
    }

    @Override
    public String mapDesc(final String desc) {
        return desc;
    }

    @Override
    public String unmapDesc(final String desc) {
        return desc;
    }
}

package org.valkyrienskies.mod.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;

public class RenderingEvents {

    private static List<Consumer<ShipStartRenderEvent>> onShipsStartRendering = new ArrayList<>();
    private static List<Consumer<ShipRender>> onShipRender = new ArrayList<>();
    private static List<Consumer<ShipRender>> afterShipRender = new ArrayList<>();

    public static void onShipsStartRendering(final Consumer<ShipStartRenderEvent> event) {
        onShipsStartRendering.add(event);
    }

    public static void onShipRender(final Consumer<ShipRender> event) {
        onShipRender.add(event);
    }

    public static void afterShipRender(final Consumer<ShipRender> event) {
        afterShipRender.add(event);
    }

    public static void shipsStartRendering(final ShipStartRenderEvent event) {
        onShipsStartRendering.forEach((consumer) -> consumer.accept(event));
    }

    public static void shipRendering(final ShipRender event) {
        onShipRender.forEach((consumer) -> consumer.accept(event));
    }

    public static void afterShipRendered(final ShipRender event) {
        afterShipRender.forEach((consumer) -> consumer.accept(event));
    }

    public static boolean eventsAreWorking() {
        return ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.VANILLA;
    }

    public record ShipStartRenderEvent(
        LevelRenderer renderer,
        RenderType renderType,
        PoseStack poseStack,
        double camX, double camY, double camZ,
        Matrix4f projectionMatrix) {
    }

    public record ShipRender(
        LevelRenderer renderer,
        RenderType renderType,
        PoseStack poseStack,
        double camX, double camY, double camZ,
        Matrix4f projectionMatrix,
        ClientShip ship,
        ObjectList<RenderChunkInfo> chunks) {
    }
}

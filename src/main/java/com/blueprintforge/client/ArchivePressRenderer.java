package com.blueprintforge.client;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.logic.ArchivePress;
import com.blueprintforge.machine.ProjectBureauBlock;
import com.blueprintforge.machine.ProjectBureauBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * The press inside the bureau. Raised while the bureau is idle or assembling.
 * During a research step or a copy it strikes, faster as the shaft spins faster.
 */
public class ArchivePressRenderer implements BlockEntityRenderer<ProjectBureauBlockEntity> {
    public static final ResourceLocation TEXTURE = BlueprintForge.id("textures/block/archive_press.png");

    public ArchivePressRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ProjectBureauBlockEntity bureau, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = bureau.getBlockState();
        float down = ArchivePress.headDown(bureau.isPressing(), Math.abs(bureau.getSpeed()),
                bureau.pressProgress(), bureau.pressTotal(), partialTick);
        float yaw = switch (state.getValue(ProjectBureauBlock.FACING)) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5, 0.0, -0.5);
        float drop = down * (8.0F / 16.0F);
        float headTop = (10.0F / 16.0F) - drop;
        float headBottom = headTop - (2.0F / 16.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        box(consumer, matrix, 2 / 16.0F, headBottom, 4 / 16.0F, 14 / 16.0F, headTop, 12 / 16.0F, light, overlay);
        box(consumer, matrix, 7 / 16.0F, headTop, 7 / 16.0F, 9 / 16.0F, 14 / 16.0F, 9 / 16.0F, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectBureauBlockEntity bureau) {
        return false;
    }

    @Override
    public boolean shouldRender(ProjectBureauBlockEntity bureau, Vec3 camera) {
        return true;
    }

    private static void box(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1,
                            int light, int overlay) {
        quad(consumer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light, overlay);
        quad(consumer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, light, overlay);
        quad(consumer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, light, overlay);
        quad(consumer, matrix, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, light, overlay);
        quad(consumer, matrix, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, light, overlay);
        quad(consumer, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, light, overlay);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, int light, int overlay) {
        vertex(consumer, matrix, x0, y0, z0, 0, 0, nx, ny, nz, light, overlay);
        vertex(consumer, matrix, x1, y1, z1, 1, 0, nx, ny, nz, light, overlay);
        vertex(consumer, matrix, x2, y2, z2, 1, 1, nx, ny, nz, light, overlay);
        vertex(consumer, matrix, x3, y3, z3, 0, 1, nx, ny, nz, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light, int overlay) {
        consumer.addVertex(matrix, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }
}

package com.blueprintforge.client;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.data.BlueprintClass;
import com.blueprintforge.data.BlueprintData;
import com.blueprintforge.data.BlueprintRegistry;
import com.blueprintforge.data.TierRegistry;
import com.blueprintforge.item.BlueprintItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paper sheet, the target item drawn on it, a tier-colored frame, and the remaining runs of a copy.
 * The item model is {@code builtin/entity}, so this is the only icon the player sees.
 */
public final class BlueprintIconRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float EDGE = 1.0F / 16.0F;

    public BlueprintIconRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer,
                             int light, int overlay) {
        BlueprintData data = BlueprintItem.data(stack).orElse(null);
        ResourceLocation texture = texture(data == null ? null : data.clazz());
        VertexConsumer paper = buffer.getBuffer(RenderType.entityCutout(texture));
        quad(paper, pose.last(), 0, 0, 1, 1, 0, 0, 0, 1, 1, 0xFFFFFF, light, overlay);

        if (data != null) {
            renderTarget(data, context, pose, buffer, light, overlay);
            int color = TierRegistry.get(data.tierId()).map(tier -> tier.color() & 0xFFFFFF).orElse(0x9E9E9E);
            VertexConsumer frame = buffer.getBuffer(RenderType.entityCutout(texture));
            quad(frame, pose.last(), 0, 0, 1, EDGE, 0.02F, 0.45F, 0.45F, 0.55F, 0.55F, color, light, overlay);
            quad(frame, pose.last(), 0, 1 - EDGE, 1, 1, 0.02F, 0.45F, 0.45F, 0.55F, 0.55F, color, light, overlay);
            quad(frame, pose.last(), 0, EDGE, EDGE, 1 - EDGE, 0.02F, 0.45F, 0.45F, 0.55F, 0.55F, color, light, overlay);
            quad(frame, pose.last(), 1 - EDGE, EDGE, 1, 1 - EDGE, 0.02F, 0.45F, 0.45F, 0.55F, 0.55F, color, light, overlay);
            if (context == ItemDisplayContext.GUI && data.clazz() == BlueprintClass.COPY && data.runsRemaining() >= 0) {
                drawRuns(pose, buffer, light, data.runsRemaining());
            }
        }
    }

    private static void renderTarget(BlueprintData data, ItemDisplayContext context, PoseStack pose,
                                     MultiBufferSource buffer, int light, int overlay) {
        ResourceLocation target = BlueprintRegistry.get(data.definitionId()).flatMap(def -> def.target()).orElse(null);
        if (target == null) {
            return;
        }
        var item = BuiltInRegistries.ITEM.get(target);
        if (item == null || item == Items.AIR) {
            return;
        }
        pose.pushPose();
        pose.translate(0.62F, 0.58F, 0.04F);
        pose.scale(0.36F, 0.36F, 0.36F);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(item), context, light, overlay, pose, buffer,
                Minecraft.getInstance().level, 1);
        pose.popPose();
    }

    private static void drawRuns(PoseStack pose, MultiBufferSource buffer, int light, int runs) {
        pose.pushPose();
        pose.translate(0, 0, 0.12F);
        pose.scale(1.0F / 16.0F, -1.0F / 16.0F, 1.0F);
        pose.translate(1.0F, -15.0F, 0);
        Font font = Minecraft.getInstance().font;
        font.drawInBatch(Component.literal(Integer.toString(runs)), 0, 0, 0xFFFFFF, true, pose.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    private static ResourceLocation texture(BlueprintClass clazz) {
        String name = switch (clazz) {
            case null -> "blueprint_blank";
            case ORIGINAL -> "blueprint_original";
            case COPY -> "blueprint_copy";
            case ANCIENT -> "blueprint_ancient";
            case FRAGMENT -> "blueprint_fragment";
        };
        return BlueprintForge.id("textures/item/" + name + ".png");
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1, int color, int light, int overlay) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        consumer.addVertex(pose, x0, y0, z).setColor(red, green, blue, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x1, y0, z).setColor(red, green, blue, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x1, y1, z).setColor(red, green, blue, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose, x0, y1, z).setColor(red, green, blue, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
    }
}

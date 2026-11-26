package com.blueprintforge.research;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * One step of a generated chain: a machine operation, and for a deployer step also the resource it hand-feeds.
 * {@link #key()} is the string a workpiece log records for the step and the string the run is scored against.
 *
 * Comparison rule of a deployer step (task 8): the key carries the item id and the count, so the same item with another
 * count and another item with the same count are both different keys, that is, no match at that position.
 */
public record ChainStep(ResourceLocation operation, @Nullable ResourceLocation item, int count) {
    public static final String NAMESPACE = "blueprintforge";
    public static final ResourceLocation DEPLOY = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "deploy");
    public static final String DEPLOY_PREFIX = "deploy:";

    public static ChainStep of(ResourceLocation operation) {
        return new ChainStep(operation, null, 0);
    }

    public static ChainStep deploy(ResourceLocation item, int count) {
        return new ChainStep(DEPLOY, item, count);
    }

    public boolean isDeploy() {
        return DEPLOY.equals(operation);
    }

    /** {@code press}, {@code blast}, or {@code deploy:minecraft:copper_ingot*2}. */
    public String key() {
        return isDeploy() ? deployKey(item, count) : operation.getPath();
    }

    public static String deployKey(ResourceLocation item, int count) {
        return DEPLOY_PREFIX + item + "*" + count;
    }
}

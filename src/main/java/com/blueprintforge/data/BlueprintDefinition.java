package com.blueprintforge.data;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A {@code blueprint/*.json} entry. Players never hold this record, they hold a blueprint item whose
 * {@link BlueprintData} component was built from it.
 *
 * @param tier tier id; the JSON number {@code N} resolves to {@code blueprintforge:tierN}
 */
public record BlueprintDefinition(
        ResourceLocation tier,
        BlueprintClass clazz,
        Optional<ResourceLocation> target,
        Output output,
        Optional<EfficiencyRange> materialEfficiency,
        Optional<EfficiencyRange> flux,
        Optional<EfficiencyRange> potency,
        Optional<CopyRules> copy,
        boolean namesOutput,
        Optional<Integer> fittingSlots,
        Display display,
        Optional<List<TooltipFlag>> tooltipFlags,
        List<ResourceLocation> tags,
        boolean hideT0,
        List<PotencyGrant> potencyOutput,
        Optional<ResourceLocation> line
) {
    public static final String DEFAULT_TIER_NAMESPACE = "blueprintforge";

    private static final Codec<ResourceLocation> TIER_REF = Codec.either(Codec.intRange(1, 5), ResourceLocation.CODEC).xmap(
            either -> either.map(n -> ResourceLocation.fromNamespaceAndPath(DEFAULT_TIER_NAMESPACE, "tier" + n), id -> id),
            Either::right);

    public static final Codec<BlueprintDefinition> CODEC = RecordCodecBuilder.<BlueprintDefinition>create(i -> i.group(
            TIER_REF.fieldOf("tier").forGetter(BlueprintDefinition::tier),
            BlueprintClass.CODEC.fieldOf("class").forGetter(BlueprintDefinition::clazz),
            ResourceLocation.CODEC.optionalFieldOf("target").forGetter(BlueprintDefinition::target),
            Output.CODEC.optionalFieldOf("output", Output.EMPTY).forGetter(BlueprintDefinition::output),
            EfficiencyRange.CODEC.optionalFieldOf("material_efficiency").forGetter(BlueprintDefinition::materialEfficiency),
            EfficiencyRange.CODEC.optionalFieldOf("flux").forGetter(BlueprintDefinition::flux),
            EfficiencyRange.CODEC.optionalFieldOf("potency").forGetter(BlueprintDefinition::potency),
            CopyRules.CODEC.optionalFieldOf("copy").forGetter(BlueprintDefinition::copy),
            Codec.BOOL.optionalFieldOf("names_output", false).forGetter(BlueprintDefinition::namesOutput),
            Codec.intRange(0, 16).optionalFieldOf("fitting_slots").forGetter(BlueprintDefinition::fittingSlots),
            Display.CODEC.fieldOf("display").forGetter(BlueprintDefinition::display),
            TooltipFlag.CODEC.listOf().optionalFieldOf("tooltip_flags").forGetter(BlueprintDefinition::tooltipFlags),
            ResourceLocation.CODEC.listOf().fieldOf("tags").forGetter(BlueprintDefinition::tags),
            Codec.BOOL.optionalFieldOf("hide_t0", false).forGetter(BlueprintDefinition::hideT0),
            PotencyGrant.CODEC.listOf().optionalFieldOf("potency_output", List.of()).forGetter(BlueprintDefinition::potencyOutput),
            ResourceLocation.CODEC.optionalFieldOf("line").forGetter(BlueprintDefinition::line)
    ).apply(i, BlueprintDefinition::new)).validate(BlueprintDefinition::validate);

    private static DataResult<BlueprintDefinition> validate(BlueprintDefinition def) {
        if (def.target.isEmpty() && def.clazz != BlueprintClass.FRAGMENT) {
            return DataResult.error(() -> "'target' is required for class " + def.clazz.getSerializedName());
        }
        if (def.copy.isPresent() && (def.clazz == BlueprintClass.ANCIENT || def.clazz == BlueprintClass.FRAGMENT)) {
            return DataResult.error(() -> "'copy' is not allowed for class " + def.clazz.getSerializedName());
        }
        if (def.namesOutput && def.clazz != BlueprintClass.ANCIENT) {
            return DataResult.error(() -> "'names_output' is only allowed for class ancient");
        }
        return DataResult.success(def);
    }

    /** Missing range means the value is fixed at 0 and cannot be researched. */
    public EfficiencyRange materialEfficiencyOrFixed() {
        return materialEfficiency.orElse(EfficiencyRange.FIXED_ZERO);
    }

    public EfficiencyRange fluxOrFixed() {
        return flux.orElse(EfficiencyRange.FIXED_ZERO);
    }

    public EfficiencyRange potencyOrFixed() {
        return potency.orElse(EfficiencyRange.FIXED_ZERO);
    }

    public EfficiencyRange range(com.blueprintforge.logic.ResearchAxis axis) {
        return switch (axis) {
            case MATERIAL -> materialEfficiencyOrFixed();
            case FLUX -> fluxOrFixed();
            case POTENCY -> potencyOrFixed();
        };
    }

    public record Output(List<OutputModifier> attributes, double durabilityMultiplier, int count) {
        public static final Output EMPTY = new Output(List.of(), 1.0, 1);

        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                OutputModifier.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(Output::attributes),
                Codec.doubleRange(0.0, Double.MAX_VALUE).optionalFieldOf("durability_multiplier", 1.0).forGetter(Output::durabilityMultiplier),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Output::count)
        ).apply(i, Output::new));
    }

    public record EfficiencyRange(int min, int max, int step, double costMultiplier) {
        public EfficiencyRange(int min, int max, int step) {
            this(min, max, step, 1.0);
        }

        public static final EfficiencyRange FIXED_ZERO = new EfficiencyRange(0, 0, 0);

        public static final Codec<EfficiencyRange> CODEC = RecordCodecBuilder.<EfficiencyRange>create(i -> i.group(
                Codec.intRange(0, 100).fieldOf("min").forGetter(EfficiencyRange::min),
                Codec.intRange(0, 100).fieldOf("max").forGetter(EfficiencyRange::max),
                Codec.intRange(0, 100).fieldOf("step").forGetter(EfficiencyRange::step),
                Codec.doubleRange(0.0, 100.0).optionalFieldOf("cost_multiplier", 1.0).forGetter(EfficiencyRange::costMultiplier)
        ).apply(i, EfficiencyRange::new)).validate(r -> {
            if (r.min > r.max) {
                return DataResult.error(() -> "min " + r.min + " is greater than max " + r.max);
            }
            if (r.max > r.min && r.step <= 0) {
                return DataResult.error(() -> "step must be positive when max > min");
            }
            return DataResult.success(r);
        });
    }

    public record CopyRules(
            boolean enabled,
            int defaultRuns,
            int maxRuns,
            int mePenalty,
            int fluxPenalty,
            List<CostEntry> copyCost,
            double costScaling,
            boolean allowFromCopy
    ) {
        public static final Codec<CopyRules> CODEC = RecordCodecBuilder.<CopyRules>create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CopyRules::enabled),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("default_runs").forGetter(CopyRules::defaultRuns),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_runs").forGetter(CopyRules::maxRuns),
                Codec.intRange(0, 100).optionalFieldOf("me_penalty", 10).forGetter(CopyRules::mePenalty),
                Codec.intRange(0, 100).optionalFieldOf("flux_penalty", 10).forGetter(CopyRules::fluxPenalty),
                CostEntry.CODEC.listOf().fieldOf("copy_cost").forGetter(CopyRules::copyCost),
                Codec.doubleRange(1.0, 16.0).fieldOf("cost_scaling").forGetter(CopyRules::costScaling),
                Codec.BOOL.optionalFieldOf("allow_from_copy", false).forGetter(CopyRules::allowFromCopy)
        ).apply(i, CopyRules::new)).validate(c -> c.defaultRuns > c.maxRuns
                ? DataResult.error(() -> "default_runs " + c.defaultRuns + " is greater than max_runs " + c.maxRuns)
                : DataResult.success(c));
    }

    /** A {@code copy_cost} line: either {@code {item, count}} or {@code {fluid, amount}}. */
    public record CostEntry(Either<ResourceLocation, ResourceLocation> itemOrFluid, int amount) {
        private record ItemCost(ResourceLocation item, int count) {
            static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("item").forGetter(ItemCost::item),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ItemCost::count)
            ).apply(i, ItemCost::new));
        }

        private record FluidCost(ResourceLocation fluid, int amount) {
            static final Codec<FluidCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("fluid").forGetter(FluidCost::fluid),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(FluidCost::amount)
            ).apply(i, FluidCost::new));
        }

        public static final Codec<CostEntry> CODEC = Codec.either(ItemCost.CODEC, FluidCost.CODEC).xmap(
                either -> either.map(
                        item -> new CostEntry(Either.left(item.item()), item.count()),
                        fluid -> new CostEntry(Either.right(fluid.fluid()), fluid.amount())),
                entry -> entry.itemOrFluid.map(
                        item -> Either.left(new ItemCost(item, entry.amount)),
                        fluid -> Either.right(new FluidCost(fluid, entry.amount))));
    }

    public record Display(Component name, List<Component> lore, Optional<ResourceLocation> itemModel, Optional<Component> loreLine) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Display::name),
                ComponentSerialization.CODEC.listOf().optionalFieldOf("lore", List.of()).forGetter(Display::lore),
                ResourceLocation.CODEC.optionalFieldOf("item_model").forGetter(Display::itemModel),
                ComponentSerialization.CODEC.optionalFieldOf("lore_line").forGetter(Display::loreLine)
        ).apply(i, Display::new));
    }

    /** One researched potency level and the enchantments the line writes at that level. */
    public record PotencyGrant(int level, List<EnchantGrant> enchantments) {
        public static final Codec<PotencyGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 100).fieldOf("level").forGetter(PotencyGrant::level),
                EnchantGrant.CODEC.listOf().optionalFieldOf("enchantments", List.of()).forGetter(PotencyGrant::enchantments)
        ).apply(i, PotencyGrant::new));
    }

    public record EnchantGrant(ResourceLocation enchantment, int level) {
        public static final Codec<EnchantGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(EnchantGrant::enchantment),
                Codec.intRange(1, 255).fieldOf("level").forGetter(EnchantGrant::level)
        ).apply(i, EnchantGrant::new));
    }

    public enum TooltipFlag implements StringRepresentable {
        SHOW_RUNS("show_runs"),
        SHOW_ME_TE("show_me_te"),
        SHOW_AXES("show_axes"),
        SHOW_TARGET("show_target"),
        SHOW_AUTHOR("show_author");

        public static final Codec<TooltipFlag> CODEC = StringRepresentable.fromEnum(TooltipFlag::values);

        private final String serializedName;

        TooltipFlag(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}

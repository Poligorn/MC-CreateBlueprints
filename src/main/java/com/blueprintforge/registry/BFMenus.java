package com.blueprintforge.registry;

import java.util.function.Supplier;

import com.blueprintforge.BlueprintForge;
import com.blueprintforge.machine.BlueprintArchiveMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BFMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BlueprintForge.MOD_ID);

    public static final Supplier<MenuType<BlueprintArchiveMenu>> BLUEPRINT_ARCHIVE = MENUS.register("blueprint_archive",
            () -> IMenuTypeExtension.create(BlueprintArchiveMenu::fromNetwork));

    private BFMenus() {
    }
}

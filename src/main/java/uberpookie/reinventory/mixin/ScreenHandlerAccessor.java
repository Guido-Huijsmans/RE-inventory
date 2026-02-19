package uberpookie.reinventory.mixin;

import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerAccessor {
    @Invoker("insertItem")
    boolean reinventory$insertItem(net.minecraft.item.ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}

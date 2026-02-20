package uberpookie.reinventory.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uberpookie.reinventory.client.SlotLockClient;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public class HandledScreenSlotLockMixin {

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void reinventory$drawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        var handler = ((HandledScreen)(Object)this).getScreenHandler();
        SlotLockClient.drawLockOverlay(context, handler, slot);
    }
}

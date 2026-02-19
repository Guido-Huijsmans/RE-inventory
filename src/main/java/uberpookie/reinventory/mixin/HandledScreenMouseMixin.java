package uberpookie.reinventory.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uberpookie.reinventory.client.MouseTweaksController;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class HandledScreenMouseMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reinventory$mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (MouseTweaksController.onMouseClicked((HandledScreen)(Object)this, click, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void reinventory$mouseReleased(net.minecraft.client.gui.Click click, CallbackInfoReturnable<Boolean> cir) {
        if (MouseTweaksController.onMouseReleased((HandledScreen)(Object)this, click)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void reinventory$mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (MouseTweaksController.onMouseDragged((HandledScreen)(Object)this, click, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void reinventory$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (MouseTweaksController.onMouseScrolled((HandledScreen)(Object)this, mouseX, mouseY, horizontalAmount, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }
}

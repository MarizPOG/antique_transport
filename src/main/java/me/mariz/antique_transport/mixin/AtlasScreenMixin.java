package me.mariz.antique_transport.mixin;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import me.mariz.antique_transport.client.AtlasOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AtlasScreen.class, remap = false)
public abstract class AtlasScreenMixin extends Screen {

    protected AtlasScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void antiquetrains$onMouseClicked(
            double mouseX,
            double mouseY,
            int mouseState,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (mouseState != 1) {
            return;
        }

        AtlasScreen screen = (AtlasScreen) (Object) this;
        if (AtlasOverlay.handleShipClickStatic(screen, (int) mouseX, (int) mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
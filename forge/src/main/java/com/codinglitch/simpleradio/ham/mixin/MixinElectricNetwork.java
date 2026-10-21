package com.codinglitch.simpleradio.ham.mixin;

import com.codinglitch.simpleradio.ham.compat.ElectrodynamicsInterference;
import electrodynamics.common.network.type.ElectricNetwork;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import voltaic.api.electricity.ICapabilityElectrodynamic;
import voltaic.prefab.utilities.object.TransferPack;

/**
 * Cuts the power of Electrodynamics networks that contain a bare wire disrupted by a nearby
 * high power radio transmission. Only applied when Electrodynamics is installed.
 */
@Mixin(value = ElectricNetwork.class, remap = false)
public abstract class MixinElectricNetwork {

    // A network reporting no load accepts nothing from producers, so batteries keep their charge
    @Inject(method = "getConnectedLoad", at = @At("HEAD"), cancellable = true)
    private void simpleradio$getConnectedLoad_radioInterference(ICapabilityElectrodynamic.LoadProfile loadProfile, Direction direction, CallbackInfoReturnable<TransferPack> cir) {
        if (ElectrodynamicsInterference.isDisrupted((ElectricNetwork) (Object) this)) {
            cir.setReturnValue(TransferPack.EMPTY);
        }
    }

    // Drop whatever was already buffered so the outage is immediate
    @Inject(method = "tick", at = @At("HEAD"))
    private void simpleradio$tick_radioInterference(CallbackInfo ci) {
        ElectricNetwork self = (ElectricNetwork) (Object) this;
        if (ElectrodynamicsInterference.isDisrupted(self)) {
            self.setJoulesStored(0);
        }
    }
}

package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * A mobile rig: in a vehicle (anything the player rides - a boat, a minecart, a horse, a Create seat, an
 * aircraft) it runs off the vehicle at full power with an antenna on the roof, and charges its battery.
 * Carried by hand it is a 5 W handheld.
 */
public class MobileRadioItem extends TransceiverItem {
    public static final String MOBILE = "mobile"; // whether it was in a vehicle at the last check (for the tooltip / HUD)

    public MobileRadioItem(Properties settings) {
        super(settings);
    }

    public static boolean isInVehicle(Entity entity) {
        return entity.getVehicle() != null;
    }

    /**
     * Range factor of an output power, relative to a 5 W handheld (range grows with the square root of power).
     */
    public static float rangeFactor(double watts) {
        return (float) Math.sqrt(Math.max(0.1, watts) / 5.0);
    }

    @Override
    public void entityTick(ItemStack stack, Entity entity) {
        super.entityTick(stack, entity);
        Level level = entity.level();
        if (level.isClientSide || entity.isRemoved()) return;

        CompoundTag tag = stack.getOrCreateTag();
        var config = SimpleRadioLibrary.SERVER_CONFIG.mobile;
        boolean mobile = isInVehicle(entity);
        if (tag.getBoolean(MOBILE) != mobile) tag.putBoolean(MOBILE, mobile);

        // after the battery level is applied by the handheld (every 20 ticks)
        if (level.getGameTime() % 20 != 0) return;

        // the vehicle powers the radio and charges its battery (once a second, so the item is not resent every tick)
        if (mobile && SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery && getBattery(stack) < maxBattery()) {
            setBattery(stack, getBattery(stack) + 20 * Math.max(0, config.vehicleCharging));
        }
        float power = mobile ? rangeFactor(config.vehicleWatts) : rangeFactor(config.portableWatts) * batteryRangeFactor(stack);
        float reception = mobile ? (float) Math.pow(10, config.roofAntennaGainDb / 20.0) : 1f;

        if (findTransmitter(level, tag) instanceof RadioTransmitter transmitter) {
            transmitter.powerMultiplier = power;
            transmitter.antennaSupplier = mobile ? () -> roofAntenna(entity) : null;
        }
        if (findReceiver(level, tag) instanceof RadioReceiver receiver) {
            receiver.receptionMultiplier = reception;
            receiver.antennaSupplier = mobile ? () -> roofAntenna(entity) : null;
        }
    }

    /**
     * The antenna sits on top of the vehicle (or of the rider on an open vehicle), so the cabin does not
     * shield it - a tunnel or a cave still does.
     */
    @Nullable
    private static Vector3f roofAntenna(Entity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle == null || entity.isRemoved()) return null;
        double top = Math.max(vehicle.getBoundingBox().maxY, entity.getBoundingBox().maxY) + 0.3;
        return new Vector3f((float) vehicle.getX(), (float) top, (float) vehicle.getZ());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag tooltip) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.mobile;
        boolean mobile = stack.hasTag() && stack.getTag().getBoolean(MOBILE);
        components.add(Component.translatable(mobile ? "tooltip.simpleradio.mobile.vehicle" : "tooltip.simpleradio.mobile.portable",
                Math.round(mobile ? config.vehicleWatts : config.portableWatts)).withStyle(mobile ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        super.appendHoverText(stack, level, components, tooltip);
    }
}

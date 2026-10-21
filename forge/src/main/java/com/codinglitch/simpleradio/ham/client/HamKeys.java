package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.client.ClientRadioManager;
import com.codinglitch.simpleradio.ham.block.CwKeyBlock;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Client side keying: the station push-to-talk key and holding the use key on a CW key block.
 * Both are sampled every client tick and only state changes are sent to the server.
 */
public class HamKeys {
    public static final KeyMapping STATION_PTT = new KeyMapping(
            "key.simpleradio.station_ptt",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.simpleradio.simpleradio"
    );

    public static final KeyMapping CHANNEL_UP = new KeyMapping(
            "key.simpleradio.channel_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.simpleradio.simpleradio"
    );
    public static final KeyMapping CHANNEL_DOWN = new KeyMapping(
            "key.simpleradio.channel_down",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.simpleradio.simpleradio"
    );

    private static boolean pttSent = false;
    private static boolean pttBlocked = false;
    private static BlockPos keyedCw = null;
    private static BlockPos keyedMicrophone = null;

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            pttSent = false;
            pttBlocked = false;
            keyedCw = null;
            keyedMicrophone = null;
            ClientRadioManager.stationPTT = false;
            return;
        }

        tickPushToTalk(minecraft, player);
        tickCwKey(minecraft);
        // voice chat push-to-talk follows every way of keying a station
        boolean handMicrophone = player.isUsingItem() && player.getUseItem().getItem() instanceof com.codinglitch.simpleradio.ham.item.HandMicrophoneItem;
        ClientRadioManager.stationPTT = pttSent || keyedMicrophone != null || handMicrophone;
        tickChannelKeys(player);
    }

    private static void tickPushToTalk(Minecraft minecraft, LocalPlayer player) {
        boolean down = STATION_PTT.isDown() && minecraft.screen == null;

        if (down && !pttSent && !pttBlocked) {
            if (isStationNearby(minecraft, player)) {
                HamNetwork.toServer(new HamNetwork.PushToTalk(true));
                pttSent = true;
            } else {
                player.displayClientMessage(Component.translatable("message.simpleradio.no_station"), true);
                pttBlocked = true; // only complain once per press
            }
        } else if (!down) {
            if (pttSent) HamNetwork.toServer(new HamNetwork.PushToTalk(false));
            pttSent = false;
            pttBlocked = false;
        }
    }

    // channel up / down through the stored channels of the handheld in hand
    private static void tickChannelKeys(LocalPlayer player) {
        int delta = 0;
        while (CHANNEL_UP.consumeClick()) delta++;
        while (CHANNEL_DOWN.consumeClick()) delta--;
        if (delta == 0) return;

        net.minecraft.world.InteractionHand hand = player.getMainHandItem().getItem() instanceof com.codinglitch.simpleradio.core.registry.items.TransceiverItem
                ? net.minecraft.world.InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof com.codinglitch.simpleradio.core.registry.items.TransceiverItem ? net.minecraft.world.InteractionHand.OFF_HAND : null;
        if (hand != null) HamNetwork.toServer(new HamNetwork.HandheldChannel(hand, delta));
    }

    private static boolean isStationNearby(Minecraft minecraft, LocalPlayer player) {
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -5, -5), origin.offset(5, 6, 5))) {
            if (minecraft.level.getBlockEntity(pos) instanceof StationBlockEntity) return true;
        }
        return false;
    }

    private static BlockPos lookedAt(Minecraft minecraft, Class<?> block) {
        if (minecraft.screen != null || minecraft.level == null) return null;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && block.isInstance(minecraft.level.getBlockState(hit.getBlockPos()).getBlock())) {
            return hit.getBlockPos().immutable();
        }
        return null;
    }

    private static BlockPos lookedAtCwKey(Minecraft minecraft) {
        return lookedAt(minecraft, CwKeyBlock.class);
    }

    private static void setMicrophoneTarget(BlockPos target) {
        if (Objects.equals(target, keyedMicrophone)) return;

        if (keyedMicrophone != null) HamNetwork.toServer(new HamNetwork.MicKey(keyedMicrophone, false));
        if (target != null) HamNetwork.toServer(new HamNetwork.MicKey(target, true));
        keyedMicrophone = target;
    }

    private static void setCwTarget(BlockPos target) {
        if (Objects.equals(target, keyedCw)) return;

        if (keyedCw != null) HamNetwork.toServer(new HamNetwork.CwKey(keyedCw, false));
        if (target != null) HamNetwork.toServer(new HamNetwork.CwKey(target, true));
        keyedCw = target;
    }

    // Fallback for looking away from the key while holding the button
    private static void tickCwKey(Minecraft minecraft) {
        setCwTarget(minecraft.options.keyUse.isDown() ? lookedAtCwKey(minecraft) : null);
        setMicrophoneTarget(minecraft.options.keyUse.isDown() ? lookedAt(minecraft, com.codinglitch.simpleradio.ham.block.DeskMicrophoneBlock.class) : null);
    }

    // Raw input events give millisecond timing instead of the 50 ms tick resolution
    private static void onUseInput(boolean mouse, int code, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        InputConstants.Key key = minecraft.options.keyUse.getKey();
        InputConstants.Type type = mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        if (key.getType() != type || key.getValue() != code) return;

        if (action == GLFW.GLFW_PRESS) {
            setCwTarget(lookedAtCwKey(minecraft));
            setMicrophoneTarget(lookedAt(minecraft, com.codinglitch.simpleradio.ham.block.DeskMicrophoneBlock.class));
        } else if (action == GLFW.GLFW_RELEASE) {
            setCwTarget(null);
            setMicrophoneTarget(null);
        }
    }

    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        onUseInput(true, event.getButton(), event.getAction());
    }

    public static void onKey(InputEvent.Key event) {
        onUseInput(false, event.getKey(), event.getAction());
    }
}

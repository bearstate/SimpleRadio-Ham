package com.codinglitch.simpleradio.ham.network;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamEvents;
import com.codinglitch.simpleradio.ham.block.RepeaterBlockEntity;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.menu.RepeaterMenu;
import com.codinglitch.simpleradio.ham.menu.StationMenu;
import com.codinglitch.simpleradio.ham.menu.ScannerMenu;
import com.codinglitch.simpleradio.ham.block.ScannerBlockEntity;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Packets of the amateur radio additions.
 */
public class HamNetwork {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CommonSimpleRadio.id("ham"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static final int MAX_SHIFT_KHZ = 100_000;

    public static void register() {
        int index = 0;
        CHANNEL.messageBuilder(HandheldSettings.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HandheldSettings::encode).decoder(HandheldSettings::decode).consumerMainThread(HandheldSettings::handle).add();
        CHANNEL.messageBuilder(StationSettings.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StationSettings::encode).decoder(StationSettings::decode).consumerMainThread(StationSettings::handle).add();
        CHANNEL.messageBuilder(RepeaterSettings.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RepeaterSettings::encode).decoder(RepeaterSettings::decode).consumerMainThread(RepeaterSettings::handle).add();
        CHANNEL.messageBuilder(RotateYagi.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RotateYagi::encode).decoder(RotateYagi::decode).consumerMainThread(RotateYagi::handle).add();
        CHANNEL.messageBuilder(PushToTalk.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PushToTalk::encode).decoder(PushToTalk::decode).consumerMainThread(PushToTalk::handle).add();
        CHANNEL.messageBuilder(CwKey.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CwKey::encode).decoder(CwKey::decode).consumerMainThread(CwKey::handle).add();
        CHANNEL.messageBuilder(ScannerSettings.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScannerSettings::encode).decoder(ScannerSettings::decode).consumerMainThread(ScannerSettings::handle).add();
        CHANNEL.messageBuilder(ScannerControl.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScannerControl::encode).decoder(ScannerControl::decode).consumerMainThread(ScannerControl::handle).add();
        CHANNEL.messageBuilder(LicenceSync.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LicenceSync::encode).decoder(LicenceSync::decode).consumerMainThread(LicenceSync::handle).add();
        CHANNEL.messageBuilder(ExamStart.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExamStart::encode).decoder(ExamStart::decode).consumerMainThread(ExamStart::handle).add();
        CHANNEL.messageBuilder(ExamSubmit.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ExamSubmit::encode).decoder(ExamSubmit::decode).consumerMainThread(ExamSubmit::handle).add();
        CHANNEL.messageBuilder(ExamResult.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExamResult::encode).decoder(ExamResult::decode).consumerMainThread(ExamResult::handle).add();
        CHANNEL.messageBuilder(HandheldChannel.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HandheldChannel::encode).decoder(HandheldChannel::decode).consumerMainThread(HandheldChannel::handle).add();
        CHANNEL.messageBuilder(HandheldStatus.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HandheldStatus::encode).decoder(HandheldStatus::decode).consumerMainThread(HandheldStatus::handle).add();
        CHANNEL.messageBuilder(MicKey.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MicKey::encode).decoder(MicKey::decode).consumerMainThread(MicKey::handle).add();
        CHANNEL.messageBuilder(AprsRequest.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AprsRequest::encode).decoder(AprsRequest::decode).consumerMainThread(AprsRequest::handle).add();
        CHANNEL.messageBuilder(AprsLog.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AprsLog::encode).decoder(AprsLog::decode).consumerMainThread(AprsLog::handle).add();
        CHANNEL.messageBuilder(AprsSettings.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AprsSettings::encode).decoder(AprsSettings::decode).consumerMainThread(AprsSettings::handle).add();
        CHANNEL.messageBuilder(SignalRequest.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SignalRequest::encode).decoder(SignalRequest::decode).consumerMainThread(SignalRequest::handle).add();
        CHANNEL.messageBuilder(SignalReading.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SignalReading::encode).decoder(SignalReading::decode).consumerMainThread(SignalReading::handle).add();
        CHANNEL.messageBuilder(ConfigSync.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSync::encode).decoder(ConfigSync::decode).consumerMainThread(ConfigSync::handle).add();
        CHANNEL.messageBuilder(SstvSend.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SstvSend::encode).decoder(SstvSend::decode).consumerMainThread(SstvSend::handle).add();
        CHANNEL.messageBuilder(SstvPreview.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SstvPreview::encode).decoder(SstvPreview::decode).consumerMainThread(SstvPreview::handle).add();

        TransceiverItem.statusSender = (player, status) -> CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new HandheldStatus(status.signalDb(), status.heardOn() == null ? "" : status.heardOn(), status.scanning(), status.holding()));
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    @Nullable
    private static Frequency.Modulation parseModulation(String shorthand) {
        return Frequency.modulationOf(shorthand);
    }

    private static void invalid(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.simpleradio.invalid_frequency").withStyle(ChatFormatting.RED), true);
    }

    // ---- Handheld ---- \\

    public record HandheldSettings(InteractionHand hand, String frequency, String modulation, int shift, boolean switchedOn, float toneTx, float toneRx,
                                   java.util.List<TransceiverItem.Memory> memories, int channel, boolean dualWatch, String watchFrequency, boolean scan) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
            buffer.writeUtf(frequency, 32);
            buffer.writeUtf(modulation, 8);
            buffer.writeVarInt(shift);
            buffer.writeBoolean(switchedOn);
            buffer.writeFloat(toneTx);
            buffer.writeFloat(toneRx);

            buffer.writeVarInt(Math.min(TransceiverItem.MAX_MEMORIES, memories.size()));
            for (TransceiverItem.Memory memory : memories.subList(0, Math.min(TransceiverItem.MAX_MEMORIES, memories.size()))) {
                buffer.writeUtf(memory.name(), 32);
                buffer.writeUtf(memory.frequency(), 32);
                buffer.writeUtf(memory.modulation().shorthand, 8);
                buffer.writeVarInt(memory.shift());
                buffer.writeFloat(memory.toneTx());
                buffer.writeFloat(memory.toneRx());
            }
            buffer.writeVarInt(channel);
            buffer.writeBoolean(dualWatch);
            buffer.writeUtf(watchFrequency, 32);
            buffer.writeBoolean(scan);
        }

        public static HandheldSettings decode(FriendlyByteBuf buffer) {
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            String frequency = buffer.readUtf(32);
            String modulation = buffer.readUtf(8);
            int shift = buffer.readVarInt();
            boolean switchedOn = buffer.readBoolean();
            float toneTx = buffer.readFloat();
            float toneRx = buffer.readFloat();

            int count = Math.min(TransceiverItem.MAX_MEMORIES, buffer.readVarInt());
            java.util.List<TransceiverItem.Memory> memories = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = buffer.readUtf(32);
                String memoryFrequency = buffer.readUtf(32);
                Frequency.Modulation memoryModulation = Frequency.modulationOf(buffer.readUtf(8));
                int memoryShift = buffer.readVarInt();
                float memoryToneTx = buffer.readFloat();
                float memoryToneRx = buffer.readFloat();
                String normalized = HamFrequency.normalize(memoryFrequency);
                if (normalized == null || memoryModulation == null || Math.abs(memoryShift) > MAX_SHIFT_KHZ) continue;
                if (name.length() > TransceiverItem.MAX_MEMORY_NAME) name = name.substring(0, TransceiverItem.MAX_MEMORY_NAME);
                memories.add(new TransceiverItem.Memory(name, normalized, memoryModulation, memoryShift,
                        com.codinglitch.simpleradio.radio.Ctcss.normalize(memoryToneTx), com.codinglitch.simpleradio.radio.Ctcss.normalize(memoryToneRx)));
            }
            return new HandheldSettings(hand, frequency, modulation, shift, switchedOn, toneTx, toneRx,
                    memories, buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(32), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof TransceiverItem transceiver)) return;

            String normalized = HamFrequency.normalize(frequency);
            Frequency.Modulation parsedModulation = parseModulation(modulation);
            if (normalized == null || parsedModulation == null || Math.abs(shift) > MAX_SHIFT_KHZ) {
                invalid(player);
                return;
            }
            if (shift != 0 && HamFrequency.shift(normalized, shift) == null) {
                invalid(player);
                return;
            }

            if (player.isUsingItem() && player.getUseItem() == stack) player.releaseUsingItem();
            transceiver.applySettings(stack, player.level(), normalized, parsedModulation, shift, switchedOn, toneTx, toneRx);
            transceiver.applyExtras(stack, memories, channel, dualWatch, watchFrequency, scan);
        }
    }

    /**
     * Channel up / down key: steps through a handheld's stored channels.
     */
    public record HandheldChannel(InteractionHand hand, int delta) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
            buffer.writeVarInt(delta);
        }

        public static HandheldChannel decode(FriendlyByteBuf buffer) {
            return new HandheldChannel(buffer.readEnum(InteractionHand.class), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof TransceiverItem transceiver)) return;
            if (player.isUsingItem() && player.getUseItem() == stack) return; // not while transmitting

            transceiver.stepChannel(stack, player.level(), delta > 0 ? 1 : -1);
            java.util.List<TransceiverItem.Memory> memories = TransceiverItem.getMemories(stack.getOrCreateTag());
            int channel = TransceiverItem.getChannel(stack.getOrCreateTag());
            if (channel >= 0 && channel < memories.size()) {
                TransceiverItem.Memory memory = memories.get(channel);
                player.displayClientMessage(Component.translatable("message.simpleradio.channel", channel + 1,
                        memory.name().isEmpty() ? HamFrequency.display(memory.frequency()) : memory.name()), true);
            }
        }
    }

    /**
     * What a handheld in the player's hand hears right now (for the S-meter on screen).
     */
    public record HandheldStatus(float signalDb, String heardOn, boolean scanning, boolean holding) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeFloat(signalDb);
            buffer.writeUtf(heardOn, 32);
            buffer.writeBoolean(scanning);
            buffer.writeBoolean(holding);
        }

        public static HandheldStatus decode(FriendlyByteBuf buffer) {
            return new HandheldStatus(buffer.readFloat(), buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HandheldHud.update(this));
        }
    }

    // ---- Station ---- \\


    public record StationSettings(BlockPos pos, String frequency, String modulation, int shift, int watts,
                                  float toneTx, float toneRx, int clarifierHz, boolean vox) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(frequency, 32);
            buffer.writeUtf(modulation, 8);
            buffer.writeVarInt(shift);
            buffer.writeVarInt(watts);
            buffer.writeFloat(toneTx);
            buffer.writeFloat(toneRx);
            buffer.writeVarInt(clarifierHz);
            buffer.writeBoolean(vox);
        }

        public static StationSettings decode(FriendlyByteBuf buffer) {
            return new StationSettings(buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(8), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            if (!(player.containerMenu instanceof StationMenu menu) || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (!(player.level().getBlockEntity(pos) instanceof StationBlockEntity station)) return;

            String normalized = HamFrequency.normalize(frequency);
            Frequency.Modulation parsedModulation = parseModulation(modulation);
            if (normalized == null || parsedModulation == null || Math.abs(shift) > MAX_SHIFT_KHZ) {
                invalid(player);
                return;
            }
            if (shift != 0 && HamFrequency.shift(normalized, shift) == null) {
                invalid(player);
                return;
            }

            station.applySettings(normalized, parsedModulation, shift, watts, toneTx, toneRx, clarifierHz, vox);
        }
    }

    public record RotateYagi(BlockPos pos, int steps) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(steps);
        }

        public static RotateYagi decode(FriendlyByteBuf buffer) {
            return new RotateYagi(buffer.readBlockPos(), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            if (!(player.containerMenu instanceof StationMenu menu) || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (!(player.level().getBlockEntity(pos) instanceof StationBlockEntity station)) return;

            station.rotateYagi(Math.max(-4, Math.min(4, steps)));
        }
    }

    // ---- Repeater ---- \\

    public record RepeaterSettings(BlockPos pos, String frequency, String modulation, int shift, boolean enabled,
                                   float accessTone, float outputTone, String callsign, int linkId) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(frequency, 32);
            buffer.writeUtf(modulation, 8);
            buffer.writeVarInt(shift);
            buffer.writeBoolean(enabled);
            buffer.writeFloat(accessTone);
            buffer.writeFloat(outputTone);
            buffer.writeUtf(callsign, 32);
            buffer.writeVarInt(linkId);
        }

        public static RepeaterSettings decode(FriendlyByteBuf buffer) {
            return new RepeaterSettings(buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(8), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readUtf(32), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            if (!(player.containerMenu instanceof RepeaterMenu menu) || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (!(player.level().getBlockEntity(pos) instanceof RepeaterBlockEntity repeater)) return;

            String normalized = HamFrequency.normalize(frequency);
            Frequency.Modulation parsedModulation = parseModulation(modulation);
            if (normalized == null || parsedModulation == null || shift == 0 || Math.abs(shift) > MAX_SHIFT_KHZ
                    || HamFrequency.shift(normalized, shift) == null) {
                invalid(player);
                return;
            }

            repeater.applySettings(normalized, parsedModulation, shift, enabled, accessTone, outputTone, callsign, linkId);
        }
    }

    // ---- Keying ---- \\

    public record PushToTalk(boolean down) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(down);
        }

        public static PushToTalk decode(FriendlyByteBuf buffer) {
            return new PushToTalk(buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null) HamEvents.setPushToTalk(player, down);
        }
    }

    public record CwKey(BlockPos pos, boolean down) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(down);
        }

        public static CwKey decode(FriendlyByteBuf buffer) {
            return new CwKey(buffer.readBlockPos(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null) HamEvents.setCwKey(player, pos, down);
        }
    }

    public record MicKey(BlockPos pos, boolean down) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(down);
        }

        public static MicKey decode(FriendlyByteBuf buffer) {
            return new MicKey(buffer.readBlockPos(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null) HamEvents.setMicKey(player, pos, down);
        }
    }

    // ---- Scanner ---- \\

    public record ScannerSettings(BlockPos pos, String start, String end, int step, String modulation) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(start, 32);
            buffer.writeUtf(end, 32);
            buffer.writeVarInt(step);
            buffer.writeUtf(modulation, 8);
        }

        public static ScannerSettings decode(FriendlyByteBuf buffer) {
            return new ScannerSettings(buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(32), buffer.readVarInt(), buffer.readUtf(8));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            if (!(player.containerMenu instanceof ScannerMenu menu) || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (!(player.level().getBlockEntity(pos) instanceof ScannerBlockEntity scanner)) return;

            String normalizedStart = HamFrequency.normalize(start);
            String normalizedEnd = HamFrequency.normalize(end);
            Frequency.Modulation parsedModulation = parseModulation(modulation);
            boolean validStep = java.util.Arrays.stream(ScannerBlockEntity.STEPS).anyMatch(allowed -> allowed == step);
            if (normalizedStart == null || normalizedEnd == null || parsedModulation == null || !validStep) {
                invalid(player);
                return;
            }

            scanner.applySettings(normalizedStart, normalizedEnd, step, parsedModulation);
        }
    }

    public record ScannerControl(BlockPos pos, int action) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(action);
        }

        public static ScannerControl decode(FriendlyByteBuf buffer) {
            return new ScannerControl(buffer.readBlockPos(), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;

            if (!(player.containerMenu instanceof ScannerMenu menu) || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (player.level().getBlockEntity(pos) instanceof ScannerBlockEntity scanner) scanner.control(action);
        }
    }

    // ---- Licence ---- \\

    public record LicenceSync(boolean licensed, String callsign) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(licensed);
            buffer.writeUtf(callsign, 32);
        }

        public static LicenceSync decode(FriendlyByteBuf buffer) {
            return new LicenceSync(buffer.readBoolean(), buffer.readUtf(32));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            com.codinglitch.simpleradio.ham.licence.Licences.clientLicensed = licensed;
            com.codinglitch.simpleradio.ham.licence.Licences.clientCallsign = callsign;
        }
    }

    public record ExamStart(int[] questions, int[][] orders) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarIntArray(questions);
            buffer.writeVarInt(orders.length);
            for (int[] order : orders) buffer.writeVarIntArray(order);
        }

        public static ExamStart decode(FriendlyByteBuf buffer) {
            int[] questions = buffer.readVarIntArray(64);
            int count = Math.min(64, buffer.readVarInt());
            int[][] orders = new int[count][];
            for (int i = 0; i < count; i++) orders[i] = buffer.readVarIntArray(4);
            return new ExamStart(questions, orders);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HamClientPackets.openExam(questions, orders));
        }
    }

    public record ExamSubmit(int[] answers) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarIntArray(answers);
        }

        public static ExamSubmit decode(FriendlyByteBuf buffer) {
            return new ExamSubmit(buffer.readVarIntArray(64));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null) com.codinglitch.simpleradio.ham.licence.LicenceExam.submit(player, answers);
        }
    }

    public record ExamResult(boolean passed, int score, int total, String callsign) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(passed);
            buffer.writeVarInt(score);
            buffer.writeVarInt(total);
            buffer.writeUtf(callsign, 32);
        }

        public static ExamResult decode(FriendlyByteBuf buffer) {
            return new ExamResult(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(32));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HamClientPackets.examResult(passed, score, total, callsign));
        }
    }

    // ---- APRS ---- \\

    public record AprsRequest(InteractionHand hand) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
        }

        public static AprsRequest decode(FriendlyByteBuf buffer) {
            return new AprsRequest(buffer.readEnum(InteractionHand.class));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !(player.getItemInHand(hand).getItem() instanceof com.codinglitch.simpleradio.ham.aprs.AprsRadioItem)) return;

            long now = player.level().getGameTime();
            java.util.List<AprsEntry> entries = new java.util.ArrayList<>();
            for (var heard : com.codinglitch.simpleradio.ham.aprs.AprsNetwork.log(player.getUUID(), now)) {
                var packet = heard.packet();
                entries.add(new AprsEntry(packet.callsign(), packet.dimension(), packet.x(), packet.z(), (int) ((now - heard.heardAt()) / 20),
                        heard.signalDb(), (float) packet.speedKmh(), packet.comment()));
            }
            CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new AprsLog(entries));
        }
    }

    public record AprsEntry(String callsign, String dimension, double x, double z, int ageSeconds, float signalDb, float speedKmh, String comment) {}

    public record AprsLog(java.util.List<AprsEntry> entries) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entries.size());
            for (AprsEntry entry : entries) {
                buffer.writeUtf(entry.callsign(), 16);
                buffer.writeUtf(entry.dimension(), 64);
                buffer.writeDouble(entry.x());
                buffer.writeDouble(entry.z());
                buffer.writeVarInt(entry.ageSeconds());
                buffer.writeFloat(entry.signalDb());
                buffer.writeFloat(entry.speedKmh());
                buffer.writeUtf(entry.comment(), 64);
            }
        }

        public static AprsLog decode(FriendlyByteBuf buffer) {
            int count = Math.min(64, buffer.readVarInt());
            java.util.List<AprsEntry> entries = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                entries.add(new AprsEntry(buffer.readUtf(16), buffer.readUtf(64), buffer.readDouble(), buffer.readDouble(),
                        buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readUtf(64)));
            }
            return new AprsLog(entries);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HamClientPackets.aprsLog(entries));
        }
    }

    public record AprsSettings(InteractionHand hand, boolean beacon, String comment) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
            buffer.writeBoolean(beacon);
            buffer.writeUtf(comment, 64);
        }

        public static AprsSettings decode(FriendlyByteBuf buffer) {
            return new AprsSettings(buffer.readEnum(InteractionHand.class), buffer.readBoolean(), buffer.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null) return;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof com.codinglitch.simpleradio.ham.aprs.AprsRadioItem) {
                com.codinglitch.simpleradio.ham.aprs.AprsRadioItem.applyAprs(stack, beacon, comment);
            }
        }
    }

    // ---- Config ---- \\

    /**
     * A player looks at a radio or receiver block and wants its S-meter.
     */
    public record SignalRequest(BlockPos pos) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
        }

        public static SignalRequest decode(FriendlyByteBuf buffer) {
            return new SignalRequest(buffer.readBlockPos());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !player.level().isLoaded(pos)) return;
            double reach = com.codinglitch.simpleradio.ham.radio.BlockSignal.MAX_DISTANCE;
            if (player.distanceToSqr(pos.getCenter()) > reach * reach) return;

            var reading = com.codinglitch.simpleradio.ham.radio.BlockSignal.read(player.level(), pos);
            if (reading == null) return;
            CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SignalReading(pos, reading.status(), reading.signal(), reading.frequency(), reading.modulation()));
        }
    }

    public record SignalReading(BlockPos pos, int status, int signal, String frequency, String modulation) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeByte(status);
            buffer.writeShort(signal);
            buffer.writeUtf(frequency, 32);
            buffer.writeUtf(modulation, 8);
        }

        public static SignalReading decode(FriendlyByteBuf buffer) {
            return new SignalReading(buffer.readBlockPos(), buffer.readByte(), buffer.readShort(), buffer.readUtf(32), buffer.readUtf(8));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.BlockSMeter.update(this));
        }
    }

    public record ConfigSync(java.util.Map<String, String> values) {
        private static final int MAX_ENTRIES = 4096;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(Math.min(MAX_ENTRIES, values.size()));
            int written = 0;
            for (var entry : values.entrySet()) {
                if (written++ >= MAX_ENTRIES) break;
                buffer.writeUtf(entry.getKey(), 256);
                buffer.writeUtf(entry.getValue(), 1024);
            }
        }

        public static ConfigSync decode(FriendlyByteBuf buffer) {
            int count = Math.min(MAX_ENTRIES, buffer.readVarInt());
            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
            for (int i = 0; i < count; i++) values.put(buffer.readUtf(256), buffer.readUtf(1024));
            return new ConfigSync(values);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HamClientPackets.applyServerConfig(values));
        }
    }

    // ---- SSTV ---- \\

    public record SstvSend(BlockPos pos, boolean cancel) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(cancel);
        }

        public static SstvSend decode(FriendlyByteBuf buffer) {
            return new SstvSend(buffer.readBlockPos(), buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !(player.containerMenu instanceof com.codinglitch.simpleradio.ham.menu.SstvMenu menu) || !menu.pos.equals(pos)) return;
            if (!(player.level().getBlockEntity(pos) instanceof com.codinglitch.simpleradio.ham.block.SstvTerminalBlockEntity terminal)) return;

            if (cancel) terminal.cancel();
            else terminal.send(player);
        }
    }

    public record SstvPreview(BlockPos pos, byte[] colors) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeByteArray(colors);
        }

        public static SstvPreview decode(FriendlyByteBuf buffer) {
            return new SstvPreview(buffer.readBlockPos(), buffer.readByteArray(128 * 128));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            if (colors.length != 128 * 128) return;
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.codinglitch.simpleradio.ham.client.HamClientPackets.sstvPreview(pos, colors));
        }
    }
}

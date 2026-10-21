package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.menu.SstvMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.ham.sstv.Robot36;
import com.codinglitch.simpleradio.ham.sstv.SstvFrame;
import com.codinglitch.simpleradio.radio.RadioMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * An SSTV terminal next to a station: sends a filled map as a Robot 36 picture, and prints pictures the
 * station receives onto blank maps - with the noise of a weak signal where it was weak.
 */
public class SstvTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_SEND = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int SIZE = 128;
    private static final int STATION_RADIUS = 2;

    public final SimpleContainer items = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            SstvTerminalBlockEntity.this.setChanged();
        }
    };

    @Nullable private StationBlockEntity station;
    private final Consumer<RadioMessage> listener = this::onData;
    private final Random random = new Random();

    // picture being received
    @Nullable private UUID picture;
    private boolean receiving = false;
    private boolean finished = false;
    private final byte[] received = new byte[SIZE * SIZE];
    private final boolean[] rows = new boolean[SIZE];
    private long lastFrame = 0;
    private long lastFrameMillis = 0;
    private boolean pendingPrint = false;
    private boolean previewDirty = false;
    private long now = 0;
    private boolean sending = false;
    private final java.util.Set<UUID> previewed = new java.util.HashSet<>();

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case SstvMenu.DATA_FLAGS -> (station != null ? SstvMenu.FLAG_STATION : 0)
                        | (sending ? SstvMenu.FLAG_SENDING : 0)
                        | (receiving ? SstvMenu.FLAG_RECEIVING : 0)
                        | (pendingPrint ? SstvMenu.FLAG_NEEDS_MAP : 0);
                case SstvMenu.DATA_SEND_PROGRESS -> station == null || !sending ? 0 : Math.round(station.dataProgress() * 1000);
                case SstvMenu.DATA_RECEIVE_PROGRESS -> receivedRows() * 1000 / SIZE;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return SstvMenu.DATA_COUNT;
        }
    };

    public SstvTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.SSTV_TERMINAL_BE.get(), pos, state);
    }

    private int receivedRows() {
        int count = 0;
        for (boolean row : rows) if (row) count++;
        return count;
    }

    // ---- Sending ---- \\

    public void send(ServerPlayer player) {
        String denial = startSending(player);
        if (denial != null) {
            player.displayClientMessage(Component.translatable(denial).withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("message.simpleradio.sstv.sending").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /**
     * Sends the map in the top slot through the station next to the terminal.
     * @return null when sending, otherwise the translation key of the reason it cannot
     */
    @Nullable
    public String startSending(@Nullable Player operator) {
        if (station == null) return "message.simpleradio.sstv.no_station";
        if (sending && station.isSendingData()) return "message.simpleradio.station_busy";
        ItemStack picture = items.getItem(SLOT_SEND);
        MapItemSavedData data = picture.is(Items.FILLED_MAP) && level != null ? MapItem.getSavedData(picture, level) : null;
        if (data == null) return "message.simpleradio.sstv.no_picture";

        byte[] colors = data.colors.clone();
        Robot36.Encoded encoded = Robot36.encode(colors, SimpleRadioLibrary.SERVER_CONFIG.sstv.volume);
        int frames = encoded.frames().size();
        int[] firstLine = new int[frames], lastLine = new int[frames];
        for (int f = 0; f < frames; f++) {
            double start = f * 20.0, end = start + 20;
            firstLine[f] = -1;
            lastLine[f] = -1;
            for (int line = 0; line < Robot36.LINES; line++) {
                double lineStart = encoded.lineStartMs()[line];
                if (lineStart < end && lineStart + Robot36.LINE_MS > start) {
                    if (firstLine[f] < 0) firstLine[f] = line;
                    lastLine[f] = line;
                }
            }
        }

        UUID pictureId = UUID.randomUUID();
        String denial = station.transmitData(encoded.frames(),
                index -> new SstvFrame(pictureId, colors, firstLine[index], lastLine[index], index == frames - 1), operator);
        if (denial == null) sending = true;
        return denial;
    }

    public void cancel() {
        if (station != null && sending && station.isSendingData()) station.cancelData();
        sending = false;
    }

    // ---- Receiving ---- \\

    private void onData(RadioMessage message) {
        if (!(message.payload instanceof SstvFrame frame)) return;

        if (!frame.picture().equals(picture)) {
            picture = frame.picture();
            finished = false;
            java.util.Arrays.fill(received, (byte) 0);
            java.util.Arrays.fill(rows, false);
        }
        // the rest of a picture that was already printed (e.g. after the signal came back)
        if (finished) return;
        receiving = true;
        lastFrame = now;
        lastFrameMillis = System.currentTimeMillis();

        // a weak signal turns into snow on the picture
        double severity = message.computeSeverity();
        var config = SimpleRadioLibrary.SERVER_CONFIG.sstv;
        double noise = Math.max(0, Math.min(1, (severity - config.noiseStart) / Math.max(1, config.noiseEnd - config.noiseStart)));
        noise = noise * noise;
        if (frame.firstLine() >= 0) {
            for (int line = frame.firstLine(); line <= frame.lastLine(); line++) {
                int row = Robot36.mapRow(line);
                for (int x = 0; x < SIZE; x++) {
                    int index = row * SIZE + x;
                    received[index] = random.nextDouble() < noise ? snow() : frame.colors()[index];
                }
                rows[row] = true;
            }
            previewDirty = true;
        }
        if (frame.last()) finish();
    }

    private static final net.minecraft.world.level.material.MapColor[] SNOW = {
            net.minecraft.world.level.material.MapColor.SNOW, net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY,
            net.minecraft.world.level.material.MapColor.COLOR_GRAY, net.minecraft.world.level.material.MapColor.COLOR_BLACK
    };

    private byte snow() {
        return SNOW[random.nextInt(SNOW.length)].getPackedId(net.minecraft.world.level.material.MapColor.Brightness.values()[random.nextInt(4)]);
    }

    private void finish() {
        receiving = false;
        finished = true;
        pendingPrint = true;
        previewDirty = true;
        setChanged();
    }

    private boolean print() {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        ItemStack blank = items.getItem(SLOT_BLANK);
        if (!blank.is(Items.MAP) || !items.getItem(SLOT_OUTPUT).isEmpty()) return false;

        int id = serverLevel.getFreeMapId();
        MapItemSavedData data = MapItemSavedData.createFresh(worldPosition.getX(), worldPosition.getZ(), (byte) 0, false, false, serverLevel.dimension()).locked();
        System.arraycopy(received, 0, data.colors, 0, received.length);
        data.setDirty();
        serverLevel.setMapData(MapItem.makeKey(id), data);

        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.getOrCreateTag().putInt("map", id);
        map.setHoverName(Component.translatable("item.simpleradio.sstv_picture"));
        blank.shrink(1);
        items.setItem(SLOT_OUTPUT, map);
        serverLevel.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    // ---- Ticking ---- \\

    public static void serverTick(Level level, BlockPos pos, BlockState state, SstvTerminalBlockEntity terminal) {
        terminal.tickServer((ServerLevel) level);
    }

    private void tickServer(ServerLevel level) {
        now = level.getGameTime();

        if (now % 20 == 0 || (station != null && station.isRemoved())) {
            StationBlockEntity found = findStation(level);
            if (found != station) {
                if (station != null) station.removeDataListener(listener);
                station = found;
                if (station != null) station.addDataListener(listener);
            }
        }

        if (sending && (station == null || !station.isSendingData())) sending = false;

        // the picture stopped coming in (signal lost): print what arrived
        // (in game time and real time: the audio is paced in real time, and a paused game stops the ticks)
        if (receiving && now - lastFrame > 60 && System.currentTimeMillis() - lastFrameMillis > 3000) finish();
        if (pendingPrint && print()) pendingPrint = false;

        // players looking at the terminal see the picture as it comes in
        if (now % 10 == 0) {
            if (previewDirty) previewed.clear();
            previewDirty = false;
            java.util.Set<UUID> viewing = new java.util.HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (player.containerMenu instanceof SstvMenu menu && menu.pos.equals(worldPosition)) {
                    viewing.add(player.getUUID());
                    if (previewed.add(player.getUUID())) {
                        HamNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HamNetwork.SstvPreview(worldPosition, received.clone()));
                    }
                }
            }
            previewed.retainAll(viewing);
        }
    }

    @Nullable
    private StationBlockEntity findStation(Level level) {
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-STATION_RADIUS, -STATION_RADIUS, -STATION_RADIUS), worldPosition.offset(STATION_RADIUS, STATION_RADIUS, STATION_RADIUS))) {
            if (level.getBlockEntity(pos) instanceof StationBlockEntity found) return found;
        }
        return null;
    }

    public boolean hasStation() {
        return station != null;
    }

    public byte[] picture() {
        return received;
    }

    @Override
    public void setRemoved() {
        if (station != null) station.removeDataListener(listener);
        super.setRemoved();
    }

    // ---- Saving ---- \\

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        for (int i = 0; i < list.size(); i++) items.setItem(i, list.get(i));
        if (tag.contains("picture")) {
            byte[] stored = tag.getByteArray("picture");
            if (stored.length == received.length) System.arraycopy(stored, 0, received, 0, stored.length);
        }
        pendingPrint = tag.getBoolean("pending_print");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list);
        // the last picture stays on the screen
        tag.putByteArray("picture", received);
        if (pendingPrint) tag.putBoolean("pending_print", true);
    }

    // ---- Menu ---- \\

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleradio.sstv_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SstvMenu(containerId, inventory, worldPosition, items, dataAccess);
    }
}

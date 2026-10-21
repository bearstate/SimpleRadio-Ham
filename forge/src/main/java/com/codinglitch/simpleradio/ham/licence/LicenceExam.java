package com.codinglitch.simpleradio.ham.licence;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The amateur radio licence exam: random multiple choice questions (texts in the language files,
 * {@code exam.simpleradio.q<n>} and {@code exam.simpleradio.q<n>.<0-3>}). Answers are checked on the server.
 */
public final class LicenceExam {
    /** Correct option of every question, generated together with the language files. */
    static final int[] ANSWERS = {1, 0, 2, 3, 0, 1, 2, 0, 3, 1, 2, 0, 3, 1, 0, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2};

    private record Pending(int[] questions) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private LicenceExam() {}

    public static int questionCount() {
        return ANSWERS.length;
    }

    public static int answer(int question) {
        return ANSWERS[question];
    }

    public static void start(ServerPlayer player) {
        if (Licences.isLicensed(player)) {
            player.displayClientMessage(Component.translatable("message.simpleradio.exam.already", Licences.callsign(player)).withStyle(ChatFormatting.GREEN), false);
            return;
        }

        long now = player.level().getGameTime();
        long retry = Licences.examRetryAt(player);
        if (now < retry) {
            player.displayClientMessage(Component.translatable("message.simpleradio.exam.wait", (retry - now + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }

        int count = Math.max(1, Math.min(ANSWERS.length, SimpleRadioLibrary.SERVER_CONFIG.band_plan.examQuestions));
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < ANSWERS.length; i++) pool.add(i);
        Collections.shuffle(pool, RANDOM);

        int[] questions = new int[count];
        int[][] orders = new int[count][];
        for (int i = 0; i < count; i++) {
            questions[i] = pool.get(i);
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order, RANDOM);
            orders[i] = order.stream().mapToInt(Integer::intValue).toArray();
        }

        PENDING.put(player.getUUID(), new Pending(questions));
        HamNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HamNetwork.ExamStart(questions, orders));
    }

    /**
     * @param answers the chosen option of every question (in the original option numbering), -1 when skipped
     */
    public static void submit(ServerPlayer player, int[] answers) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || answers.length != pending.questions().length) return;

        int score = score(pending.questions(), answers);
        int total = pending.questions().length;
        int passMark = Math.min(total, Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.band_plan.examPassMark));

        if (score >= passMark) {
            String callsign = Licences.grant(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f);
            player.displayClientMessage(Component.translatable("message.simpleradio.exam.passed", score, total, callsign).withStyle(ChatFormatting.GREEN), false);
            HamNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HamNetwork.ExamResult(true, score, total, callsign));
        } else {
            long retry = player.level().getGameTime() + SimpleRadioLibrary.SERVER_CONFIG.band_plan.examRetrySeconds * 20L;
            Licences.setExamRetryAt(player, retry);
            player.displayClientMessage(Component.translatable("message.simpleradio.exam.failed", score, total, passMark).withStyle(ChatFormatting.RED), false);
            HamNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HamNetwork.ExamResult(false, score, total, ""));
        }
    }

    public static int score(int[] questions, int[] answers) {
        int score = 0;
        for (int i = 0; i < questions.length; i++) {
            if (questions[i] >= 0 && questions[i] < ANSWERS.length && answers[i] == ANSWERS[questions[i]]) score++;
        }
        return score;
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }
}

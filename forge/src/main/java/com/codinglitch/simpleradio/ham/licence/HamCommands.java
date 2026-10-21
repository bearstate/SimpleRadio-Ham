package com.codinglitch.simpleradio.ham.licence;

import com.codinglitch.simpleradio.radio.propagation.Ionosphere;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /telsiz lisans [ver|iptal <player>]}, {@code /telsiz cagri <player> <call sign>}, {@code /telsiz yayilim}
 */
public final class HamCommands {
    private HamCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("telsiz")
                .then(Commands.literal("lisans")
                        .executes(HamCommands::showOwn)
                        .then(Commands.literal("ver").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("oyuncu", EntityArgument.player()).executes(HamCommands::grant)))
                        .then(Commands.literal("iptal").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("oyuncu", EntityArgument.player()).executes(HamCommands::revoke))))
                .then(Commands.literal("cagri").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("cagri_isareti", StringArgumentType.word()).executes(HamCommands::setCallsign))))
                .then(Commands.literal("yayilim").executes(HamCommands::conditions)));
    }

    private static int showOwn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (Licences.isLicensed(player)) {
            context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.licence.yes", Licences.callsign(player)).withStyle(ChatFormatting.GREEN), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.licence.no").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "oyuncu");
        String callsign = Licences.grant(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.licence.granted", player.getDisplayName(), callsign), true);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "oyuncu");
        Licences.revoke(player);
        context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.licence.revoked", player.getDisplayName()), true);
        return 1;
    }

    private static int setCallsign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "oyuncu");
        String callsign = CallsignRegistry.clean(StringArgumentType.getString(context, "cagri_isareti"));
        if (!Licences.setCallsign(player, callsign)) {
            context.getSource().sendFailure(Component.translatable("command.simpleradio.callsign.taken", callsign));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.callsign.set", player.getDisplayName(), callsign), true);
        return 1;
    }

    private static int conditions(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Ionosphere.Conditions conditions = Ionosphere.conditions(level);
        double muf = Ionosphere.maximumUsableFrequency(level);

        context.getSource().sendSuccess(() -> Component.translatable("command.simpleradio.conditions.header",
                Math.round(conditions.solarFlux()), conditions.kIndex(), String.format(Locale.ROOT, "%.1f", muf),
                Component.translatable(conditions.sporadicE() ? "command.simpleradio.conditions.es_yes" : "command.simpleradio.conditions.es_no")).withStyle(ChatFormatting.AQUA), false);

        MutableComponent bands = Component.empty();
        for (int i = 0; i < Ionosphere.DISPLAY_BANDS.length; i++) {
            Ionosphere.BandState state = Ionosphere.bandState(level, Ionosphere.DISPLAY_BANDS[i]);
            ChatFormatting color = switch (state) {
                case GOOD -> ChatFormatting.GREEN;
                case FAIR -> ChatFormatting.YELLOW;
                case POOR -> ChatFormatting.GOLD;
                case CLOSED -> ChatFormatting.RED;
            };
            if (i > 0) bands.append(Component.literal("  "));
            bands.append(Component.literal(Ionosphere.DISPLAY_BAND_NAMES[i] + ": ").withStyle(ChatFormatting.GRAY));
            bands.append(Component.translatable("band.simpleradio.state." + state.name().toLowerCase(Locale.ROOT)).withStyle(color));
        }
        context.getSource().sendSuccess(() -> bands, false);
        return 1;
    }
}

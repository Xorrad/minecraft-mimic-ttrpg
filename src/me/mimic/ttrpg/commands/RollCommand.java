package me.mimic.ttrpg.commands;

import me.mimic.lib.commands.CommandGroup;
import me.mimic.lib.commands.CommandParameter;
import me.mimic.ttrpg.core.Dice;
import me.mimic.ttrpg.localization.Localization;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// /roll [dice], e.g. "/roll 4d16+3-2-1+8". Without dice, rolls 1d20.
public class RollCommand extends CommandGroup {

    // Players within this distance of the roller see the result.
    private static final double AUDIENCE_RADIUS = 32;
    // Dice groups with more dice than this only show their sum, to keep the message short.
    private static final int MAX_SHOWN_ROLLS = 20;

    public RollCommand() {
        super("roll");

        newDefaultCommand()
                .description("Roll dice, e.g. 4d16+3-2-1+8")
                .param(new CommandParameter("dice", CommandParameter.Type.STRING, "1d20").greedy())
                .complete((sender, args) -> args.length == 1 ? List.of("1d20", "1d100", "2d6", "4d6") : Collections.emptyList())
                .execute(RollCommand::roll);
    }

    public static void roll(CommandSender sender, Object[] objects) {
        String expression = (String) objects[0];

        Dice.Result result;
        try {
            result = Dice.roll(expression, ThreadLocalRandom.current());
        }
        catch(Dice.InvalidDiceException e) {
            if(e.isOverLimit())
                sender.sendMessage(Localization.CMD_ROLL_TOO_LARGE.format(Dice.MAX_DICE, Dice.MAX_SIDES));
            else
                sender.sendMessage(Localization.CMD_ROLL_INVALID.format(expression));
            return;
        }

        String message = Localization.CMD_ROLL_RESULT.format(sender.getName(), result.expression(), describe(result), result.total());
        for(CommandSender viewer : audience(sender))
            viewer.sendMessage(message);
    }

    // Each part of the roll, e.g. "[5, 16, 1, 9] +3 -2 -1 +8". Highest rolls are green, ones are red.
    private static String describe(Dice.Result result) {
        List<String> pieces = new ArrayList<>();
        for(int i = 0; i < result.parts().size(); i++) {
            Dice.Part part = result.parts().get(i);
            String sign = part.negative() ? "-" : (i == 0 ? "" : "+");

            if(!part.isDice()) {
                pieces.add("§7" + sign + part.modifier());
                continue;
            }

            if(part.rolls().size() > MAX_SHOWN_ROLLS) {
                long sum = Math.abs(part.value());
                pieces.add("§7" + sign + "§f[" + part.rolls().size() + "d" + part.sides() + ": " + sum + "]");
                continue;
            }

            List<String> rolls = new ArrayList<>();
            for(int roll : part.rolls()) {
                String color = (roll == part.sides() && part.sides() > 1) ? "§a" : (roll == 1 ? "§c" : "§f");
                rolls.add(color + roll);
            }
            pieces.add("§7" + sign + "§f[" + String.join("§7, ", rolls) + "§f]");
        }
        return String.join(" ", pieces);
    }

    // The roller and the players near them; only the sender when rolling from the console.
    private static List<CommandSender> audience(CommandSender sender) {
        List<CommandSender> audience = new ArrayList<>();
        audience.add(sender);
        if(sender instanceof Player player) {
            double radiusSquared = AUDIENCE_RADIUS * AUDIENCE_RADIUS;
            for(Player other : player.getWorld().getPlayers()) {
                if(!other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= radiusSquared)
                    audience.add(other);
            }
        }
        return audience;
    }
}

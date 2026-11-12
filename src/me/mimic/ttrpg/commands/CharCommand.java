package me.mimic.ttrpg.commands;

import me.mimic.lib.commands.CommandGroup;
import me.mimic.lib.commands.CommandParameter;
import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.Gender;
import me.mimic.ttrpg.core.names.NameType;
import me.mimic.ttrpg.core.skins.SkinLibrary;
import net.citizensnpcs.api.npc.NPC;
import me.mimic.ttrpg.localization.Localization;
import me.mimic.ttrpg.util.CitizensUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;

public class CharCommand extends CommandGroup {

    public CharCommand() {
        super("char");

        newSubCommand("new")
                .playerOnly(true)
                .param(new CommandParameter("culture", CommandParameter.Type.STRING))
                .param(new CommandParameter("gender", CommandParameter.Type.STRING, "MALE"))
                .complete((sender, args) -> {
                    if(args.length == 1) return TTRPG.getInstance().cultures.values().stream().map(Culture::getId).filter(s -> s.startsWith(args[0].toUpperCase())).toList();
                    if(args.length == 2) return Arrays.stream(Gender.values()).map(Gender::name).filter(s -> s.startsWith(args[1].toUpperCase())).toList();
                    return Collections.emptyList();
                })
                .execute(CharCommand::newCharacterWithCulture);
    }

    public static void newCharacterWithCulture(CommandSender sender, Object[] params) {
        Player player = (Player) sender;
        String cultureId = ((String) params[0]).toUpperCase();
        String genderStr = (String) params[1];

        if(!TTRPG.getInstance().cultures.containsKey(cultureId)) {
            sender.sendMessage(Localization.CMD_CULTURE_DOESNT_EXIST.format(cultureId));
            return;
        }

        Gender gender = Gender.fromString(genderStr);
        if(gender == null) {
            sender.sendMessage(Localization.CMD_CHAR_GENDER_INVALID.format(genderStr));
            return;
        }

        Culture culture = TTRPG.getInstance().cultures.get(cultureId);
        NameType nameType = gender.getNameType();

        // Some name managers query a web service, so the name is generated off the main thread
        // and the NPC is spawned back on it.
        Bukkit.getScheduler().runTaskAsynchronously(TTRPG.getInstance(), () -> {
            String name = culture.getNameManager().get(nameType) + " " + culture.getNameManager().get(NameType.FAMILY);
            Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> {
                if(!player.isOnline())
                    return;
                NPC npc = CitizensUtil.spawnNPC(name, player.getLocation(), culture, gender);
                player.sendMessage(Localization.CMD_CHAR_CREATED.format(name, culture.getName()));
                // Dress the new NPC for its culture and gender. Quiet if the server has no skins set up.
                SkinLibrary.pickSkinFor(npc, player, true, () -> {});
            });
        });
    }
}

package me.mimic.ttrpg.commands;

import me.mimic.lib.commands.CommandGroup;
import me.mimic.lib.commands.CommandParameter;
import me.mimic.ttrpg.core.NpcSelection;
import me.mimic.ttrpg.localization.Localization;
import me.mimic.ttrpg.util.CitizensUtil;
import me.mimic.ttrpg.util.SelectionTools;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NpcsCommand extends CommandGroup {

    private static final int DEFAULT_SCATTER_RADIUS = 3;
    private static final int MAX_SCATTER_RADIUS = 32;
    private static final int DEFAULT_LINE_SPACING = 2;
    private static final int MAX_LINE_SPACING = 10;
    private static final double LINE_DISTANCE = 3;

    public NpcsCommand() {
        super("npcs");

        newSubCommand("wand")
                .playerOnly(true)
                .execute(NpcsCommand::wand);

        newSubCommand("follow")
                .playerOnly(true)
                .execute(NpcsCommand::follow);

        newSubCommand("tphere")
                .aliases("tp")
                .description("Teleport the selected NPCs to you, onto you or in a formation")
                .playerOnly(true)
                .param(CommandParameter.of("--scatter|--line|--circle", CommandParameter.Type.STRING).optional())
                .param(CommandParameter.of("size", CommandParameter.Type.INT).optional())
                .complete((sender, args) -> {
                    if(args.length == 1) return List.of("--scatter", "--line", "--circle");
                    if(args.length == 2) return List.of("2", "3", "5", "10");
                    return Collections.emptyList();
                })
                .execute(NpcsCommand::tphere);

        newSubCommand("goto")
                .description("Teleport to an NPC")
                .playerOnly(true)
                .param(CommandParameter.of("id", CommandParameter.Type.INT))
                .complete((sender, args) -> args.length == 1
                        ? NpcSelection.get((Player) sender).stream().map(npc -> String.valueOf(npc.getId())).toList()
                        : Collections.emptyList())
                .execute(NpcsCommand::goTo);

        newSubCommand("bring")
                .description("Teleport an NPC to you")
                .playerOnly(true)
                .param(CommandParameter.of("id", CommandParameter.Type.INT))
                .complete((sender, args) -> args.length == 1
                        ? NpcSelection.get((Player) sender).stream().map(npc -> String.valueOf(npc.getId())).toList()
                        : Collections.emptyList())
                .execute(NpcsCommand::bring);

        newSubCommand("brush")
                .description("Get a brush that selects NPCs within a radius")
                .playerOnly(true)
                .param(new CommandParameter("radius", CommandParameter.Type.INT, SelectionTools.DEFAULT_BRUSH_RADIUS))
                .complete((sender, args) -> args.length == 1 ? List.of("3", "5", "10", "20") : Collections.emptyList())
                .execute(NpcsCommand::brush);

        newSubCommand("list")
                .playerOnly(true)
                .execute(NpcsCommand::list);

        newSubCommand("deselect")
                .description("Remove an NPC from your selection")
                .playerOnly(true)
                .param(CommandParameter.of("id", CommandParameter.Type.INT))
                .complete((sender, args) -> args.length == 1
                        ? NpcSelection.get((Player) sender).stream().map(npc -> String.valueOf(npc.getId())).toList()
                        : Collections.emptyList())
                .execute(NpcsCommand::deselect);

        newSubCommand("clear")
                .playerOnly(true)
                .execute(NpcsCommand::clear);

        newSubCommand("names")
                .aliases("name")
                .playerOnly(true)
                .param(CommandParameter.of("state", CommandParameter.Type.STRING).optional())
                .complete((sender, args) -> args.length == 1 ? List.of("--false", "--true", "--hover") : Collections.emptyList())
                .execute(NpcsCommand::hideNames);
    }

    public static void wand(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        player.getInventory().addItem(SelectionTools.createWand());
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
    }

    public static void brush(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int radius = (int) objects[0];
        if(radius < 1 || radius > SelectionTools.MAX_BRUSH_RADIUS) {
            player.sendMessage(Localization.CMD_NPCS_BRUSH_RADIUS_INVALID.format(SelectionTools.MAX_BRUSH_RADIUS));
            return;
        }
        player.getInventory().addItem(SelectionTools.createBrush(radius));
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
    }

    public static void follow(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;

        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage(Localization.CMD_NPCS_NONE_SELECTED.format());
            return;
        }

        // If every selected NPC already follows, stop them; otherwise make them all follow the player.
        boolean allFollowing = selected.stream()
                .allMatch(npc -> ((FollowTrait) npc.getOrAddTrait(FollowTrait.class)).isEnabled());

        ArrayList<String> names = new ArrayList<>();
        for(NPC npc : selected) {
            FollowTrait trait = (FollowTrait) npc.getOrAddTrait(FollowTrait.class);
            names.add(npc.getName());
            trait.follow(allFollowing ? null : player);
        }

        if(allFollowing)
            player.sendMessage(Localization.CMD_NPCS_FOLLOW_STOPPED.format(Localization.nameList(names)));
        else
            player.sendMessage(Localization.CMD_NPCS_FOLLOW_STARTED.format(Localization.nameList(names), player.getName()));
    }

    public static void tphere(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        String option = (objects[0] == null) ? "" : ((String) objects[0]).toLowerCase();
        Integer size = (Integer) objects[1];

        Formation formation = switch(option.startsWith("--") ? option.substring(2) : option) {
            case "" -> Formation.NONE;
            case "scatter" -> Formation.SCATTER;
            case "line" -> Formation.LINE;
            case "circle" -> Formation.CIRCLE;
            default -> null;
        };
        if(formation == null || (formation == Formation.NONE && size != null)) {
            player.sendMessage(Localization.CMD_NPCS_TPHERE_USAGE.format());
            return;
        }

        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage(Localization.CMD_NPCS_NONE_SELECTED.format());
            return;
        }

        // The size is the radius for --scatter and --circle, and the space between NPCs for --line.
        double amount = switch(formation) {
            case SCATTER -> (size != null) ? size : DEFAULT_SCATTER_RADIUS;
            case LINE -> (size != null) ? size : DEFAULT_LINE_SPACING;
            // Big enough by default to leave about 1.5 blocks between neighbours.
            case CIRCLE -> (size != null) ? size : Math.max(2, Math.ceil(selected.size() * 1.5 / (2 * Math.PI)));
            case NONE -> 0;
        };
        int max = (formation == Formation.LINE) ? MAX_LINE_SPACING : MAX_SCATTER_RADIUS;
        if(formation != Formation.NONE && (amount < 1 || amount > max)) {
            player.sendMessage((formation == Formation.LINE ? Localization.CMD_NPCS_TPHERE_SPACING_INVALID : Localization.CMD_NPCS_TPHERE_RADIUS_INVALID).format(max));
            return;
        }

        Location center = player.getLocation();
        // The player's horizontal facing, and the direction to their right.
        Location flat = center.clone();
        flat.setPitch(0);
        Vector forward = flat.getDirection();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        ArrayList<Location> taken = new ArrayList<>();
        ArrayList<String> npcsList = new ArrayList<>();
        for(int i = 0; i < selected.size(); i++) {
            NPC npc = selected.get(i);
            // Use the NPC's real size (it varies with its scale), plus a small margin.
            Entity entity = npc.isSpawned() ? npc.getEntity() : null;
            double width = ((entity != null) ? entity.getWidth() : 0.6) + 0.1;
            double height = ((entity != null) ? entity.getHeight() : 1.8) + 0.1;

            Location destination = switch(formation) {
                case NONE -> center;
                case SCATTER -> CitizensUtil.scatterLocation(center, amount, taken, width, height);
                case LINE -> {
                    // Side by side, 3 blocks in front of the player, centered on where they look.
                    double offset = (i - (selected.size() - 1) / 2.0) * amount;
                    Location target = center.clone().add(forward.clone().multiply(LINE_DISTANCE)).add(right.clone().multiply(offset));
                    yield CitizensUtil.formationLocation(target, center, taken, width, height);
                }
                case CIRCLE -> {
                    // Evenly around the player, starting in front of them.
                    double angle = 2 * Math.PI * i / selected.size();
                    Vector direction = forward.clone().multiply(Math.cos(angle)).add(right.clone().multiply(Math.sin(angle)));
                    Location target = center.clone().add(direction.multiply(amount));
                    yield CitizensUtil.formationLocation(target, center, taken, width, height);
                }
            };
            taken.add(destination);
            npcsList.add(npc.getName());
            npc.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        String names = Localization.nameList(npcsList);
        String sizeText = (amount == Math.floor(amount)) ? String.valueOf((int) amount) : String.valueOf(amount);
        switch(formation) {
            case NONE -> player.sendMessage(Localization.CMD_NPCS_TPHERE_DONE.format(names, player.getName()));
            case SCATTER -> player.sendMessage(Localization.CMD_NPCS_TPHERE_SCATTERED.format(names, player.getName(), sizeText));
            case LINE -> player.sendMessage(Localization.CMD_NPCS_TPHERE_LINE.format(names, player.getName(), sizeText));
            case CIRCLE -> player.sendMessage(Localization.CMD_NPCS_TPHERE_CIRCLE.format(names, player.getName(), sizeText));
        }
    }

    private enum Formation { NONE, SCATTER, LINE, CIRCLE }

    public static void bring(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if(npc == null) {
            player.sendMessage(Localization.CMD_NPCS_NOT_FOUND.format(id));
            return;
        }

        npc.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        player.sendMessage(Localization.CMD_NPCS_BROUGHT.format(npc.getName(), npc.getId(), player.getName()));
    }

    public static void goTo(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        Location location = (npc == null) ? null : (npc.isSpawned() ? npc.getEntity().getLocation() : npc.getStoredLocation());
        if(location == null || location.getWorld() == null) {
            player.sendMessage(Localization.CMD_NPCS_NOT_FOUND.format(id));
            return;
        }

        player.teleport(location, PlayerTeleportEvent.TeleportCause.COMMAND);
        player.sendMessage(Localization.CMD_NPCS_WENT_TO.format(npc.getName(), npc.getId()));
    }

    public static void list(CommandSender sender, Object[] objects) {
        showList((Player) sender);
    }

    // One line per selected NPC, each with a button that removes it from the selection.
    public static void showList(Player player) {
        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage(Localization.CMD_NPCS_NONE_SELECTED.format());
            return;
        }

        TextComponent header = text(Localization.CMD_NPCS_LIST_HEADER.format(selected.size()));
        header.addExtra(button(Localization.CMD_NPCS_LIST_CLEAR.format(), "/npcs clear", Localization.CMD_NPCS_LIST_CLEAR_HOVER.format()));
        player.spigot().sendMessage(header);

        for(NPC npc : selected) {
            TextComponent line = text(Localization.CMD_NPCS_LIST_ENTRY.format(npc.getName(), npc.getId()));
            line.addExtra(button(Localization.CMD_NPCS_LIST_TP_TO.format(), "/npcs goto " + npc.getId(), Localization.CMD_NPCS_LIST_TP_TO_HOVER.format(npc.getName())));
            line.addExtra(text(" "));
            line.addExtra(button(Localization.CMD_NPCS_LIST_TP_HERE.format(), "/npcs bring " + npc.getId(), Localization.CMD_NPCS_LIST_TP_HERE_HOVER.format(npc.getName())));
            line.addExtra(text(" "));
            line.addExtra(button(Localization.CMD_NPCS_LIST_REMOVE.format(), "/npcs deselect " + npc.getId(), Localization.CMD_NPCS_LIST_REMOVE_HOVER.format(npc.getName())));
            player.spigot().sendMessage(line);
        }
    }

    public static void deselect(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = NpcSelection.get(player).stream().filter(n -> n.getId() == id).findFirst().orElse(null);
        if(npc == null) {
            player.sendMessage(Localization.CMD_NPCS_NOT_SELECTED.format(id));
            return;
        }

        NpcSelection.deselect(player, npc);
        player.sendMessage(Localization.NPC_DESELECTED.format(npc.getName(), npc.getId()));
        // Show the list again so the next NPC can be removed with one click.
        showList(player);
    }

    public static void clear(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        NpcSelection.clear(player);
        player.sendMessage(Localization.CMD_NPCS_CLEARED.format());
    }

    private static TextComponent text(String legacyText) {
        return new TextComponent(TextComponent.fromLegacyText(legacyText));
    }

    private static TextComponent button(String label, String command, String hover) {
        TextComponent button = text(label);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(hover))));
        return button;
    }

    public static void hideNames(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        String arg = (objects.length > 0 && objects[0] != null) ? (String) objects[0] : "";
        // Accept both "--hover" and "hover", in any case.
        String newState = (arg.startsWith("--") ? arg.substring(2) : arg).toLowerCase();

        if(!newState.equals("false") && !newState.equals("true") && !newState.equals("hover")) {
            player.sendMessage(Localization.CMD_NPCS_NAMES_USAGE.format());
            return;
        }

        for(NPC npc : CitizensAPI.getNPCRegistry().sorted()) {
            // Citizens may store this value as a Boolean (e.g. when loaded from its saves), so compare it as text.
            Object old = npc.data().get(NPC.Metadata.NAMEPLATE_VISIBLE, true);
            if (!String.valueOf(old).equalsIgnoreCase(newState)) {
                npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, newState);
                npc.scheduleUpdate(NPC.NPCUpdate.PACKET);
            }
        }

        switch(newState) {
            case "hover":
                player.sendMessage(Localization.CMD_NPCS_NAMES_HOVER.format());
                break;
            case "true":
                player.sendMessage(Localization.CMD_NPCS_NAMES_TRUE.format());
                break;
            case "false":
                player.sendMessage(Localization.CMD_NPCS_NAMES_FALSE.format());
                break;
        }
    }
}

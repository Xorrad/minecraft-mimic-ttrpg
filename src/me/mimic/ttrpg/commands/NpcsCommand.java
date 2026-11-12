package me.mimic.ttrpg.commands;

import me.mimic.lib.commands.CommandGroup;
import me.mimic.lib.commands.CommandParameter;
import me.mimic.ttrpg.core.NpcSelection;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NpcsCommand extends CommandGroup {

    private static final int DEFAULT_SCATTER_RADIUS = 3;
    private static final int MAX_SCATTER_RADIUS = 32;

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
                .description("Teleport the selected NPCs to you, optionally scattered around you")
                .playerOnly(true)
                .param(CommandParameter.of("--scatter", CommandParameter.Type.STRING).optional())
                .param(new CommandParameter("radius", CommandParameter.Type.INT, DEFAULT_SCATTER_RADIUS))
                .complete((sender, args) -> {
                    if(args.length == 1) return List.of("--scatter");
                    if(args.length == 2) return List.of("3", "5", "10");
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
            player.sendMessage("§cThe radius must be between 1 and " + SelectionTools.MAX_BRUSH_RADIUS + " blocks.");
            return;
        }
        player.getInventory().addItem(SelectionTools.createBrush(radius));
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
    }

    public static void follow(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;

        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage("§aNo NPCs are selected.");
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
            player.sendMessage("§e" + String.join("§a, §e", names) + "§a are no longer following anyone.");
        else
            player.sendMessage("§e" + String.join("§a, §e", names) + "§a are now following §e" + player.getName() + "§a.");
    }

    public static void tphere(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        String option = (String) objects[0];
        int radius = (int) objects[1];

        boolean scatter = (option != null);
        if(scatter && !option.equalsIgnoreCase("--scatter") && !option.equalsIgnoreCase("scatter")) {
            player.sendMessage("§cInvalid syntax! Usage: /npcs tphere [--scatter] [radius]");
            return;
        }
        if(scatter && (radius < 1 || radius > MAX_SCATTER_RADIUS)) {
            player.sendMessage("§cThe radius must be between 1 and " + MAX_SCATTER_RADIUS + " blocks.");
            return;
        }

        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage("§aNo NPCs are selected.");
            return;
        }

        Location center = player.getLocation();
        ArrayList<Location> taken = new ArrayList<>();
        ArrayList<String> npcsList = new ArrayList<>();
        for(NPC npc : selected) {
            // Use the NPC's real size (it varies with its scale), plus a small margin.
            Entity entity = npc.isSpawned() ? npc.getEntity() : null;
            double width = ((entity != null) ? entity.getWidth() : 0.6) + 0.1;
            double height = ((entity != null) ? entity.getHeight() : 1.8) + 0.1;
            Location destination = scatter ? CitizensUtil.scatterLocation(center, radius, taken, width, height) : center;
            taken.add(destination);
            npcsList.add(npc.getName());
            npc.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }

        if(scatter)
            player.sendMessage("§e" + String.join("§a, §e", npcsList) + "§a have been scattered within §e" + radius + "§a blocks of §e" + player.getName() + "§a.");
        else
            player.sendMessage("§e" + String.join("§a, §e", npcsList) + "§a have been teleported to §e" + player.getName() + "§a.");
    }

    public static void bring(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if(npc == null) {
            player.sendMessage("§cNo NPC with the ID " + id + " could be found.");
            return;
        }

        npc.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        player.sendMessage("§e" + npc.getName() + " §a(ID " + npc.getId() + ") has been teleported to §e" + player.getName() + "§a.");
    }

    public static void goTo(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        Location location = (npc == null) ? null : (npc.isSpawned() ? npc.getEntity().getLocation() : npc.getStoredLocation());
        if(location == null || location.getWorld() == null) {
            player.sendMessage("§cNo NPC with the ID " + id + " could be found.");
            return;
        }

        player.teleport(location, PlayerTeleportEvent.TeleportCause.COMMAND);
        player.sendMessage("§aTeleported to §e" + npc.getName() + " §a(ID " + npc.getId() + ").");
    }

    public static void list(CommandSender sender, Object[] objects) {
        showList((Player) sender);
    }

    // One line per selected NPC, each with a button that removes it from the selection.
    public static void showList(Player player) {
        List<NPC> selected = NpcSelection.get(player);
        if(selected.isEmpty()) {
            player.sendMessage("§aNo NPCs are selected.");
            return;
        }

        TextComponent header = text("§aSelected NPCs §7(" + selected.size() + ") ");
        header.addExtra(button("§c[Clear all]", "/npcs clear", "§7Remove every NPC from the selection"));
        player.spigot().sendMessage(header);

        for(NPC npc : selected) {
            TextComponent line = text("§7- §e" + npc.getName() + " §7(ID " + npc.getId() + ") ");
            line.addExtra(button("§b[TP To]", "/npcs goto " + npc.getId(), "§7Teleport to §e" + npc.getName()));
            line.addExtra(text(" "));
            line.addExtra(button("§b[TP Here]", "/npcs bring " + npc.getId(), "§7Teleport §e" + npc.getName() + "§7 to you"));
            line.addExtra(text(" "));
            line.addExtra(button("§c[Remove]", "/npcs deselect " + npc.getId(), "§7Remove §e" + npc.getName() + "§7 from the selection"));
            player.spigot().sendMessage(line);
        }
    }

    public static void deselect(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        int id = (int) objects[0];

        NPC npc = NpcSelection.get(player).stream().filter(n -> n.getId() == id).findFirst().orElse(null);
        if(npc == null) {
            player.sendMessage("§cNo selected NPC has the ID " + id + ".");
            return;
        }

        NpcSelection.deselect(player, npc);
        player.sendMessage("§a(§c-§a) §e" + npc.getName() + " §a(ID " + npc.getId() + ").");
        // Show the list again so the next NPC can be removed with one click.
        showList(player);
    }

    public static void clear(CommandSender sender, Object[] objects) {
        Player player = (Player) sender;
        NpcSelection.clear(player);
        player.sendMessage("§aNPCs are no longer being selected.");
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
            player.sendMessage("§cInvalid syntax! Usage: /npcs names --false,--true,--hover");
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
                player.sendMessage("§aNPCs names are only displayed on hover.");
                break;
            case "true":
                player.sendMessage("§aNPCs names are always displayed.");
                break;
            case "false":
                player.sendMessage("§aNPCs names are no longer displayed.");
                break;
        }
    }
}

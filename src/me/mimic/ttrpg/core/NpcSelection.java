package me.mimic.ttrpg.core;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.highlight.NpcHighlighter;
import me.mimic.ttrpg.core.highlight.PotionHighlighter;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// The NPCs each player has selected with the wand or the brush.
// The highlighter shows each player their selection: only to them with ProtocolLib,
// or to everyone with a Glowing effect without it.
public final class NpcSelection {

    private static NpcHighlighter highlighter = new PotionHighlighter();

    private NpcSelection() {}

    public static void setHighlighter(NpcHighlighter highlighter) {
        NpcSelection.highlighter = highlighter;
    }

    private static Map<Player, ArrayList<NPC>> selections() {
        return TTRPG.getInstance().selectedNPCS;
    }

    public static List<NPC> get(Player player) {
        ArrayList<NPC> selected = selections().get(player);
        return (selected == null) ? List.of() : List.copyOf(selected);
    }

    public static boolean isSelected(Player player, NPC npc) {
        ArrayList<NPC> selected = selections().get(player);
        return selected != null && selected.contains(npc);
    }

    public static boolean isSelectedByAnyone(NPC npc) {
        return selections().values().stream().anyMatch(selected -> selected.contains(npc));
    }

    // Returns true if the NPC was not selected before.
    public static boolean select(Player player, NPC npc) {
        ArrayList<NPC> selected = selections().computeIfAbsent(player, p -> new ArrayList<>());
        if(selected.contains(npc))
            return false;
        selected.add(npc);
        highlighter.selected(player, npc);
        return true;
    }

    // Returns true if the NPC was selected before.
    public static boolean deselect(Player player, NPC npc) {
        ArrayList<NPC> selected = selections().get(player);
        if(selected == null || !selected.remove(npc))
            return false;
        if(selected.isEmpty())
            selections().remove(player);
        highlighter.deselected(player, npc);
        return true;
    }

    public static void clear(Player player) {
        ArrayList<NPC> selected = selections().remove(player);
        if(selected == null)
            return;
        for(NPC npc : selected)
            highlighter.deselected(player, npc);
    }

    // Clears every selection and removes every highlight, when the plugin shuts down.
    public static void clearAll() {
        for(Player player : new ArrayList<>(selections().keySet()))
            clear(player);
        highlighter.shutdown();
    }

    // Drops an NPC from every selection, e.g. when it is deleted.
    public static void forget(NPC npc) {
        for(Player player : new ArrayList<>(selections().keySet()))
            deselect(player, npc);
    }

    // Puts the highlight back on an NPC that was respawned while selected.
    public static void refresh(NPC npc) {
        highlighter.respawned(npc);
    }

    public static int selectAll(Player player, Collection<NPC> npcs) {
        int count = 0;
        for(NPC npc : npcs) {
            if(select(player, npc))
                count++;
        }
        return count;
    }

    public static int deselectAll(Player player, Collection<NPC> npcs) {
        int count = 0;
        for(NPC npc : npcs) {
            if(deselect(player, npc))
                count++;
        }
        return count;
    }
}

package me.mimic.ttrpg.core.highlight;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

// Shows players which NPCs they have selected. NpcSelection calls it after each change to a selection.
public interface NpcHighlighter {

    // The player has just selected the NPC.
    void selected(Player player, NPC npc);

    // The player has just deselected the NPC.
    void deselected(Player player, NPC npc);

    // The NPC has just spawned again (e.g. its chunk was loaded), while possibly selected.
    void respawned(NPC npc);

    // The plugin is shutting down: remove every highlight.
    default void shutdown() {}
}

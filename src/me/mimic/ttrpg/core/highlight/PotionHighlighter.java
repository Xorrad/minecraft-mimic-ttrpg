package me.mimic.ttrpg.core.highlight;

import me.mimic.ttrpg.core.NpcSelection;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

// Used when ProtocolLib isn't installed: selected NPCs get the Glowing effect, which every player sees.
// An NPC keeps glowing as long as at least one player has it selected.
public class PotionHighlighter implements NpcHighlighter {

    @Override
    public void selected(Player player, NPC npc) {
        glow(npc);
    }

    @Override
    public void deselected(Player player, NPC npc) {
        if(!NpcSelection.isSelectedByAnyone(npc))
            unglow(npc);
    }

    @Override
    public void respawned(NPC npc) {
        if(NpcSelection.isSelectedByAnyone(npc))
            glow(npc);
    }

    private static void glow(NPC npc) {
        if(npc.isSpawned() && npc.getEntity() instanceof LivingEntity entity)
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private static void unglow(NPC npc) {
        if(npc.isSpawned() && npc.getEntity() instanceof LivingEntity entity)
            entity.removePotionEffect(PotionEffectType.GLOWING);
    }
}

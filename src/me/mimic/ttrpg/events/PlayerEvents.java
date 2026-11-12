package me.mimic.ttrpg.events;

import me.mimic.ttrpg.core.NpcSelection;
import me.mimic.ttrpg.menus.CharacterMenu;
import me.mimic.ttrpg.util.SelectionTools;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class PlayerEvents implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        NpcSelection.clear(player);
        CharacterMenu.cancelFamilySelection(player);
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        if(!event.getHand().equals(EquipmentSlot.HAND))
            return;

        Player player = event.getPlayer();
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getRightClicked());

        ItemStack hand = player.getEquipment().getItem(EquipmentSlot.HAND);
        if (hand.getType().equals(Material.STICK) || SelectionTools.isBrush(hand))
            return;

        if (npc == null || !npc.getTraitNullable(MobType.class).getType().equals(EntityType.PLAYER))
            return;

        event.setCancelled(true);
        new CharacterMenu(npc).open(player);
    }

}

package me.mimic.ttrpg.events;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.NpcSelection;
import me.mimic.ttrpg.localization.Localization;
import me.mimic.ttrpg.util.SelectionTools;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NpcsEvents implements Listener {

    // The players who used the brush during the current tick. One click can fire more than one event
    // (e.g. clicking an NPC can also count as a click in the air), so only the first one per tick is used.
    private final Set<UUID> usedBrushThisTick = new HashSet<>();

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event)
    {
        if(!event.getHand().equals(EquipmentSlot.HAND))
            return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        int radius = SelectionTools.brushRadius(item);
        if(radius > 0) {
            event.setCancelled(true);
            this.useBrush(player, event.getRightClicked().getLocation(), radius, !player.isSneaking());
            return;
        }

        if(!SelectionTools.isWand(item))
            return;

        NPC npc = getNPC(event.getRightClicked());
        if (npc == null)
            return;

        if(NpcSelection.deselect(player, npc))
            player.sendMessage(Localization.NPC_DESELECTED.format(npc.getName(), npc.getId()));
        else if(NpcSelection.select(player, npc))
            player.sendMessage(Localization.NPC_SELECTED.format(npc.getName(), npc.getId()));

        event.setCancelled(true);
    }

    @EventHandler
    public void onBrushClick(PlayerInteractEvent event) {
        if(event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL)
            return;

        int radius = SelectionTools.brushRadius(event.getItem());
        if(radius <= 0)
            return;

        // Don't break or brush the block that was clicked.
        event.setCancelled(true);

        // Right-click selects, sneak + right-click deselects; left-click does nothing.
        if(event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        boolean select = !player.isSneaking();
        Location center = this.aimedLocation(player);
        if(center == null) {
            if(this.firstUseThisTick(player))
                player.sendMessage(Localization.BRUSH_NO_TARGET.format(SelectionTools.BRUSH_RANGE));
            return;
        }
        this.useBrush(player, center, radius, select);
    }

    @EventHandler
    public void onBrushHit(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player player))
            return;

        // Hitting with the brush does nothing, and doesn't hurt NPCs.
        if(SelectionTools.isBrush(player.getInventory().getItemInMainHand()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onNPCSpawn(NPCSpawnEvent event) {
        // The glow is lost when an NPC despawns (e.g. its chunk unloads), so put it back on the next tick.
        NPC npc = event.getNPC();
        Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> NpcSelection.refresh(npc));
    }

    @EventHandler
    public void onNPCRemove(NPCRemoveEvent event) {
        NpcSelection.forget(event.getNPC());
    }

    private void useBrush(Player player, Location center, int radius, boolean select) {
        if(!this.firstUseThisTick(player))
            return;

        List<NPC> npcs = this.npcsAround(center, radius);
        int changed = select ? NpcSelection.selectAll(player, npcs) : NpcSelection.deselectAll(player, npcs);
        int total = NpcSelection.get(player).size();
        this.showRadius(player, center, radius, select);

        if(select)
            player.sendMessage(Localization.BRUSH_SELECTED.format(changed, radius, total));
        else
            player.sendMessage(Localization.BRUSH_DESELECTED.format(changed, radius, total));
    }

    private boolean firstUseThisTick(Player player) {
        UUID uuid = player.getUniqueId();
        if(!this.usedBrushThisTick.add(uuid))
            return false;
        Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> this.usedBrushThisTick.remove(uuid));
        return true;
    }

    // Where the player is aiming: the first NPC or block in range.
    private Location aimedLocation(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), SelectionTools.BRUSH_RANGE,
                FluidCollisionMode.NEVER, true, 0.2, entity -> !entity.equals(player) && getNPC(entity) != null);
        if(result == null)
            return null;
        return result.getHitPosition().toLocation(player.getWorld());
    }

    private List<NPC> npcsAround(Location center, int radius) {
        double radiusSquared = (double) radius * radius;
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(entity -> entity.getLocation().distanceSquared(center) <= radiusSquared)
                .map(NpcsEvents::getNPC)
                .filter(npc -> npc != null)
                .toList();
    }

    // Draws the brush's circle around the center, only for the player using it.
    private void showRadius(Player player, Location center, int radius, boolean select) {
        Particle.DustOptions dust = new Particle.DustOptions(select ? Color.LIME : Color.RED, 1.0F);
        World world = center.getWorld();
        int points = Math.min(256, Math.max(16, radius * 8));
        for(int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = new Location(world,
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + 0.2,
                    center.getZ() + radius * Math.sin(angle));
            player.spawnParticle(Particle.DUST, point, 1, dust);
        }
    }

    // The NPC of an entity, ignoring helper NPCs Citizens creates on its own (such as the seats of sitting NPCs).
    private static NPC getNPC(Entity entity) {
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
        if(npc == null || npc.getOwningRegistry() != CitizensAPI.getNPCRegistry())
            return null;
        return npc;
    }
}

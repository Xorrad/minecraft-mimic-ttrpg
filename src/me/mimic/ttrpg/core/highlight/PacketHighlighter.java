package me.mimic.ttrpg.core.highlight;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import me.mimic.ttrpg.core.NpcSelection;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

// Used when ProtocolLib is installed: each player sees only the NPCs they selected glow.
//
// The glow is a flag (0x40) in byte 0 of an entity's metadata. Whenever the server sends a player
// the metadata of an NPC that player selected, the flag is added to that player's copy of the packet,
// so the glow survives the server updating the NPC (sneaking, burning...). When a selection changes,
// the player is sent the NPC's current flags with or without the glow.
public class PacketHighlighter implements NpcHighlighter {

    private static final int FLAGS_INDEX = 0;
    private static final byte GLOWING_FLAG = 0x40;

    private final Plugin plugin;
    private final ProtocolManager protocol;
    // The entity IDs of the NPCs each player has selected. Packets may be handled off the main thread,
    // so this is only ever replaced by thread-safe collections.
    private final Map<UUID, Set<Integer>> glowingFor = new ConcurrentHashMap<>();
    private boolean warned = false;

    public PacketHighlighter(Plugin plugin) {
        this.plugin = plugin;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                addGlow(event);
            }
        });
    }

    @Override
    public void selected(Player player, NPC npc) {
        this.rebuild(player);
        this.sendFlags(player, npc, true);
    }

    @Override
    public void deselected(Player player, NPC npc) {
        this.rebuild(player);
        this.sendFlags(player, npc, false);
    }

    @Override
    public void respawned(NPC npc) {
        // A respawned NPC has a new entity ID, so the players who selected it need their sets rebuilt.
        for(Player player : Bukkit.getOnlinePlayers()) {
            if(NpcSelection.isSelected(player, npc)) {
                this.rebuild(player);
                this.sendFlags(player, npc, true);
            }
        }
    }

    @Override
    public void shutdown() {
        for(UUID uuid : new ArrayList<>(this.glowingFor.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if(player == null)
                continue;
            for(NPC npc : NpcSelection.get(player))
                this.sendFlags(player, npc, false);
        }
        this.glowingFor.clear();
        this.protocol.removePacketListeners(this.plugin);
    }

    private void rebuild(Player player) {
        Set<Integer> ids = NpcSelection.get(player).stream()
                .filter(NPC::isSpawned)
                .map(npc -> npc.getEntity().getEntityId())
                .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet));
        if(ids.isEmpty())
            this.glowingFor.remove(player.getUniqueId());
        else
            this.glowingFor.put(player.getUniqueId(), ids);
    }

    // Sends the player the NPC's current flags, with or without the glow.
    private void sendFlags(Player player, NPC npc, boolean glow) {
        if(!npc.isSpawned() || !player.isOnline())
            return;
        Entity entity = npc.getEntity();
        byte flags = currentFlags(entity);
        if(glow)
            flags |= GLOWING_FLAG;

        try {
            PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entity.getEntityId());
            packet.getDataValueCollectionModifier().write(0, List.of(
                    new WrappedDataValue(FLAGS_INDEX, WrappedDataWatcher.Registry.get((Type) Byte.class), flags)));
            // Not filtered: these flags are already final, the listener must not change them.
            this.protocol.sendServerPacket(player, packet, false);
        }
        catch(RuntimeException e) {
            if(!this.warned) {
                this.warned = true;
                this.plugin.getLogger().log(Level.WARNING, "Could not update the glow of selected NPCs", e);
            }
        }
    }

    private void addGlow(PacketEvent event) {
        if(event.isPlayerTemporary())
            return;
        Set<Integer> ids = this.glowingFor.get(event.getPlayer().getUniqueId());
        if(ids == null)
            return;

        PacketContainer packet = event.getPacket();
        if(!ids.contains(packet.getIntegers().read(0)))
            return;

        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
        if(values == null)
            return;

        boolean changed = false;
        List<WrappedDataValue> withGlow = new ArrayList<>(values.size());
        for(WrappedDataValue value : values) {
            if(value.getIndex() == FLAGS_INDEX && value.getValue() instanceof Byte flags) {
                withGlow.add(new WrappedDataValue(FLAGS_INDEX, value.getSerializer(), (byte) (flags | GLOWING_FLAG)));
                changed = true;
            }
            else {
                withGlow.add(value);
            }
        }
        if(!changed)
            return;

        // The same packet may go to other players, so only this player's copy is changed.
        PacketContainer copy = packet.shallowClone();
        copy.getDataValueCollectionModifier().write(0, withGlow);
        event.setPacket(copy);
    }

    // The entity's flags byte as the server would send it, from the Bukkit API.
    private static byte currentFlags(Entity entity) {
        byte flags = 0;
        if(entity.getFireTicks() > 0) flags |= 0x01;
        if(entity instanceof Player player) {
            if(player.isSneaking()) flags |= 0x02;
            if(player.isSprinting()) flags |= 0x08;
        }
        if(entity instanceof LivingEntity living) {
            if(living.isSwimming()) flags |= 0x10;
            if(living.isInvisible()) flags |= 0x20;
            if(living.isGliding()) flags |= (byte) 0x80;
        }
        if(entity.isGlowing()) flags |= GLOWING_FLAG;
        return flags;
    }
}

package me.mimic.ttrpg.util;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.localization.Localization;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

// The items used to select NPCs: the wand (one NPC at a time) and the brush (every NPC in a radius).
public final class SelectionTools {

    public static final int DEFAULT_BRUSH_RADIUS = 5;
    public static final int MAX_BRUSH_RADIUS = 32;
    public static final int BRUSH_RANGE = 64;

    // Names wands were given before they carried a tag, so those wands keep working.
    private static final List<String> LEGACY_WAND_NAMES = List.of("§eBaguette de selection", "§eSelection wand");

    private SelectionTools() {}

    private static NamespacedKey brushKey() {
        return new NamespacedKey(TTRPG.getInstance(), "selection_brush_radius");
    }

    private static NamespacedKey wandKey() {
        return new NamespacedKey(TTRPG.getInstance(), "selection_wand");
    }

    // The wand is recognised by a hidden tag, so its name can be changed in the messages file.
    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(Localization.WAND_NAME.format());
        meta.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public static boolean isWand(ItemStack item) {
        if(item == null || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        if(meta.getPersistentDataContainer().has(wandKey(), PersistentDataType.BYTE))
            return true;
        return meta.hasDisplayName() && LEGACY_WAND_NAMES.stream().anyMatch(name -> name.equalsIgnoreCase(meta.getDisplayName()));
    }

    public static ItemStack createBrush(int radius) {
        ItemStack brush = new ItemStack(Material.BRUSH);
        ItemMeta meta = brush.getItemMeta();
        meta.setDisplayName(Localization.BRUSH_NAME.format(radius));
        meta.setLore(List.of(Localization.BRUSH_LORE.format(radius).split("\n")));
        meta.getPersistentDataContainer().set(brushKey(), PersistentDataType.INTEGER, radius);
        brush.setItemMeta(meta);
        return brush;
    }

    public static boolean isBrush(ItemStack item) {
        return brushRadius(item) > 0;
    }

    // The radius stored on a brush, or 0 if the item is not a brush.
    public static int brushRadius(ItemStack item) {
        if(item == null || !item.hasItemMeta())
            return 0;
        Integer radius = item.getItemMeta().getPersistentDataContainer().get(brushKey(), PersistentDataType.INTEGER);
        return (radius == null) ? 0 : radius;
    }
}

package me.mimic.ttrpg.util;

import me.mimic.ttrpg.TTRPG;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

// The items used to select NPCs: the wand (one NPC at a time) and the brush (every NPC in a radius).
public final class SelectionTools {

    public static final String WAND_NAME = "§eSelection wand";
    public static final int DEFAULT_BRUSH_RADIUS = 5;
    public static final int MAX_BRUSH_RADIUS = 32;
    public static final int BRUSH_RANGE = 64;

    private SelectionTools() {}

    private static NamespacedKey brushKey() {
        return new NamespacedKey(TTRPG.getInstance(), "selection_brush_radius");
    }

    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        wand.setItemMeta(meta);
        return wand;
    }

    public static boolean isWand(ItemStack item) {
        if(item == null || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equalsIgnoreCase(WAND_NAME);
    }

    public static ItemStack createBrush(int radius) {
        ItemStack brush = new ItemStack(Material.BRUSH);
        ItemMeta meta = brush.getItemMeta();
        meta.setDisplayName("§eSelection brush §7(" + radius + " blocks)");
        meta.setLore(List.of(
                "§7Right-click to §aselect§7 the NPCs",
                "§7within §f" + radius + "§7 blocks of where you aim.",
                "§7Sneak + right-click to §cdeselect§7 them."));
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

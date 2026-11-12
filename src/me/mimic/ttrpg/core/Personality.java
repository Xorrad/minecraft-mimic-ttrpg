package me.mimic.ttrpg.core;

import me.mimic.ttrpg.localization.Localization;
import org.bukkit.Material;

// Each personality's name and description come from the messages file,
// under its own name (e.g. LOYAL) and its name followed by _DESC (e.g. LOYAL_DESC).
public enum Personality {

    LOYAL(Material.APPLE),
    DISLOYAL(Material.POISONOUS_POTATO),

    GREEDY(Material.GOLD_NUGGET),
    GENEROUS(Material.FLOWER_POT),

    DILIGENT(Material.IRON_PICKAXE),
    LAZY(Material.BROWN_BED),

    CHASTE(Material.WHITE_CANDLE),
    LUSTFUL(Material.REDSTONE),
    ;

    private final Material icon;

    Personality(Material icon) {
        this.icon = icon;
    }

    public String getName() {
        return Localization.valueOf(this.name()).format();
    }

    public String getDescription() {
        return Localization.valueOf(this.name() + "_DESC").format();
    }

    public Material getIcon() {
        return icon;
    }
}

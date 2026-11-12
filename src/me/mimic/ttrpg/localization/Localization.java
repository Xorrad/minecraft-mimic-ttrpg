package me.mimic.ttrpg.localization;

import me.mimic.ttrpg.TTRPG;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.ChatColor;

public enum Localization {
    MENU_TITLE_CHARACTER,
    MENU_TITLE_CHANGE_CULTURE,
    MENU_TITLE_CHANGE_FAITH,
    MENU_TITLE_CHANGE_PERSONALITY,
    MENU_TITLE_RENAME,

    RENAME_DESC,
    RENAME_INVALID,
    RENAMED,

    CULTURE_SELECT,
    FAITH_SELECT,

    CMD_CULTURE_EXISTS,
    CMD_CULTURE_DOESNT_EXIST,
    CMD_CULTURE_CREATED,
    CMD_CULTURE_DELETED,
    CMD_CULTURE_NO_MANUAL_NAMES,
    CMD_CULTURE_NAME_ADDED,
    CMD_CULTURE_NAME_REMOVED,
    CMD_CULTURE_NAME_DOESNT_EXIST,
    CMD_CULTURE_SHOW,
    CMD_CULTURE_INFO,
    CMD_CULTURE_INFO_NAME,
    CMD_CULTURE_INFO_NAMEMANAGER,
    CMD_CULTURE_NAMETYPE_INVALID,
    CMD_CULTURE_NAMEMANAGER_INVALID,
    CMD_CULTURE_NAMEMANAGER_CHANGED,
    CMD_CULTURE_NAMEMANAGER_TEMPLATE_UNSUPPORTED,
    CMD_CULTURE_NAMEMANAGER_TEMPLATE_CHANGED,
    CMD_CULTURE_LIST,

    CMD_FAITH_CREATED,
    CMD_FAITH_DELETED,
    CMD_FAITH_EXISTS,
    CMD_FAITH_DOESNT_EXIST,
    CMD_FAITH_LIST,

    NAME,
    CULTURE,
    FAITH,
    ERROR,
    SEPARATOR,
    NONE,

    DECREASE_BY_ONE,
    INCREASE_BY_ONE,

    STAT_STRENGTH,
    STAT_DEXTERITY,
    STAT_CONSTITUTION,
    STAT_INTELLIGENCE,
    STAT_CHARISMA,

    FAMILY_FATHER,
    FAMILY_MOTHER,
    FAMILY_PARTNER,
    FAMILY_CHILD,
    FAMILY_CHANGED,
    FAMILY_SELECT,

    MOUNT,
    MOUNT_DESC,
    INVENTORY,
    INVENTORY_DESC,
    EQUIP,
    EQUIP_DESC,
    SIT,
    SIT_DESC,
    FOLLOW,
    FOLLOW_DESC,
    SELECT,
    SELECT_DESC,

    LOYAL,
    LOYAL_DESC,
    DISLOYAL,
    DISLOYAL_DESC,
    GREEDY,
    GREEDY_DESC,
    GENEROUS,
    GENEROUS_DESC,
    DILIGENT,
    DILIGENT_DESC,
    LAZY,
    LAZY_DESC,
    CHASTE,
    CHASTE_DESC,
    LUSTFUL,
    LUSTFUL_DESC,

    ;

    private String format;

    public String format(Object... args) {
        if(this.format == null)
            return this.name();
        return String.format(ChatColor.translateAlternateColorCodes('&', this.format), args);
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    // The English messages bundled in the plugin jar.
    private static final String MESSAGES_PATH = "lang/en_US.yml";

    public static void load() {
        InputStream stream = TTRPG.getInstance().getResource(MESSAGES_PATH);
        YamlConfiguration messages = (stream == null)
                ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for(Localization msg : values()) {
            msg.setFormat(messages.getString(msg.name()));
        }
    }

    @Override
    public String toString() {
        return this.format();
    }
}

package me.mimic.ttrpg.localization;

import me.mimic.ttrpg.TTRPG;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.util.Collection;
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
    CMD_CULTURE_INFO_SKIN,
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

    CMD_ROLL_RESULT,
    CMD_ROLL_INVALID,
    CMD_ROLL_TOO_LARGE,

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

    PERSONALITY_REMOVE,
    PERSONALITY_ADD,

    MOUNT_FAILED,
    INVENTORY_FAILED,
    EQUIP_FAILED,
    SIT_FAILED,
    FOLLOW_FAILED,
    FAMILY_SELF,

    GENDER,
    GENDER_DESC,
    GENDER_MALE,
    GENDER_FEMALE,

    SKIN,
    SKIN_DESC_PICK,
    SKIN_DESC_REMOVE,
    SKIN_UPLOADING,
    SKIN_APPLIED,
    SKIN_ALREADY,
    SKIN_NONE_AVAILABLE,
    SKIN_BUSY,
    SKIN_FAILED,
    SKIN_REMOVED,

    CMD_CHAR_GENDER_INVALID,
    CMD_CHAR_CREATED,
    CMD_CULTURE_LIST_ENTRY,
    CMD_CULTURE_GENERATED_NAME,
    CMD_FAITH_LIST_ENTRY,

    NAMES_INFO_TYPE,
    NAMES_INFO_DIRECTORY,
    NAMES_INFO_LIST,
    NAMES_INFO_TEMPLATE,

    NPC_NAMES_SEPARATOR,
    NPC_SELECTED,
    NPC_DESELECTED,
    CMD_NPCS_NONE_SELECTED,
    CMD_NPCS_NOT_FOUND,
    CMD_NPCS_NOT_SELECTED,
    CMD_NPCS_CLEARED,
    CMD_NPCS_FOLLOW_STARTED,
    CMD_NPCS_FOLLOW_STOPPED,
    CMD_NPCS_TPHERE_USAGE,
    CMD_NPCS_TPHERE_RADIUS_INVALID,
    CMD_NPCS_TPHERE_SPACING_INVALID,
    CMD_NPCS_TPHERE_DONE,
    CMD_NPCS_TPHERE_SCATTERED,
    CMD_NPCS_TPHERE_LINE,
    CMD_NPCS_TPHERE_CIRCLE,
    CMD_NPCS_BROUGHT,
    CMD_NPCS_WENT_TO,
    CMD_NPCS_LIST_HEADER,
    CMD_NPCS_LIST_ENTRY,
    CMD_NPCS_LIST_CLEAR,
    CMD_NPCS_LIST_CLEAR_HOVER,
    CMD_NPCS_LIST_TP_TO,
    CMD_NPCS_LIST_TP_TO_HOVER,
    CMD_NPCS_LIST_TP_HERE,
    CMD_NPCS_LIST_TP_HERE_HOVER,
    CMD_NPCS_LIST_REMOVE,
    CMD_NPCS_LIST_REMOVE_HOVER,
    CMD_NPCS_NAMES_USAGE,
    CMD_NPCS_NAMES_HOVER,
    CMD_NPCS_NAMES_TRUE,
    CMD_NPCS_NAMES_FALSE,
    CMD_NPCS_BRUSH_RADIUS_INVALID,

    WAND_NAME,
    BRUSH_NAME,
    BRUSH_LORE,
    BRUSH_NO_TARGET,
    BRUSH_SELECTED,
    BRUSH_DESELECTED,

    ;

    private String format;

    public String format(Object... args) {
        if(this.format == null)
            return this.name();
        return String.format(ChatColor.translateAlternateColorCodes('&', this.format), args);
    }

    // Joins NPC names for a message, e.g. "Anna, Bob, Carl", with NPC_NAMES_SEPARATOR between them.
    public static String nameList(Collection<String> names) {
        return String.join(NPC_NAMES_SEPARATOR.format(), names);
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

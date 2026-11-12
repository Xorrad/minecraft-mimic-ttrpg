package me.mimic.ttrpg.core;

import me.mimic.ttrpg.core.names.NameType;
import me.mimic.ttrpg.localization.Localization;
import org.bukkit.Material;

public enum Gender {
    MALE(NameType.MALE, Material.LIGHT_BLUE_DYE),
    FEMALE(NameType.FEMALE, Material.PINK_DYE);

    // The kind of first name a character of this gender gets.
    private final NameType nameType;
    private final Material icon;

    Gender(NameType nameType, Material icon) {
        this.nameType = nameType;
        this.icon = icon;
    }

    public NameType getNameType() {
        return nameType;
    }

    public Material getIcon() {
        return icon;
    }

    // The name shown to players, from GENDER_MALE or GENDER_FEMALE in the messages file.
    public String getDisplayName() {
        return Localization.valueOf("GENDER_" + this.name()).format();
    }

    // The other gender, to switch between them.
    public Gender other() {
        return (this == MALE) ? FEMALE : MALE;
    }

    // The gender with this name, ignoring case, or null if there is none.
    public static Gender fromString(String name) {
        for(Gender gender : values()) {
            if(gender.name().equalsIgnoreCase(name))
                return gender;
        }
        return null;
    }
}

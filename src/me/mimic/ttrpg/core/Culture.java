package me.mimic.ttrpg.core;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.configs.CulturesConfig;
import me.mimic.ttrpg.core.names.NamesManager;

import java.util.List;

public class Culture {
    private String id;
    private String name;
    private NamesManager namesManager;
    // The folders of skins/cultures/ this culture's skin parts come from (the "skin" property in cultures.yml).
    // Empty means the culture's own folder.
    private List<String> skinFolders;

    public Culture(String id, String name, NamesManager namesManager) {
        this(id, name, namesManager, List.of());
    }

    public Culture(String id, String name, NamesManager namesManager, List<String> skinFolders) {
        this.id = id;
        this.name = name;
        this.namesManager = namesManager;
        this.setSkinFolders(skinFolders);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NamesManager getNameManager() {
        return namesManager;
    }

    public void setNamesManager(NamesManager namesManager) {
        this.namesManager = namesManager;
    }

    // The folders of skins/cultures/ the culture's skin parts come from, in lowercase:
    // those of its "skin" property, or else its own folder (its id).
    public List<String> getSkinFolders() {
        return this.skinFolders.isEmpty() ? List.of(this.id.toLowerCase()) : this.skinFolders;
    }

    // Whether the culture's skin folders were set with the "skin" property.
    public boolean hasSkinProperty() {
        return !this.skinFolders.isEmpty();
    }

    public void setSkinFolders(List<String> skinFolders) {
        this.skinFolders = (skinFolders == null) ? List.of() : skinFolders.stream()
                .filter(folder -> folder != null && !folder.isBlank())
                .map(folder -> folder.trim().toLowerCase())
                .distinct()
                .toList();
    }

    public void save() {
        CulturesConfig config = (CulturesConfig) TTRPG.getInstance().getConfig("cultures");

        config.set(this.id + ".name", this.name);
        config.set(this.id + ".name-manager.type", this.namesManager.getType().name().toLowerCase());
        // One folder is written as a single value, several as a list; nothing when the culture uses its own.
        if(this.skinFolders.isEmpty())
            config.set(this.id + ".skin", null);
        else
            config.set(this.id + ".skin", (this.skinFolders.size() == 1) ? this.skinFolders.get(0) : this.skinFolders);

        namesManager.saveConfig(this.id);

        config.save();
    }
}

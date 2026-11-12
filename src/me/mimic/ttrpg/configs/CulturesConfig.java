package me.mimic.ttrpg.configs;

import me.mimic.lib.configs.Config;
import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.names.NamesManager;

import java.util.List;
import java.util.logging.Level;

public class CulturesConfig extends Config {

    public CulturesConfig() {
        super("cultures.yml");
    }

    @Override
    public void initDefault() {
        if(this.file.exists())
            return;
        if(TTRPG.saveDefaultResource(this.getName()))
            TTRPG.log(Level.INFO, "Created default config file (%s)", this.getName());
    }

    @Override
    public void load() {
        super.load();

        for(String id : getKeys(false)) {
            try {
                String name = getString(id + ".name");
                NamesManager.Type nameManagerType = NamesManager.Type.valueOf(getString(id + ".name-manager.type").toUpperCase());
                NamesManager namesManager = nameManagerType.getClazz().getConstructor(String.class).newInstance(id);
                TTRPG.getInstance().cultures.put(id, new Culture(id, name, namesManager, skinFolders(id)));
            }
            catch(Exception e) {
                TTRPG.log(Level.INFO, "Could not load culture %s", id);
                e.printStackTrace();
            }
        }
        TTRPG.log(Level.INFO, "Loaded %d cultures", TTRPG.getInstance().cultures.size());
    }

    // The "skin" property of a culture: the folders of skins/cultures/ its skin parts come from,
    // written as one name ("skin: medieval") or a list ("skin: [medieval, roman]"). Empty if not set.
    private List<String> skinFolders(String id) {
        String key = id + ".skin";
        if(isList(key))
            return getStringList(key);
        String folder = getString(key);
        return (folder == null || folder.isBlank()) ? List.of() : List.of(folder);
    }
}

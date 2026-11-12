package me.mimic.ttrpg.configs;

import me.mimic.lib.LibMain;
import me.mimic.lib.configs.Config;
import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.localization.Language;
import me.mimic.ttrpg.localization.Localization;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class LocalizationConfig extends Config {

    public LocalizationConfig() {
        super(TTRPG.getInstance().language.getPath());
    }

    public void setActiveLanguage(Language lang) {
        this.save();
        this.file = new File(LibMain.getInstance().getDataFolder(), lang.getPath());
        this.load();
    }

    @Override
    public void initDefault() {
        for(Language lang : Language.values()) {
            if(new File(TTRPG.getInstance().getDataFolder(), lang.getPath()).exists())
                continue;
            if(TTRPG.saveDefaultResource(lang.getPath()))
                TTRPG.log(Level.INFO, "Created default config file (%s)", lang.name());
        }
    }

    // Returns the localization bundled in the plugin jar, or an empty configuration if there is none.
    public static YamlConfiguration loadBundled(Language lang) {
        InputStream stream = TTRPG.getInstance().getResource(lang.getPath());
        if(stream == null)
            return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Override
    public void load() {
        super.load();

        // Add default to file for missing localization.
        YamlConfiguration dc = loadBundled(TTRPG.getInstance().language);
        for (Localization msg : Localization.values()) {
            if (this.contains(msg.name()) || !dc.contains(msg.name()))
                continue;
            this.set(msg.name(), dc.getString(msg.name()));
        }
        this.save();
    }
}

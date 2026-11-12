package me.mimic.ttrpg.core.names.types;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.configs.CulturesConfig;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.names.NameType;
import me.mimic.ttrpg.core.names.NamesManager;
import me.mimic.ttrpg.generators.RinkworksGenerator;
import me.mimic.ttrpg.localization.Localization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

public class RinkworksNamesManager extends NamesManager {
    private HashMap<NameType, String> templates;
    private HashMap<NameType, RinkworksGenerator> generators;

    public RinkworksNamesManager(String configPath) {
        super(Type.RINKWORKS, configPath);

        this.templates = new HashMap<>();
        this.generators = new HashMap<>();

        this.loadConfig(configPath);
        this.loadData();
    }

    public String getTemplate(NameType nameType)
    {
        return this.templates.get(nameType);
    }

    public void setTemplate(NameType nameType, String template) {
        this.templates.put(nameType, template);
        this.generators.put(nameType, new RinkworksGenerator(template));
    }

    @Override
    public String get(NameType nameType) {
        RinkworksGenerator generator = this.generators.get(nameType);
        if(generator == null) {
            TTRPG.log(Level.INFO, "Warning! A rinkworks name generator has no template for %s.", nameType.name().toLowerCase());
            return "";
        }
        return generator.toString();
    }

    @Override
    public ArrayList<String> getResume() {
        ArrayList<String> lines = new ArrayList<>();

        lines.add(Localization.NAMES_INFO_TYPE.format(this.type.name().toLowerCase()));

        for(NameType type : NameType.values())
        {
            lines.add(Localization.NAMES_INFO_TEMPLATE.format(type.name(), this.templates.get(type)));
        }

        return lines;
    }

    @Override
    public void loadConfig(String configPath) {
        CulturesConfig config = (CulturesConfig) TTRPG.getInstance().getConfig("cultures");

        for(NameType nameType : NameType.values()) {
            String key = configPath + ".name-manager.template." + nameType.name().toLowerCase();
            if(config.contains(key)) {
                this.setTemplate(nameType, config.getString(key));
            }
        }
    }

    @Override
    public void saveConfig(String configPath) {
        CulturesConfig config = (CulturesConfig) TTRPG.getInstance().getConfig("cultures");

        for(NameType nameType : NameType.values())
            config.set(configPath + ".name-manager.template." + nameType.name().toLowerCase(), this.templates.get(nameType));

        config.save();
    }

    @Override
    public void setDefaultConfig(Culture culture) {
        String template = "ss";

        for(NameType nameType : NameType.values())
            this.setTemplate(nameType, template);
    }

    @Override
    public void loadData()
    {
    }
}

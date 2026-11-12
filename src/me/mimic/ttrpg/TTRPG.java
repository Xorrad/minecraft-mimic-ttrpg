package me.mimic.ttrpg;

import me.mimic.ttrpg.commands.*;
import me.mimic.ttrpg.configs.*;
import me.mimic.ttrpg.core.traits.*;
import me.mimic.lib.LibMain;
import me.mimic.lib.configs.Config;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.Faith;
import me.mimic.ttrpg.core.NpcSelection;
import me.mimic.ttrpg.core.skins.SkinLibrary;
import me.mimic.ttrpg.core.highlight.PacketHighlighter;
import me.mimic.ttrpg.events.NpcsEvents;
import me.mimic.ttrpg.events.PlayerEvents;
import me.mimic.ttrpg.localization.Localization;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

public class TTRPG extends JavaPlugin  {
    static TTRPG instance;

    private HashMap<String, Config> configs;
    public HashMap<String, Culture> cultures;
    public HashMap<String, Faith> faiths;
    public HashMap<Player, ArrayList<NPC>> selectedNPCS;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        LibMain.init(this);
        this.initVariables();
        this.initHighlighter();
        this.registerCommands();
        this.registerEvents();
        this.initConfigurations();
        this.initDependencies();
    }

    @Override
    public void onDisable() {
        // Remove the glow of selected NPCs, which would otherwise stay after a reload.
        if(this.selectedNPCS != null)
            NpcSelection.clearAll();

    }

    private void initVariables() {
        this.cultures = new HashMap<>();
        this.faiths = new HashMap<>();
        this.selectedNPCS = new HashMap<>();
    }

    // With ProtocolLib, each player sees only their own selected NPCs glow; without it, everyone sees them glow.
    private void initHighlighter() {
        if(!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            log(Level.INFO, "ProtocolLib is not installed: selected NPCs glow for every player.");
            return;
        }
        try {
            NpcSelection.setHighlighter(new PacketHighlighter(this));
            log(Level.INFO, "Using ProtocolLib: selected NPCs only glow for the player who selected them.");
        }
        catch(Throwable e) {
            // e.g. a ProtocolLib version that doesn't match the server.
            getLogger().log(Level.WARNING, "Could not use ProtocolLib, selected NPCs will glow for every player.", e);
        }
    }

    private void initDependencies() {
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(StatsTrait.class));
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(CultureTrait.class));
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(FaithTrait.class));
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(FamilyTrait.class));
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(PersonalityTrait.class));
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(GenderTrait.class));
    }

    private void registerCommands() {
        new CultureCommand().register();
        new FaithCommand().register();
        new CharCommand().register();
        new NpcsCommand().register();
        new RollCommand().register();
    }

    private void registerEvents() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerEvents(), this);
        pm.registerEvents(new NpcsEvents(), this);
    }

    private void initConfigurations() {
        this.configs = new HashMap<String, Config>();

        Localization.load();

        this.loadConfig("cultures", new CulturesConfig());
        this.loadConfig("faiths", new FaithsConfig());

        SkinLibrary.createFolders(this.cultures.values());
    }

    public void loadConfig(String name, Config config) {
        this.configs.put(name, config);
        config.load();
    }

    public Config getConfig(String name) {
        return this.configs.get(name);
    }

    // Copies a file bundled in the plugin jar to the data folder.
    // Returns false when the jar does not contain it.
    public static boolean saveDefaultResource(String path) {
        if(instance.getResource(path) == null)
            return false;
        instance.saveResource(path, false);
        return true;
    }

    public static void log(String format, Object... args) {
        log(Level.INFO, format, args);
    }

    public static void log(Level level, String format, Object... args) {
        instance.getLogger().log(level, String.format(format, args));
    }

    public static TTRPG getInstance() {
        return instance;
    }
}

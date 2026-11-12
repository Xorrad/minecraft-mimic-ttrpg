package me.mimic.ttrpg.core.skins;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.Gender;
import me.mimic.ttrpg.core.traits.CultureTrait;
import me.mimic.ttrpg.core.traits.GenderTrait;
import me.mimic.ttrpg.localization.Localization;
import me.mimic.ttrpg.util.MathUtil;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

// The skins of NPCs, in plugins/MimicTTRPG/skins (see README.txt there):
//  - put together from skin parts, shared (parts/) or cultural (cultures/<folder>/, the culture's own folder
//    or those of its "skin" property in cultures.yml), see SkinComposer;
//  - or whole skins of each gender (male/, female/), used when no skin can be put together.
//
// Player NPCs need a skin signed by Mojang, so each image is uploaded once through Citizens (to MineSkin),
// and the signed texture is cached in skins/cache.yml by the image's hash, then reused.
public final class SkinLibrary {

    public static final String FOLDER = "skins";
    private static final String CACHE_FILE = "cache.yml";
    private static final String LAYERS_FILE = "layers.yml";
    private static final String README_FILE = "README.txt";
    private static final String PARTS_FOLDER = "parts";
    private static final String CULTURES_FOLDER = "cultures";
    private static final String GENERATED_FOLDER = "generated";

    // Used when layers.yml has no layers. Matches the parts made by tools/SkinPartsGenerator.java.
    private static final List<SkinComposer.Layer> DEFAULT_LAYERS = List.of(
            new SkinComposer.Layer("body", true, 1),
            new SkinComposer.Layer("clothes", true, 1));

    // NPCs whose skin is being created, so a second request doesn't upload again.
    private static final Set<Integer> pending = new HashSet<>();

    private SkinLibrary() {}

    public enum Result {
        // The skin was cached and is applied: onDone was already called with its description.
        APPLIED,
        // The skin is being uploaded: onDone will be called later with its description, or null if it failed.
        UPLOADING,
        ALREADY_HAS_SKIN,
        NO_SKINS,
        BUSY,
        FAILED
    }

    private static File skinsFolder() {
        return new File(TTRPG.getInstance().getDataFolder(), FOLDER);
    }

    // Creates the skins folders, with the README and layers.yml, so server owners know where to put their images.
    public static void createFolders(Collection<Culture> cultures) {
        File skins = skinsFolder();
        skins.mkdirs();
        for(String file : List.of(README_FILE, LAYERS_FILE)) {
            if(!new File(skins, file).exists())
                TTRPG.saveDefaultResource(FOLDER + "/" + file);
        }

        for(Gender gender : Gender.values())
            new File(skins, genderFolder(gender)).mkdirs();
        for(SkinComposer.Layer layer : loadLayers().layers()) {
            for(Gender gender : Gender.values())
                new File(new File(new File(skins, PARTS_FOLDER), layer.name()), genderFolder(gender)).mkdirs();
        }
        for(Culture culture : cultures)
            for(String folder : culture.getSkinFolders())
                new File(new File(skins, CULTURES_FOLDER), folder).mkdirs();
        new File(skins, GENERATED_FOLDER).mkdirs();
    }

    public static boolean hasSkin(NPC npc) {
        SkinTrait skin = npc.getTraitNullable(SkinTrait.class);
        return skin != null && skin.getTexture() != null;
    }

    public static void removeSkin(NPC npc) {
        SkinTrait skin = npc.getTraitNullable(SkinTrait.class);
        if(skin != null)
            skin.clearTexture();
        saveNPCs();
    }

    // Gives the NPC a skin, unless it already has one: put together from the parts of its culture's skin
    // folders (or the shared parts if culture is null) and gender, or else a whole skin of its gender.
    // onDone runs on the main thread, with a description of the skin, or null if it failed.
    public static Result pickSkin(NPC npc, Culture culture, Gender gender, Consumer<String> onDone) {
        if(hasSkin(npc))
            return Result.ALREADY_HAS_SKIN;
        if(pending.contains(npc.getId()))
            return Result.BUSY;

        LayersConfig config = loadLayers();
        File skins = skinsFolder();
        SkinComposer composer = new SkinComposer(new File(skins, PARTS_FOLDER), new File(skins, CULTURES_FOLDER),
                config.layers(), MathUtil.random, message -> TTRPG.getInstance().getLogger().warning(message));

        SkinComposer.Composed composed;
        try {
            composed = composer.compose((culture == null) ? List.of() : culture.getSkinFolders(), genderFolder(gender));
        }
        catch(IOException e) {
            TTRPG.getInstance().getLogger().log(Level.WARNING, "Could not put a skin together", e);
            return Result.FAILED;
        }

        if(composed == null)
            return applyWholeSkin(npc, gender, onDone);

        String hash = sha1(composed.png());
        saveGenerated(hash, composed.png());
        String description = String.join(", ", composed.parts());
        return applyImage(npc, composed.png(), hash, config.isSlim(gender), description, onDone);
    }

    // Gives the NPC a skin for its culture and gender (see pickSkin), telling the player how it goes.
    // onApplied runs once the skin is applied. With quietIfNoSkins, nothing is said when no skins are set up,
    // e.g. when creating NPCs on a server that doesn't use this.
    public static void pickSkinFor(NPC npc, Player player, boolean quietIfNoSkins, Runnable onApplied) {
        Culture culture = npc.getOrAddTrait(CultureTrait.class).getCulture();
        Gender gender = npc.getOrAddTrait(GenderTrait.class).getGender();

        Result result = pickSkin(npc, culture, gender, description -> {
            if(description == null) {
                player.sendMessage(Localization.SKIN_FAILED.format());
                return;
            }
            player.sendMessage(Localization.SKIN_APPLIED.format(npc.getName(), description));
            onApplied.run();
        });

        switch(result) {
            case UPLOADING -> player.sendMessage(Localization.SKIN_UPLOADING.format());
            case ALREADY_HAS_SKIN -> player.sendMessage(Localization.SKIN_ALREADY.format(npc.getName()));
            case NO_SKINS -> {
                if(!quietIfNoSkins)
                    player.sendMessage(Localization.SKIN_NONE_AVAILABLE.format(npc.getName(),
                            "plugins/" + TTRPG.getInstance().getName() + "/" + FOLDER + "/" + README_FILE));
            }
            case BUSY -> player.sendMessage(Localization.SKIN_BUSY.format(npc.getName()));
            case FAILED -> player.sendMessage(Localization.SKIN_FAILED.format());
            case APPLIED -> {}
        }
    }

    // A random whole skin from male/ or female/.
    private static Result applyWholeSkin(NPC npc, Gender gender, Consumer<String> onDone) {
        File[] files = new File(skinsFolder(), genderFolder(gender)).listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if(files == null || files.length == 0)
            return Result.NO_SKINS;
        File file = files[MathUtil.random.nextInt(files.length)];

        byte[] image;
        try {
            image = Files.readAllBytes(file.toPath());
        }
        catch(IOException e) {
            TTRPG.getInstance().getLogger().warning("Could not read the skin " + file.getName() + ": " + e.getMessage());
            return Result.FAILED;
        }
        boolean slim = file.getName().toLowerCase().contains("slim");
        return applyImage(npc, image, sha1(image), slim, genderFolder(gender) + "/" + file.getName(), onDone);
    }

    // Applies the image as the NPC's skin: right away if it was uploaded before, otherwise after uploading it.
    private static Result applyImage(NPC npc, byte[] image, String hash, boolean slim, String description, Consumer<String> onDone) {
        YamlConfiguration cache = loadCache();
        if(cache.isConfigurationSection(hash)) {
            apply(npc, cache.getString(hash + ".uuid"), cache.getString(hash + ".value"), cache.getString(hash + ".signature"));
            onDone.accept(description);
            return Result.APPLIED;
        }

        pending.add(npc.getId());
        Bukkit.getScheduler().runTaskAsynchronously(TTRPG.getInstance(), () -> {
            String[] texture = upload(image, slim, description);
            Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> {
                pending.remove(npc.getId());
                if(texture == null) {
                    onDone.accept(null);
                    return;
                }
                YamlConfiguration updated = loadCache();
                updated.set(hash + ".skin", description);
                updated.set(hash + ".uuid", texture[0]);
                updated.set(hash + ".value", texture[1]);
                updated.set(hash + ".signature", texture[2]);
                saveCache(updated);
                apply(npc, texture[0], texture[1], texture[2]);
                onDone.accept(description);
            });
        });
        return Result.UPLOADING;
    }

    private static void apply(NPC npc, String uuid, String value, String signature) {
        npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(uuid, signature, value);
        saveNPCs();
    }

    // Uploads the image through Citizens' skin generator. Returns {uuid, value, signature}, or null.
    // Citizens' generator is called by reflection: it is an internal class whose result type
    // (a JSON object from a library bundled with the server) isn't on the plugin's classpath.
    private static String[] upload(byte[] image, boolean slim, String description) {
        try {
            Class<?> generator = Class.forName("net.citizensnpcs.util.MojangSkinGenerator");
            Method generate = generator.getMethod("generateFromPNG", byte[].class, boolean.class);
            Map<?, ?> data = (Map<?, ?>) generate.invoke(null, image, slim);
            Map<?, ?> texture = (Map<?, ?>) data.get("texture");
            String uuid = String.valueOf(data.get("uuid"));
            String value = (String) texture.get("value");
            String signature = (String) texture.get("signature");
            if(value == null || signature == null)
                throw new IllegalStateException("the response has no signed texture");
            return new String[] { uuid, value, signature };
        }
        catch(Throwable e) {
            TTRPG.getInstance().getLogger().log(Level.WARNING, "Could not create the skin " + description
                    + ". Skins must be 64x64 Minecraft skin images, and MineSkin may limit how often skins are created.", e);
            return null;
        }
    }

    // The layers of put-together skins and the arm model of each gender, from layers.yml.
    record LayersConfig(List<SkinComposer.Layer> layers, boolean slimMale, boolean slimFemale) {
        boolean isSlim(Gender gender) {
            return (gender == Gender.FEMALE) ? this.slimFemale : this.slimMale;
        }
    }

    static LayersConfig loadLayers() {
        File file = new File(skinsFolder(), LAYERS_FILE);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        List<SkinComposer.Layer> layers = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("layers");
        if(section != null) {
            for(String name : section.getKeys(false)) {
                boolean required = section.getBoolean(name + ".required", false);
                double chance = Math.clamp(section.getDouble(name + ".chance", 1), 0, 1);
                layers.add(new SkinComposer.Layer(name, required, chance));
            }
        }
        if(layers.isEmpty())
            layers = DEFAULT_LAYERS;

        return new LayersConfig(layers, yaml.getBoolean("slim.male", false), yaml.getBoolean("slim.female", true));
    }

    private static String genderFolder(Gender gender) {
        return gender.name().toLowerCase();
    }

    // Keeps a copy of each put-together skin, so server owners can see how they look.
    private static void saveGenerated(String hash, byte[] png) {
        File file = new File(new File(skinsFolder(), GENERATED_FOLDER), hash + ".png");
        if(file.exists())
            return;
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), png);
        }
        catch(IOException e) {
            TTRPG.getInstance().getLogger().warning("Could not save the skin " + file.getName() + ": " + e.getMessage());
        }
    }

    private static File cacheFile() {
        return new File(skinsFolder(), CACHE_FILE);
    }

    private static YamlConfiguration loadCache() {
        return YamlConfiguration.loadConfiguration(cacheFile());
    }

    private static void saveCache(YamlConfiguration cache) {
        try {
            cache.options().setHeader(List.of(
                    "Signed textures of the skins in this folder, so each image is only uploaded once.",
                    "Safe to delete: skins are then uploaded again when used."));
            cache.save(cacheFile());
        }
        catch(IOException e) {
            TTRPG.getInstance().getLogger().warning("Could not save the skin cache: " + e.getMessage());
        }
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        }
        catch(NoSuchAlgorithmException e) {
            // Every Java runtime has SHA-1; fall back to something stable anyway.
            return Integer.toHexString(Arrays.hashCode(data));
        }
    }

    private static void saveNPCs() {
        ((Citizens) CitizensAPI.getPlugin()).storeNPCs();
    }
}

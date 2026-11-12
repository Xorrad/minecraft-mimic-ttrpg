package me.mimic.ttrpg.menus;

import me.mimic.ttrpg.core.traits.*;
import me.mimic.lib.ui.Item;
import me.mimic.lib.ui.ItemClickResult;
import me.mimic.lib.ui.Menu;
import me.mimic.lib.ui.TextMenu;
import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.CharacterStats;
import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.Faith;
import me.mimic.ttrpg.core.Gender;
import me.mimic.ttrpg.core.skins.SkinLibrary;
import me.mimic.ttrpg.core.Personality;
import me.mimic.ttrpg.localization.Localization;
import me.mimic.ttrpg.util.CitizensUtil;
import me.mimic.ttrpg.util.SkinUtil;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Inventory;
import net.citizensnpcs.editor.Editor;
import net.citizensnpcs.editor.EquipmentEditor;
import net.citizensnpcs.trait.Controllable;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.SitTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CharacterMenu extends Menu {
    private static final Map<UUID, Listener> PENDING_FAMILY_SELECTIONS = new HashMap<>();

    public NPC npc;

//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | Name     | Culture   | Faith        | Gender       |          |         |         |         |         |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | Strength | Dexterity | Constitution | Intelligence | Charisma |         |         |         |         |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | Greedy   | Loyal     | Chaste       |              |          |         |         |         |         |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | Father   | Mother    | Partner      | Child 1      | Child 2  | Child 3 | Child 4 | Child 5 | Child 6 |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | #        | #         | #            | #            | #        | #       | #       | #       | #       |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+
//    | Mount    | Inv       | Equip        | Sit          | Follow   | Select  | Skin    |         |         |
//    +----------+-----------+--------------+--------------+----------+---------+---------+---------+---------+

    public CharacterMenu(NPC npc) {
        super();
        this.npc = npc;

        // Initialize the menu.
        this.title(Localization.MENU_TITLE_CHARACTER.format(npc.getName()));
        this.size(6);
        this.onClick((p, item, event) -> { return true; });

        this.update();
    }

    private void update() {
        this.updateInfo();
        this.updateStats();
        this.updatePersonalities();
        this.updateFamily();
        this.updateActions();
        this.updateRowBorders();
    }

    private void reopen(Player p) {
        this.destroy();
        this.update();
        this.open(p);
    }

    // Update characters information items
    // such as: name, culture, faith...
    private void updateInfo() {
        final int row = 0;

        Culture culture = npc.getOrAddTrait(CultureTrait.class).getCulture();
        Faith faith = npc.getOrAddTrait(FaithTrait.class).getFaith();
        boolean hasCulture = (culture != null);
        boolean hasFaith = (faith != null);

        this.set(row*9 + 0, new Item()
            .material(Material.PLAYER_HEAD)
            .name(Localization.NAME.format())
            .lore("§f" + npc.getName(), "", Localization.RENAME_DESC.format())
            .head(SkinUtil.getNPCProfile(npc))
            .leftClick((p, m) -> {
                // Open the anvil on the next tick, once the click on this menu is handled.
                Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> openRename(p));
                return ItemClickResult.NO_RESULT;
            })
        );

        this.set(row*9 + 1, new Item()
            .material(hasCulture ? Material.FLOWER_BANNER_PATTERN : Material.BARRIER)
            .name(Localization.CULTURE.format())
            .lore(hasCulture ? "§f" + culture.getName() : Localization.NONE.format())
            .hideAttributes()
            .leftClick((p, m) -> {
                openChangeCulture(p);
                return ItemClickResult.NO_RESULT;
            })
            .rightClick((p, m) -> {
                npc.getOrAddTrait(CultureTrait.class).setCulture(null);
                this.saveNPC();
                this.updateInfo();
                return ItemClickResult.NO_RESULT;
            })
        );

        this.set(row*9 + 2, new Item()
            .material(hasFaith ? Material.END_CRYSTAL : Material.BARRIER)
            .name(Localization.FAITH.format())
            .lore(hasFaith ? "§f" + faith.getName() : Localization.NONE.format())
            .leftClick((p, m) -> {
                openChangeFaith(p);
                return ItemClickResult.NO_RESULT;
            })
            .rightClick((p, m) -> {
                npc.getOrAddTrait(FaithTrait.class).setFaith(null);
                this.saveNPC();
                this.updateInfo();
                return ItemClickResult.NO_RESULT;
            })
        );

        // Clicking switches between male and female.
        GenderTrait genderTrait = npc.getOrAddTrait(GenderTrait.class);
        Gender gender = genderTrait.getGender();
        this.set(row*9 + 3, new Item()
            .material(gender.getIcon())
            .name(Localization.GENDER.format())
            .lore("§f" + gender.getDisplayName(), "", Localization.GENDER_DESC.format())
            .click((p, m) -> {
                genderTrait.setGender(gender.other());
                this.saveNPC();
                this.updateInfo();
                return ItemClickResult.NO_RESULT;
            })
        );
    }

    // Update character stats items such as:
    // strength, dexterity, intelligence, etc...
    private void updateStats() {
        final int row = 1;

        int i = 0;
        for(CharacterStats stat : CharacterStats.values()) {
            StatsTrait trait = npc.getOrAddTrait(StatsTrait.class);

            int mod = trait.getStatModifier(stat);
            String statModifierText = ((mod < 0) ? "§c" : (mod > 0) ? "§a+" : "§7") + mod;

            this.set(row*9 + i, new Item()
                .material(stat.getIcon())
                .name(Localization.valueOf("STAT_" + stat.name()).format())
                .lore("§f" + trait.getStat(stat) + " §7(" + statModifierText + "§7)",
                        "",
                        Localization.DECREASE_BY_ONE.format(),
                        Localization.INCREASE_BY_ONE.format())
                .hideAttributes()
                .leftClick((p, m) -> {
                    int val = Math.max(0, trait.getStat(stat) - 1);
                    trait.setStat(stat, val);
                    this.saveNPC();
                    this.updateStats();
                    return ItemClickResult.NO_RESULT;
                })
                .rightClick((p, m) -> {
                    trait.setStat(stat, trait.getStat(stat) + 1);
                    this.saveNPC();
                    this.updateStats();
                    return ItemClickResult.NO_RESULT;
                })
            );
            i++;
        }
    }

    private void updatePersonalities() {
        final int row = 2;
        PersonalityTrait trait = npc.getOrAddTrait(PersonalityTrait.class);

        for(int i = 0; i < 9; i++) {
            this.set(row*9 + i, new Item().material(Material.AIR));
        }

        int i = 0;
        for(Personality personality : trait.getPersonalities()) {
            this.set(row*9 + i, new Item()
                .material(personality.getIcon())
                .name("§e" + personality.getName())
                .lore("§f" + personality.getDescription(),
                        "",
                        Localization.PERSONALITY_REMOVE.format())
                .hideAttributes()
                .leftClick((p, m) -> {
                    return ItemClickResult.NO_RESULT;
                })
                .rightClick((p, m) -> {
                    trait.removePersonality(personality);
                    this.saveNPC();
                    this.updatePersonalities();
                    return ItemClickResult.NO_RESULT;
                })
            );
            i++;
        }

        this.set(row*9 + i, new Item()
                .material(Material.BARRIER)
                .name(Localization.PERSONALITY_ADD.format())
                .leftClick((p, m) -> {
                    openPersonalityTrait(p);
                    this.saveNPC();
                    this.updatePersonalities();
                    return ItemClickResult.NO_RESULT;
                })
        );
    }

    // Update items for character family members
    // such as: father, mother, partner and children.
    private void updateFamily() {
        final int row = 3;

        FamilyTrait family = npc.getOrAddTrait(FamilyTrait.class);

        int i = 0;
        for(FamilyTrait.Role role : FamilyTrait.Role.values()) {
            this.set(row * 9 + i, makeFamilyHeadItem(Localization.valueOf("FAMILY_" + role.name()).format(), family.getRelative(role))
                    .leftClick((p, m) -> {
                        openChangeFamily(p, newRelative -> {
                            family.setRelative(role, newRelative);
                            p.sendMessage(Localization.FAMILY_CHANGED.format(npc.getName(), role.name().toLowerCase(), newRelative.getName()));
                            this.saveNPC();
                        });
                        return ItemClickResult.CLOSE_INVENTORY;
                    })
                    .rightClick((p, m) -> {
                        family.setRelative(role, null);
                        this.saveNPC();
                        this.updateFamily();
                        return ItemClickResult.NO_RESULT;
                    })
            );
            i++;
        }

        // Clear the children item slots beforehand
        // because if there aren't any children anymore,
        // it won't clear the former ones.
        for(int j = i; j < 9; j++) {
            this.set(row * 9 + j, new Item().material(Material.AIR));
        }

        for(NPC child : family.getChildren()) {
            this.set(row * 9 + i, makeFamilyHeadItem(Localization.FAMILY_CHILD.format(), child));
            i++;
        }
    }

    // Update items related to actions and operations
    // such as: follow, mount, inventory, etc...
    private void updateActions() {
        final int row = 5;

        this.set(row*9 + 0, new Item()
                .material(Material.SADDLE)
                .name(Localization.MOUNT.format())
                .lore(Localization.MOUNT_DESC.format())
                .leftClick((p, m) -> {
                    if (!npc.isSpawned()) {
                        p.sendMessage(Localization.MOUNT_FAILED.format());
                        return ItemClickResult.NO_RESULT;
                    }
                    Controllable trait = (Controllable) npc.getOrAddTrait(Controllable.class);
                    trait.setEnabled(true);
                    trait.mount(p);
                    return ItemClickResult.CLOSE_INVENTORY;
                })
        );

        this.set(row*9 + 1, new Item()
                .material(Material.CHEST)
                .name(Localization.INVENTORY.format())
                .lore(Localization.INVENTORY_DESC.format())
                .leftClick((p, m) -> {
                    if (!npc.hasTrait(Inventory.class)) {
                        p.sendMessage(Localization.INVENTORY_FAILED.format());
                        return ItemClickResult.NO_RESULT;
                    }
                    Inventory trait = (Inventory) npc.getTraitNullable(Inventory.class);
                    this.setClosed(true);
                    trait.openInventory(p);
                    return ItemClickResult.NO_RESULT;
                })
        );

        this.set(row*9 + 2, new Item()
                .material(Material.IRON_CHESTPLATE)
                .name(Localization.EQUIP.format())
                .lore(Localization.EQUIP_DESC.format())
                .leftClick((p, m) -> {
                    if (!npc.isSpawned()) {
                        p.sendMessage(Localization.EQUIP_FAILED.format());
                        return ItemClickResult.NO_RESULT;
                    }
                    this.setClosed(true);
                    Editor.enterOrLeave(p, new EquipmentEditor(p, npc));
                    return ItemClickResult.NO_RESULT;
                })
        );

        this.set(row*9 + 3, new Item()
                .material(Material.OAK_STAIRS)
                .name(Localization.SIT.format())
                .lore(Localization.SIT_DESC.format())
                .leftClick((p, m) -> {
                    if (!npc.isSpawned()) {
                        p.sendMessage(Localization.SIT_FAILED.format());
                        return ItemClickResult.NO_RESULT;
                    }
                    SitTrait trait = (SitTrait)npc.getOrAddTrait(SitTrait.class);
                    boolean toSit = !trait.isSitting();

                    // Keep the trait and only toggle it, like Citizens' /npc sit:
                    // a trait added back after being removed does not take effect.
                    if (!toSit){
                        // The seat is lowered into the block below, so once the character is off it,
                        // move it back up on top of that block instead of leaving it in the ground.
                        Location seat = npc.getStoredLocation().clone();
                        trait.setSitting((Location)null);
                        Bukkit.getScheduler().runTask(TTRPG.getInstance(), () -> {
                            if (npc.isSpawned() && !trait.isSitting())
                                npc.teleport(CitizensUtil.standingLocation(seat), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        });
                    }
                    else {
                        trait.setSitting(CitizensUtil.seatLocation(npc.getStoredLocation()));
                    }
                    this.saveNPC();
                    return ItemClickResult.NO_RESULT;
                })
        );

        this.set(row*9 + 4, new Item()
                .material(Material.FEATHER)
                .name(Localization.FOLLOW.format())
                .lore(Localization.FOLLOW_DESC.format())
                .leftClick((p, m) -> {
                    if (!npc.isSpawned()) {
                        p.sendMessage(Localization.FOLLOW_FAILED.format());
                        return ItemClickResult.NO_RESULT;
                    }

                    // Keep the trait and only toggle it, like Citizens' /npc follow.
                    FollowTrait trait = (FollowTrait) npc.getOrAddTrait(FollowTrait.class);
                    boolean following = !trait.isEnabled();
                    trait.follow(following ? p : null);

                    this.saveNPC();
                    return ItemClickResult.NO_RESULT;
                })
        );

        this.set(row*9 + 5, new Item()
                .material(Material.STICK)
                .name(Localization.SELECT.format())
                .lore(Localization.SELECT_DESC.format())
                .leftClick((p, m) -> {
                    p.performCommand("npc select " + npc.getId());
                    return ItemClickResult.NO_RESULT;
                })
        );

        boolean hasSkin = SkinLibrary.hasSkin(npc);
        this.set(row*9 + 6, new Item()
                .material(Material.PAINTING)
                .name(Localization.SKIN.format())
                .lore(hasSkin ? Localization.SKIN_DESC_REMOVE.format() : Localization.SKIN_DESC_PICK.format())
                .leftClick((p, m) -> {
                    this.pickRandomSkin(p);
                    return ItemClickResult.NO_RESULT;
                })
                .rightClick((p, m) -> {
                    if(SkinLibrary.hasSkin(npc)) {
                        SkinLibrary.removeSkin(npc);
                        p.sendMessage(Localization.SKIN_REMOVED.format(npc.getName()));
                        this.updateInfo();
                        this.updateActions();
                    }
                    return ItemClickResult.NO_RESULT;
                })
        );
    }

    // Gives the NPC a skin for its culture and gender, if it has none yet.
    private void pickRandomSkin(Player player) {
        // The head in the menu shows the skin, so refresh it with the button once the skin is applied.
        SkinLibrary.pickSkinFor(npc, player, false, () -> {
            this.updateInfo();
            this.updateActions();
        });
    }

    // Add glass pane borders between specific rows.
    private void updateRowBorders() {
        // addBorder(2);
        addBorder(4);
    }

    private void addBorder(int row) {
        for(int i = 0; i < 9; i++) {
            this.set(row*9 + i, new Item().material(Material.WHITE_STAINED_GLASS_PANE));
        }
    }

    public void openRename(Player player) {
        if(!player.isOnline())
            return;

        new TextMenu()
                .title(Localization.MENU_TITLE_RENAME.format(npc.getName()))
                .text(npc.getName())
                .item(new Item(Material.NAME_TAG))
                .validate(text -> !text.isBlank(), Localization.RENAME_INVALID.format())
                .onComplete((p, text) -> {
                    String oldName = npc.getName();
                    String newName = text.trim();
                    if(!newName.equals(oldName)) {
                        npc.setName(newName);
                        this.saveNPC();
                        p.sendMessage(Localization.RENAMED.format(oldName, newName));
                    }
                    this.title(Localization.MENU_TITLE_CHARACTER.format(npc.getName()));
                    this.reopen(p);
                })
                .onCancel(this::reopen)
                .open(player);
    }

    public void openChangeCulture(Player player) {
        Culture npcCulture = npc.getOrAddTrait(CultureTrait.class).getCulture();

        Menu menu = new Menu()
                .title(Localization.MENU_TITLE_CHANGE_CULTURE.format(npc.getName()))
                .size(6)
                .onClick((p, item, event) -> { return true; });

        for(Culture culture : TTRPG.getInstance().cultures.values()) {
            menu.add(new Item()
                .material((culture == npcCulture) ? Material.NETHER_STAR : Material.FLOWER_BANNER_PATTERN)
                .name("§e" + culture.getName())
                .lore(Localization.CULTURE_SELECT.format())
                .hideAttributes()
                .leftClick((p, m) -> {
                    npc.getOrAddTrait(CultureTrait.class).setCulture(culture);
                    this.saveNPC();
                    menu.setClosed(true);
                    this.reopen(player);
                    return ItemClickResult.NO_RESULT;
                })
            );
        }

        menu.open(player);
    }

    public void openChangeFaith(Player player) {
        Faith npcFaith = npc.getOrAddTrait(FaithTrait.class).getFaith();

        Menu menu = new Menu()
                .title(Localization.MENU_TITLE_CHANGE_FAITH.format())
                .size(6)
                .onClick((p, item, event) -> { return true; });

        for(Faith faith : TTRPG.getInstance().faiths.values()) {
            menu.add(new Item()
                .material((faith == npcFaith) ? Material.NETHER_STAR : Material.END_CRYSTAL)
                .name("§e" + faith.getName())
                .lore(Localization.FAITH.format())
                .hideAttributes()
                .leftClick((p, m) -> {
                    npc.getOrAddTrait(FaithTrait.class).setFaith(faith);
                    this.saveNPC();
                    menu.setClosed(true);
                    this.reopen(player);
                    return ItemClickResult.NO_RESULT;
                })
            );
        }

        menu.open(player);
    }

    public void openPersonalityTrait(Player player) {
        Menu menu = new Menu()
                .title(Localization.MENU_TITLE_CHANGE_PERSONALITY.format())
                .size(6)
                .onClick((p, item, event) -> { return true; });

        for(Personality trait : Personality.values()) {
            menu.add(new Item()
                    .material(trait.getIcon())
                    .name("§e" + trait.getName())
                    .lore("§f" + trait.getDescription())
                    .hideAttributes()
                    .leftClick((p, m) -> {
                        npc.getOrAddTrait(PersonalityTrait.class).addPersonality(trait);
                        this.saveNPC();
                        menu.setClosed(true);
                        this.reopen(player);
                        return ItemClickResult.NO_RESULT;
                    })
            );
        }

        menu.open(player);
    }

    public void openChangeFamily(Player player, Consumer<NPC> applyFunction) {
        // Only the latest selection of a player waits for a target.
        cancelFamilySelection(player);
        player.sendMessage(Localization.FAMILY_SELECT.format());

        Listener listener = new Listener() {
            @EventHandler
            public void onClick(EntityDamageByEntityEvent event) {
                if(!event.getDamager().equals(player))
                    return;
                NPC target = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
                if (target == null)
                    return;

                event.setCancelled(true);
                if(target.equals(npc)) {
                    player.sendMessage(Localization.FAMILY_SELF.format());
                    return;
                }
                cancelFamilySelection(player);
                applyFunction.accept(target);
            }
        };
        PENDING_FAMILY_SELECTIONS.put(player.getUniqueId(), listener);
        Bukkit.getServer().getPluginManager().registerEvents(listener, TTRPG.getInstance());
    }

    public static void cancelFamilySelection(Player player) {
        Listener previous = PENDING_FAMILY_SELECTIONS.remove(player.getUniqueId());
        if(previous != null)
            HandlerList.unregisterAll(previous);
    }

    private Item makeFamilyHeadItem(String title, NPC relative) {
        if(relative == null) {
            return new Item()
                .material(Material.BARRIER)
                .name(title)
                .lore(Localization.NONE.format());
        }

        return new Item()
                .material(Material.PLAYER_HEAD)
                .name(title)
                .lore("§f" + relative.getName())
                .head(SkinUtil.getNPCProfile(relative));
    }

    private void saveNPC() {
        ((Citizens) CitizensAPI.getPlugin()).storeNPCs();
    }

//    @EventHandler
//    @Override
//    public void onInventoryClose(InventoryCloseEvent event) {
//        super.onInventoryClose(event);
//    }
//
//    @EventHandler
//    @Override
//    public void onInventoryClick(InventoryClickEvent event) {
//        super.onInventoryClick(event);
//    }
}

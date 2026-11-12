package me.mimic.ttrpg.core.traits;

import me.mimic.ttrpg.TTRPG;
import me.mimic.ttrpg.core.Faith;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;

// Stores the faith's ID, like CultureTrait, and looks the faith up when asked:
// a deleted faith then disappears from NPCs right away, and a faith that isn't loaded yet
// when Citizens loads the NPC isn't lost.
@TraitName("faithtrait")
public class FaithTrait extends Trait {
    private String faithId;

    public FaithTrait() {
        super("faithtrait");
        this.faithId = "";
    }

    public Faith getFaith() {
        return TTRPG.getInstance().faiths.getOrDefault(this.faithId, null);
    }

    public void setFaith(Faith faith) {
        this.faithId = (faith == null) ? "" : faith.getId();
    }

    @Override
    public void load(DataKey key) {
        this.faithId = key.getString("id", "");
    }

    @Override
    public void save(DataKey key) {
        key.setString("id", this.faithId);
    }
}

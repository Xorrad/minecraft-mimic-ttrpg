package me.mimic.ttrpg.core.traits;

import me.mimic.ttrpg.core.Gender;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;

// A character's gender. NPCs are male unless set otherwise, including those made before this trait existed.
@TraitName("gendertrait")
public class GenderTrait extends Trait {
    public static final Gender DEFAULT = Gender.MALE;

    private Gender gender;

    public GenderTrait() {
        super("gendertrait");
        this.gender = DEFAULT;
    }

    public Gender getGender() {
        return this.gender;
    }

    public void setGender(Gender gender) {
        this.gender = (gender == null) ? DEFAULT : gender;
    }

    @Override
    public void load(DataKey key) {
        this.setGender(Gender.fromString(key.getString("gender", "")));
    }

    @Override
    public void save(DataKey key) {
        key.setString("gender", this.gender.name());
    }
}

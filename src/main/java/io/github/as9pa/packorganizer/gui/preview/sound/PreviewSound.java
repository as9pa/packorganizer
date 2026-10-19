package io.github.as9pa.packorganizer.gui.preview.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The vanilla 1.8.9 sound events the preview plays, each with the variants its sounds.json entry lists. */
public enum PreviewSound {
    HURT("game.player.hurt", "damage/hit1", "damage/hit2", "damage/hit3"),
    BOW("random.bow", "random/bow"),
    BOW_HIT("random.bowhit", "random/bowhit1", "random/bowhit2", "random/bowhit3", "random/bowhit4"),
    EAT("random.eat", "random/eat1", "random/eat2", "random/eat3"),
    BURP("random.burp", "random/burp"),
    GLASS("game.potion.smash", "random/glass1", "random/glass2", "random/glass3"),
    TELEPORT("mob.endermen.portal", "mob/endermen/portal", "mob/endermen/portal2");

    /** Where a pack keeps its sound files. */
    static final String ROOT = "assets/minecraft/sounds/";

    private final String eventName;
    private final List<String> paths;

    PreviewSound(String eventName, String... variants) {
        this.eventName = eventName;
        List<String> p = new ArrayList<>(variants.length);
        for (String v : variants) {
            p.add(ROOT + v + ".ogg");
        }
        this.paths = Collections.unmodifiableList(p);
    }

    public String eventName() {
        return eventName;
    }

    /** Pack-relative paths: "assets/minecraft/sounds/" + variant + ".ogg". */
    public List<String> paths() {
        return paths;
    }
}

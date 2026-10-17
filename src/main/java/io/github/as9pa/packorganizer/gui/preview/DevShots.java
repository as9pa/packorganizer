package io.github.as9pa.packorganizer.gui.preview;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.Settings;
import io.github.as9pa.packorganizer.filter.PackPaths;
import io.github.as9pa.packorganizer.gui.PlayerPreviewPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Dev-only screenshot harness for the player preview, compared by eye against the approved mock states. Inert unless
 * {@code <mcDataDir>/config/packorganizer/devshots.properties} exists. Then, on the title screen, it sizes the window
 * to 1920x1057 at GUI scale 2, opens the Resource Packs screen and for each state forces the panel's pack and mouse,
 * waits for the pack and its sharp sky (or {@link #READY_TIMEOUT_MS}), lets {@link #SETTLE_FRAMES} frames pass, runs
 * the state's actions on its tick clock and saves {@code <state>.png} (the panel with the mock's 8 px margin) and
 * {@code <state>-full.png}, then quits.
 *
 * <p>Keys: {@code pack} (substring of the pack file name), {@code mouse} (GUI x,y the head follows), {@code states}
 * (comma list, default all in table order), {@code out} (folder under the game dir, default screenshots/devshots).
 * The day cycle is frozen at noon like the mock's {@code freeze=1}; every setting it touches is put back before quitting.
 */
public final class DevShots {
    private static final String FILE = "devshots.properties";
    private static final int WIDE_W = 1920;
    private static final int WIDE_H = 1057;
    private static final int GUI_SCALE = 2;
    private static final long READY_TIMEOUT_MS = 5000;
    private static final int SETTLE_FRAMES = 20;
    /** shoot.js crops the mock states with this margin around the panel, in screen px. */
    private static final int CROP_MARGIN = 8;

    /**
     * One screenshot. A state runs on a tick clock from 0, started once the pack and sky are ready and settled: the
     * right button goes down at 0 when {@code press} is set and comes up at {@code releaseTick}, a hit lands at
     * {@code hitTick}, and the shot is the first frame at {@code shotTick}. Use, hit and enchanted states are rows
     * built from these, timed like the mock's {@code act} and {@code at} (seconds at 20 ticks).
     */
    static final class State {
        final String name;
        int windowW = WIDE_W;
        int windowH = WIDE_H;
        boolean forcePack = true;
        String armor = "diamond";
        HeldItem item = HeldItem.SWORD;
        boolean enchanted;
        boolean sky = true;
        float spin;
        /** GUI x, y the head follows (and the crosshair sits at over the view); null is the {@code mouse} key. */
        int[] mouse;
        boolean press;
        int releaseTick = -1;
        int hitTick = -1;
        int shotTick;

        State(String name) {
            this.name = name;
        }

        State window(int w, int h) { windowW = w; windowH = h; return this; }
        State idle() { forcePack = false; return this; }
        State armor(String material) { armor = material; return this; }
        State item(HeldItem held) { item = held; return this; }
        State enchanted() { enchanted = true; return this; }
        State skyOff() { sky = false; return this; }
        State spin(float deg) { spin = deg; return this; }
        State mouse(int x, int y) { mouse = new int[] {x, y}; return this; }
        /** Right button held from tick 0; the shot comes after {@code ticks} with it still held. */
        State hold(int ticks) { press = true; shotTick = Math.max(shotTick, ticks); return this; }
        /** Right button down at 0, up at {@code ticks}; the shot comes {@code after} ticks later. */
        State use(int ticks, int after) { press = true; releaseTick = ticks; shotTick = Math.max(shotTick, ticks + after); return this; }
        /** A sword click on the model at {@code tick}; the shot comes {@code after} ticks later. */
        State hit(int tick, int after) { hitTick = tick; shotTick = Math.max(shotTick, tick + after); return this; }
    }

    private static final Map<String, State> STATES = new LinkedHashMap<>();

    private static void add(State s) {
        STATES.put(s.name, s);
    }

    static {
        add(new State("default"));
        add(new State("back").spin(180));
        add(new State("block").hold(1));
        for (String material : new String[] {"leather", "chainmail", "iron", "gold"}) {
            add(new State("armor-" + material).armor(material));
        }
        add(new State("item-bow").item(HeldItem.BOW));
        add(new State("item-bowdraw").item(HeldItem.BOW).hold(20));
        add(new State("item-pearl").item(HeldItem.ENDER_PEARL));
        add(new State("item-apple").item(HeldItem.GOLDEN_APPLE));
        add(new State("item-potion").item(HeldItem.SPLASH_POTION));
        add(new State("item-rod").item(HeldItem.FISHING_ROD));
        add(new State("skyoff").skyOff());
        add(new State("idle").idle());
        add(new State("small").window(1200, 700));
        // shoot.js mouse positions are 1920x1057 page px, so GUI px are half.
        add(new State("hit").enchanted().hit(1, 3));
        add(new State("use-arrow").item(HeldItem.BOW).use(20, 1));
        add(new State("use-pearl").item(HeldItem.ENDER_PEARL).mouse(360, 75).use(1, 0));
        add(new State("use-pearl-land").item(HeldItem.ENDER_PEARL).mouse(360, 75).use(1, 11));
        add(new State("use-eat").item(HeldItem.GOLDEN_APPLE).hold(26));
        add(new State("use-effects").item(HeldItem.GOLDEN_APPLE).hold(70));
        add(new State("use-splash").item(HeldItem.SPLASH_POTION).mouse(822, 500).use(1, 20));
        add(new State("use-rod").item(HeldItem.FISHING_ROD).mouse(770, 320).use(1, 49));
        add(new State("crosshair").mouse(900, 160));   // over the sky right of the head at 1920x1057
        add(new State("ench").enchanted());
        add(new State("ench-bow").item(HeldItem.BOW).enchanted().hold(20));
    }

    private static DevShots instance;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<State> queue = new ArrayList<>();
    private final File packFile;
    private final String packName;
    private final int mouseX;
    private final int mouseY;
    private final File outDir;
    private PlayerPreviewPanel panel;

    private boolean started;
    private int index = -1;
    /** Ticks on the state's clock; -1 until the pack and sky are ready and the settle frames have passed. */
    private int tick = -1;
    private long stateStartMs;
    /** Settle frames counted since ready; -1 before. */
    private int frames = -1;
    /** The shot tick has come; the next rendered frame is saved. */
    private boolean shoot;
    private Restore restore;

    /** The settings a run changes, put back before it quits. */
    private static final class Restore {
        final String armor;
        final String item;
        final boolean enchanted;
        final boolean sky;
        final boolean cycle;
        final int guiScale;

        Restore(Settings s, int guiScale) {
            armor = s.previewArmor;
            item = s.previewItem;
            enchanted = s.previewEnchanted;
            sky = s.skyBackdrop;
            cycle = s.skyCycle;
            this.guiScale = guiScale;
        }
    }

    private DevShots(Properties p) {
        String pack = p.getProperty("pack", "").trim();
        packFile = findPack(pack);
        packName = packFile == null ? null : PackPaths.shortName(packFile.getName());
        String[] mouse = p.getProperty("mouse", "360,194").split(",");
        mouseX = Integer.parseInt(mouse[0].trim());
        mouseY = Integer.parseInt(mouse[1].trim());
        outDir = new File(mc.mcDataDir, p.getProperty("out", "screenshots/devshots").trim());
        String states = p.getProperty("states", "").trim();
        if (states.isEmpty()) {
            queue.addAll(STATES.values());
        } else {
            for (String name : states.split(",")) {
                State s = STATES.get(name.trim());
                if (s == null) {
                    Log.LOG.warn("devshots: unknown state {}", name.trim());
                } else {
                    queue.add(s);
                }
            }
        }
        Log.LOG.info("devshots: {} states, pack {}, out {}", queue.size(), packFile, outDir);
    }

    /** Called once from mod init; registers the harness only when the properties file exists. */
    public static void init() {
        File file = new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + PackOrganizer.MODID + File.separator + FILE);
        if (!file.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            p.load(in);
            instance = new DevShots(p);
        } catch (IOException | RuntimeException e) {
            Log.LOG.warn("devshots: cannot read " + file, e);
            return;
        }
        MinecraftForge.EVENT_BUS.register(instance);
    }

    /** The panel announces itself on construction; ignored unless a run is active. */
    public static void attach(PlayerPreviewPanel panel) {
        if (instance != null) {
            instance.panel = panel;
            if (instance.index >= 0 && instance.index < instance.queue.size()) {
                instance.apply(instance.queue.get(instance.index));
            }
        }
    }

    public static void detach(PlayerPreviewPanel panel) {
        if (instance != null && instance.panel == panel) {
            instance.panel = null;
        }
    }

    private File findPack(String part) {
        File[] files = mc.getResourcePackRepository().getDirResourcepacks().listFiles();
        if (part.isEmpty() || files == null) {
            return null;
        }
        for (File f : files) {
            if (f.getName().contains(part)) {
                return f;
            }
        }
        Log.LOG.warn("devshots: no pack matching {}", part);
        return null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu) {
                started = true;
                Settings s = PackOrganizer.settings();
                restore = new Restore(s, mc.gameSettings.guiScale);
                s.skyCycle = false;
                mc.gameSettings.guiScale = GUI_SCALE;
                next();
            }
            return;
        }
        if (index < 0 || index >= queue.size() || shoot) {
            return;
        }
        State s = queue.get(index);
        if (!(mc.currentScreen instanceof GuiScreenResourcePacks) || panel == null) {
            return;
        }
        if (tick < 0) {
            // The clock waits for the pack and sky, so a short action is not over before they load.
            if (frames < 0 && (ready(s) || Minecraft.getSystemTime() - stateStartMs > READY_TIMEOUT_MS)) {
                frames = 0;
            }
            return;
        }
        tick++;
        if (s.press && tick == s.releaseTick) {
            panel.debugUse(false);
        }
        if (tick == s.hitTick) {
            panel.debugHit();
        }
        if (tick >= s.shotTick) {
            shoot = true;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || index < 0 || index >= queue.size()) {
            return;
        }
        if (frames >= 0 && ++frames >= SETTLE_FRAMES) {
            // Settled: the state's clock starts and the right button goes down.
            frames = -1;
            tick = 0;
            if (queue.get(index).press && panel != null) {
                panel.debugUse(true);
            }
        } else if (shoot) {
            shoot = false;
            capture(queue.get(index));
            next();
        }
    }

    private boolean ready(State s) {
        if (!panel.visible()) {
            return true;
        }
        PackModel model = panel.debugModel();
        if (model == null || s.forcePack && packFile != null && model != PreviewTextures.get(packFile)) {
            return false;
        }
        return !s.sky || model.skySharp();
    }

    private void next() {
        index++;
        if (index >= queue.size()) {
            finish();
            return;
        }
        State s = queue.get(index);
        tick = -1;   // before apply(), so the right button waits for the new state's clock
        frames = -1;
        shoot = false;
        if (mc.displayWidth != s.windowW || mc.displayHeight != s.windowH) {
            try {
                Display.setDisplayMode(new DisplayMode(s.windowW, s.windowH));
            } catch (LWJGLException e) {
                Log.LOG.warn("devshots: cannot resize the window", e);
            }
            mc.resize(s.windowW, s.windowH);
        }
        if (!(mc.currentScreen instanceof GuiScreenResourcePacks)) {
            mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));   // the panel attaches and gets apply()
        } else if (panel != null) {
            apply(s);
        }
        stateStartMs = Minecraft.getSystemTime();
        Log.LOG.info("devshots: state {}", s.name);
    }

    /** Puts the panel and settings in the state's pose; the right button waits for the clock to start. */
    private void apply(State s) {
        Settings settings = PackOrganizer.settings();
        settings.previewArmor = s.armor;
        settings.skyBackdrop = s.sky;
        panel.debugItem(HeldItem.SWORD);   // a switch drops a held bow without shooting and reels the rod in
        panel.debugUse(false);
        panel.debugItem(s.item);
        panel.debugEnchanted(s.enchanted);
        int mx = s.mouse != null ? s.mouse[0] : mouseX;
        int my = s.mouse != null ? s.mouse[1] : mouseY;
        panel.debugOverride(s.forcePack && packFile != null, packFile, packName, mx, my, s.spin);
        if (s.press && tick >= 0) {
            panel.debugUse(true);
        }
    }

    private void capture(State s) {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        }
        IntBuffer buf = BufferUtils.createIntBuffer(w * h);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        int[] px = new int[w * h];
        buf.get(px);
        BufferedImage full = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < h; row++) {
            full.setRGB(0, h - 1 - row, w, 1, px, row * w, w);   // GL rows run bottom up
        }
        outDir.mkdirs();
        try {
            ImageIO.write(full, "png", new File(outDir, s.name + "-full.png"));
            if (panel != null && panel.visible()) {
                int scale = new ScaledResolution(mc).getScaleFactor();
                int[] r = panel.debugBounds();
                int x0 = Math.max(0, r[0] * scale - CROP_MARGIN);
                int y0 = Math.max(0, r[1] * scale - CROP_MARGIN);
                int x1 = Math.min(w, (r[0] + r[2]) * scale + CROP_MARGIN);
                int y1 = Math.min(h, (r[1] + r[3]) * scale + CROP_MARGIN);
                ImageIO.write(full.getSubimage(x0, y0, x1 - x0, y1 - y0), "png", new File(outDir, s.name + ".png"));
            } else {
                Log.LOG.info("devshots: {} has the panel hidden, saved the full screen only", s.name);
            }
            Log.LOG.info("devshots: saved {} ({}x{}, {} ms)", s.name, w, h, Minecraft.getSystemTime() - stateStartMs);
        } catch (IOException e) {
            Log.LOG.warn("devshots: cannot save " + s.name, e);
        }
    }

    private void finish() {
        Settings s = PackOrganizer.settings();
        s.previewArmor = restore.armor;
        s.previewItem = restore.item;
        s.previewEnchanted = restore.enchanted;
        s.skyBackdrop = restore.sky;
        s.skyCycle = restore.cycle;
        mc.gameSettings.guiScale = restore.guiScale;
        Log.LOG.info("devshots: done, quitting");
        mc.shutdown();
    }
}

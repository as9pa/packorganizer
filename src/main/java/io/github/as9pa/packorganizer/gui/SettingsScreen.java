package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.Settings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** One ON/OFF toggle per setting. Done saves and returns to the parent screen. */
public class SettingsScreen extends GuiScreen {
    private static final int DONE_ID = 100;
    private static final String[] LABELS = {
            "Pack previews", "Transparent background", "Pack folders",
            "Color filter", "Resolution labels", "Remember open folders"
    };

    private final GuiScreen parent;

    /** Public with this exact signature: GuiModList builds the screen by reflection. */
    public SettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = width / 2 - 100;
        int top = height / 6;
        for (int i = 0; i < LABELS.length; i++) {
            buttonList.add(new GuiButton(i, x, top + i * 24, 200, 20, label(i)));
        }
        buttonList.add(new GuiButton(DONE_ID, x, top + LABELS.length * 24 + 12, 200, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE_ID) {
            Settings.saveNow();
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id >= 0 && button.id < LABELS.length) {
            set(button.id, !value(button.id));
            button.displayString = label(button.id);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Pack Organizer", width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String label(int i) {
        return LABELS[i] + ": " + (value(i) ? "ON" : "OFF");
    }

    private static boolean value(int i) {
        Settings s = PackOrganizer.settings();
        switch (i) {
            case 0: return s.packPreviews;
            case 1: return s.transparentBackground;
            case 2: return s.packFolders;
            case 3: return s.colorFilter;
            case 4: return s.resolutionLabels;
            default: return s.rememberOpenFolders;
        }
    }

    private static void set(int i, boolean v) {
        Settings s = PackOrganizer.settings();
        switch (i) {
            case 0: s.packPreviews = v; break;
            case 1: s.transparentBackground = v; break;
            case 2: s.packFolders = v; break;
            case 3: s.colorFilter = v; break;
            case 4: s.resolutionLabels = v; break;
            default: s.rememberOpenFolders = v; break;
        }
    }
}

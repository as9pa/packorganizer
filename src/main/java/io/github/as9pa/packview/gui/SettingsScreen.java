package io.github.as9pa.packview.gui;

import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.Settings;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** One ON/OFF toggle per setting. Done saves and returns to the parent screen. */
public class SettingsScreen extends GuiScreen {
    private static final int DONE_ID = 100;
    /** In two columns the original switches go left and the player preview switches right. */
    private static final int LEFT_ROWS = 6;

    private final GuiScreen parent;

    /** Public with this exact signature: GuiModList builds the screen by reflection. */
    public SettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        List<Settings.Switch> switches = Settings.SWITCHES;
        int n = switches.size();
        int top = height / 6;
        int rows;
        if (top + n * 24 + 28 <= height - 28) {
            for (int i = 0; i < n; i++) {
                buttonList.add(new GuiButton(i, width / 2 - 100, top + i * 24, 200, 20, label(i)));
            }
            rows = n;
        } else {
            for (int i = 0; i < n; i++) {
                boolean left = i < LEFT_ROWS;
                int x = left ? width / 2 - 155 : width / 2 + 5;
                int row = left ? i : i - LEFT_ROWS;
                buttonList.add(new GuiButton(i, x, top + row * 24, 150, 20, label(i)));
            }
            rows = Math.max(Math.min(n, LEFT_ROWS), n - LEFT_ROWS);
        }
        buttonList.add(new GuiButton(DONE_ID, width / 2 - 100, top + rows * 24 + 12, 200, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE_ID) {
            Settings.saveNow();
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id >= 0 && button.id < Settings.SWITCHES.size()) {
            Settings.Switch sw = Settings.SWITCHES.get(button.id);
            Settings s = PackView.settings();
            sw.set(s, !sw.get(s));
            button.displayString = label(button.id);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "packview", width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String label(int i) {
        Settings.Switch sw = Settings.SWITCHES.get(i);
        return sw.label + ": " + (sw.get(PackView.settings()) ? "ON" : "OFF");
    }
}

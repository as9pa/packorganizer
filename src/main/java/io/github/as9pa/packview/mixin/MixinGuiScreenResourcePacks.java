package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.Settings;
import io.github.as9pa.packview.gui.FilteredList;
import io.github.as9pa.packview.gui.McbridgeWidgets;
import io.github.as9pa.packview.gui.PacksToolbar;
import io.github.as9pa.packview.gui.PlayerPreviewPanel;
import io.github.as9pa.packview.gui.PreviewCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the packview toolbar (search, chips, counter) and pushes both lists down to make room. */
@Mixin(GuiScreenResourcePacks.class)
public abstract class MixinGuiScreenResourcePacks extends GuiScreen {
    private static final int LIST_WIDTH = 200;

    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private List<ResourcePackListEntry> selectedResourcePacks;
    @Shadow private GuiResourcePackAvailable availableResourcePacksList;
    @Shadow private GuiResourcePackSelected selectedResourcePacksList;

    @Unique private PacksToolbar packview$toolbar;
    /** Kept across resizes so the model's motion and the shown pack carry over; dropped on close. */
    @Unique private PlayerPreviewPanel packview$playerPreview;
    /** Scroll of both lists, kept across closing and reopening the screen (and window resizes). */
    @Unique private static int packview$availableScroll;
    @Unique private static int packview$selectedScroll;

    /** On a resize initGui runs again and replaces both lists, so take their scroll before it does. */
    @Inject(method = "initGui", at = @At("HEAD"))
    private void packview$keepScroll(CallbackInfo ci) {
        packview$saveScroll();
    }

    @Unique
    private void packview$saveScroll() {
        if (availableResourcePacksList != null && selectedResourcePacksList != null) {
            packview$availableScroll = availableResourcePacksList.getAmountScrolled();
            packview$selectedScroll = selectedResourcePacksList.getAmountScrolled();
        }
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void packview$addToolbar(CallbackInfo ci) {
        String keptQuery = packview$toolbar == null ? "" : packview$toolbar.query();   // survives a window resize
        int left = width / 2 - 204;
        packview$toolbar = new PacksToolbar((GuiScreenResourcePacks) (Object) this, fontRendererObj, left, PacksToolbar.TOOLBAR_Y,
                availableResourcePacks, selectedResourcePacks, keptQuery);
        buttonList.addAll(packview$toolbar.chips());
        if (packview$toolbar.colorChip() != null) {
            buttonList.add(packview$toolbar.colorChip());
        }

        // setDimensions resets left/right, so re-apply the x bounds vanilla set in initGui.
        availableResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - PacksToolbar.LIST_BOTTOM_INSET);
        availableResourcePacksList.setSlotXBoundsFromLeft(left);
        selectedResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - PacksToolbar.LIST_BOTTOM_INSET);
        selectedResourcePacksList.setSlotXBoundsFromLeft(width / 2 + 4);

        ((FilteredList) availableResourcePacksList).packview$setView(packview$toolbar.visible());
        packview$toolbar.refresh();
        // After refresh, so the Available list's size (and so its scroll clamp) is the real one.
        availableResourcePacksList.scrollBy(packview$availableScroll - availableResourcePacksList.getAmountScrolled());
        selectedResourcePacksList.scrollBy(packview$selectedScroll - selectedResourcePacksList.getAmountScrolled());

        if (packview$playerPreview == null) {
            packview$playerPreview = new PlayerPreviewPanel(this);
        }
        packview$playerPreview.setSelected(selectedResourcePacks);
        packview$playerPreview.layout(width, height);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void packview$refreshView(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PlayerPreviewPanel.resetHover();   // the list rows offer the hovered pack again this frame
        if (packview$toolbar != null) {
            packview$toolbar.refresh();
        }
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreenResourcePacks;drawBackground(I)V"))
    private void packview$transparentBackground(GuiScreenResourcePacks screen, int tint) {
        if (!PackView.settings().transparentBackground) {
            drawBackground(tint);
        } else if (mc.theWorld != null) {
            drawGradientRect(0, 0, width, height, 0xC0101010, 0xD0101010);
        } else {
            drawRect(0, 0, width, height, 0xFF101010);
        }
    }

    // While the color popup is under the mouse, the lists below get no mouse position, so no row hovers.

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiResourcePackAvailable;drawScreen(IIF)V"))
    private void packview$drawAvailable(GuiResourcePackAvailable list, int mouseX, int mouseY, float partialTicks) {
        boolean hidden = packview$popupUnder(mouseX, mouseY);
        list.drawScreen(hidden ? -1 : mouseX, hidden ? -1 : mouseY, partialTicks);
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiResourcePackSelected;drawScreen(IIF)V"))
    private void packview$drawSelected(GuiResourcePackSelected list, int mouseX, int mouseY, float partialTicks) {
        boolean hidden = packview$popupUnder(mouseX, mouseY);
        list.drawScreen(hidden ? -1 : mouseX, hidden ? -1 : mouseY, partialTicks);
    }

    @Unique
    private boolean packview$popupUnder(int mouseX, int mouseY) {
        return packview$toolbar != null && packview$toolbar.popupUnder(mouseX, mouseY);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void packview$drawToolbar(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (packview$playerPreview != null) {
            packview$playerPreview.draw(mouseX, mouseY, partialTicks);
        }
        if (packview$toolbar != null) {
            packview$toolbar.draw(mouseX, mouseY);
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void packview$onChipPressed(GuiButton button, CallbackInfo ci) {
        if (packview$toolbar != null && packview$toolbar.activate(button)) {
            ci.cancel();
        }
    }

    /**
     * Focuses the search box; a click the color panel or the player preview's buttons take never reaches the lists
     * or buttons. A right press anywhere uses the preview's held item and still goes on to vanilla.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void packview$focusSearch(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton == 1 && packview$playerPreview != null) {
            packview$playerPreview.rightPressed();
        }
        if (packview$toolbar != null && packview$toolbar.mouseClicked(mouseX, mouseY, mouseButton)) {
            ci.cancel();
        } else if (packview$playerPreview != null
                && packview$playerPreview.mouseClicked(mouseX, mouseY, mouseButton)) {
            ci.cancel();
        }
    }

    /** GuiScreenResourcePacks declares mouseReleased itself, so hook it rather than override: ends a spin, click or use. */
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void packview$mouseReleased(int mouseX, int mouseY, int state, CallbackInfo ci) {
        if (packview$playerPreview != null) {
            packview$playerPreview.mouseReleased(state);
        }
    }

    // GuiScreenResourcePacks does not override these four, so nothing vanilla is replaced: the mixin
    // merges them as ordinary overrides of GuiScreen and defers to super.

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc closes an open color popup first; only a second Esc closes the screen.
        if (packview$toolbar != null && keyCode == Keyboard.KEY_ESCAPE && packview$toolbar.closePopup()) {
            return;
        }
        if (packview$toolbar != null && keyCode != Keyboard.KEY_ESCAPE && packview$toolbar.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        packview$saveScroll();
        Settings.saveNow();
        PreviewCache.clear();
        if (packview$playerPreview != null) {
            packview$playerPreview.close();
            packview$playerPreview = null;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (packview$toolbar != null) {
            packview$toolbar.mouseDragged(mouseX, mouseY);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (packview$toolbar != null) {
            packview$toolbar.tick();
        }
        if (packview$playerPreview != null) {
            packview$playerPreview.tick();
        }
    }

    /**
     * For the mcbridge automation mod, which finds this by reflection under this exact name (so not @Unique): one
     * map per packview widget on the screen, see {@link McbridgeWidgets}. Empty before the first initGui.
     */
    public List<?> mcbridgeDescribe() {
        List<Object> out = new ArrayList<>();
        if (packview$toolbar != null) {
            McbridgeWidgets.describeToolbar(packview$toolbar, out);
        }
        if (packview$playerPreview != null) {
            packview$playerPreview.describeInto(out);
        }
        McbridgeWidgets.toPixels(out, new ScaledResolution(mc).getScaleFactor());
        return out;
    }

    /**
     * For mcbridge, by reflection like {@link #mcbridgeDescribe()}: true when no preview is loading and the shown
     * player preview has nothing in motion.
     */
    public boolean mcbridgeIdle() {
        return PreviewCache.idle() && (packview$playerPreview == null || !packview$playerPreview.visible()
                || !packview$playerPreview.animating());
    }
}

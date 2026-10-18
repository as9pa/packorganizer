package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.Settings;
import io.github.as9pa.packorganizer.gui.FilteredList;
import io.github.as9pa.packorganizer.gui.PacksToolbar;
import io.github.as9pa.packorganizer.gui.PlayerPreviewPanel;
import io.github.as9pa.packorganizer.gui.PreviewCache;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the packorganizer toolbar (search, chips, counter) and pushes both lists down to make room. */
@Mixin(GuiScreenResourcePacks.class)
public abstract class MixinGuiScreenResourcePacks extends GuiScreen {
    private static final int LIST_WIDTH = 200;
    private static final int LIST_BOTTOM_INSET = 51;

    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private List<ResourcePackListEntry> selectedResourcePacks;
    @Shadow private GuiResourcePackAvailable availableResourcePacksList;
    @Shadow private GuiResourcePackSelected selectedResourcePacksList;

    @Unique private PacksToolbar packorganizer$toolbar;
    /** Kept across resizes so the model's motion and the shown pack carry over; dropped on close. */
    @Unique private PlayerPreviewPanel packorganizer$playerPreview;
    /** Scroll of both lists, kept across closing and reopening the screen (and window resizes). */
    @Unique private static int packorganizer$availableScroll;
    @Unique private static int packorganizer$selectedScroll;

    /** On a resize initGui runs again and replaces both lists, so take their scroll before it does. */
    @Inject(method = "initGui", at = @At("HEAD"))
    private void packorganizer$keepScroll(CallbackInfo ci) {
        packorganizer$saveScroll();
    }

    @Unique
    private void packorganizer$saveScroll() {
        if (availableResourcePacksList != null && selectedResourcePacksList != null) {
            packorganizer$availableScroll = availableResourcePacksList.getAmountScrolled();
            packorganizer$selectedScroll = selectedResourcePacksList.getAmountScrolled();
        }
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void packorganizer$addToolbar(CallbackInfo ci) {
        String keptQuery = packorganizer$toolbar == null ? "" : packorganizer$toolbar.query();   // survives a window resize
        int left = width / 2 - 204;
        packorganizer$toolbar = new PacksToolbar((GuiScreenResourcePacks) (Object) this, fontRendererObj, left, PacksToolbar.TOOLBAR_Y,
                availableResourcePacks, selectedResourcePacks, keptQuery);
        buttonList.addAll(packorganizer$toolbar.chips());
        if (packorganizer$toolbar.colorChip() != null) {
            buttonList.add(packorganizer$toolbar.colorChip());
        }

        // setDimensions resets left/right, so re-apply the x bounds vanilla set in initGui.
        availableResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        availableResourcePacksList.setSlotXBoundsFromLeft(left);
        selectedResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        selectedResourcePacksList.setSlotXBoundsFromLeft(width / 2 + 4);

        ((FilteredList) availableResourcePacksList).packorganizer$setView(packorganizer$toolbar.visible());
        packorganizer$toolbar.refresh();
        // After refresh, so the Available list's size (and so its scroll clamp) is the real one.
        availableResourcePacksList.scrollBy(packorganizer$availableScroll - availableResourcePacksList.getAmountScrolled());
        selectedResourcePacksList.scrollBy(packorganizer$selectedScroll - selectedResourcePacksList.getAmountScrolled());

        if (packorganizer$playerPreview == null) {
            packorganizer$playerPreview = new PlayerPreviewPanel(this);
        }
        packorganizer$playerPreview.setSelected(selectedResourcePacks);
        packorganizer$playerPreview.layout(width, height);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void packorganizer$refreshView(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PlayerPreviewPanel.resetHover();   // the list rows offer the hovered pack again this frame
        if (packorganizer$toolbar != null) {
            packorganizer$toolbar.refresh();
        }
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreenResourcePacks;drawBackground(I)V"))
    private void packorganizer$transparentBackground(GuiScreenResourcePacks screen, int tint) {
        if (!PackOrganizer.settings().transparentBackground) {
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
    private void packorganizer$drawAvailable(GuiResourcePackAvailable list, int mouseX, int mouseY, float partialTicks) {
        boolean hidden = packorganizer$popupUnder(mouseX, mouseY);
        list.drawScreen(hidden ? -1 : mouseX, hidden ? -1 : mouseY, partialTicks);
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiResourcePackSelected;drawScreen(IIF)V"))
    private void packorganizer$drawSelected(GuiResourcePackSelected list, int mouseX, int mouseY, float partialTicks) {
        boolean hidden = packorganizer$popupUnder(mouseX, mouseY);
        list.drawScreen(hidden ? -1 : mouseX, hidden ? -1 : mouseY, partialTicks);
    }

    @Unique
    private boolean packorganizer$popupUnder(int mouseX, int mouseY) {
        return packorganizer$toolbar != null && packorganizer$toolbar.popupUnder(mouseX, mouseY);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void packorganizer$drawToolbar(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (packorganizer$playerPreview != null) {
            packorganizer$playerPreview.draw(mouseX, mouseY, partialTicks);
        }
        if (packorganizer$toolbar != null) {
            packorganizer$toolbar.draw(mouseX, mouseY);
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void packorganizer$onChipPressed(GuiButton button, CallbackInfo ci) {
        if (packorganizer$toolbar != null && packorganizer$toolbar.activate(button)) {
            ci.cancel();
        }
    }

    /**
     * Focuses the search box; a click the color panel or the player preview's buttons take never reaches the lists
     * or buttons. A right press anywhere uses the preview's held item and still goes on to vanilla.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void packorganizer$focusSearch(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton == 1 && packorganizer$playerPreview != null) {
            packorganizer$playerPreview.rightPressed();
        }
        if (packorganizer$toolbar != null && packorganizer$toolbar.mouseClicked(mouseX, mouseY, mouseButton)) {
            ci.cancel();
        } else if (packorganizer$playerPreview != null
                && packorganizer$playerPreview.mouseClicked(mouseX, mouseY, mouseButton)) {
            ci.cancel();
        }
    }

    /** GuiScreenResourcePacks declares mouseReleased itself, so hook it rather than override: ends a spin, click or use. */
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void packorganizer$mouseReleased(int mouseX, int mouseY, int state, CallbackInfo ci) {
        if (packorganizer$playerPreview != null) {
            packorganizer$playerPreview.mouseReleased(state);
        }
    }

    // GuiScreenResourcePacks does not override these four, so nothing vanilla is replaced: the mixin
    // merges them as ordinary overrides of GuiScreen and defers to super.

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc closes an open color popup first; only a second Esc closes the screen.
        if (packorganizer$toolbar != null && keyCode == Keyboard.KEY_ESCAPE && packorganizer$toolbar.closePopup()) {
            return;
        }
        if (packorganizer$toolbar != null && keyCode != Keyboard.KEY_ESCAPE && packorganizer$toolbar.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        packorganizer$saveScroll();
        Settings.saveNow();
        PreviewCache.clear();
        if (packorganizer$playerPreview != null) {
            packorganizer$playerPreview.close();
            packorganizer$playerPreview = null;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (packorganizer$toolbar != null) {
            packorganizer$toolbar.mouseDragged(mouseX, mouseY);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (packorganizer$toolbar != null) {
            packorganizer$toolbar.tick();
        }
        if (packorganizer$playerPreview != null) {
            packorganizer$playerPreview.tick();
        }
    }
}

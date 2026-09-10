package appeng.client.gui.widgets;

import appeng.api.config.LockCraftingMode;
import appeng.client.gui.AEBaseGui;
import appeng.api.config.Settings;
import appeng.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * A state shown as an icon, with what it means in the tooltip. It used to write its title beside the icon too,
 * which put a line of text over whatever the window had there.
 */
public class GuiImgLabel extends GuiLabel implements ITooltip {
    public GuiImgLabel(FontRenderer fontRendererObj, final int x, final int y, final Enum idx, final Enum val) {
        super(fontRendererObj, 0, x, y, 16, 16, 0);
        this.currentValue = val;
        this.labelSetting = idx;

        if (appearances == null) {
            appearances = new HashMap<>();
            // The open and shut padlocks the monitor's lock wears.
            registerApp(16 * 2 + 7, Settings.UNLOCK, LockCraftingMode.NONE, GuiText.NoneLock, null);
            registerApp(16 * 2 + 8, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_LOW, GuiText.CraftingLock, GuiText.LowRedstoneLock);
            registerApp(16 * 2 + 8, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_HIGH, GuiText.CraftingLock, GuiText.HighRedstoneLock);
            registerApp(16 * 2 + 8, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_PULSE, GuiText.CraftingLock, GuiText.UntilPulseUnlock);
            registerApp(16 * 2 + 8, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_RESULT, GuiText.CraftingLock, GuiText.ResultLock);
        }
    }

    private final Enum labelSetting;
    private Enum currentValue;
    private static Map<GuiImgButton.EnumPair, LabelAppearance> appearances;

    public void setVisibility(final boolean vis) {
        this.visible = vis;
    }

    @Override
    public void drawLabel(Minecraft mc, int mouseX, int mouseY) {
        final LabelAppearance appearance = this.getAppearance();
        if (!this.visible || appearance == null) {
            return;
        }

        AEBaseGui.enableSpriteBlending();
        mc.renderEngine.bindTexture(new ResourceLocation("appliedenergistics2", "textures/guis/states.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.drawTexturedModalRect(this.x, this.y, appearance.index % 16 * 16, appearance.index / 16 * 16, 16, 16);
    }

    private LabelAppearance getAppearance() {
        if (this.labelSetting == null || this.currentValue == null) {
            return null;
        }
        return appearances.get(new GuiImgButton.EnumPair(this.labelSetting, this.currentValue));
    }

    private void registerApp(final int iconIndex, final Settings setting, final Enum val, final GuiText title, final GuiText hint) {
        final LabelAppearance a = new LabelAppearance();
        a.index = iconIndex;
        a.title = title.getUnlocalized();
        a.hint = hint == null ? null : hint.getUnlocalized();
        appearances.put(new GuiImgButton.EnumPair(setting, val), a);
    }

    @Override
    public String getMessage() {
        final LabelAppearance appearance = this.getAppearance();
        if (appearance == null) {
            return null;
        }

        final String title = I18n.translateToLocal(appearance.title);
        return appearance.hint == null ? title : title + "\n" + I18n.translateToLocal(appearance.hint);
    }

    public void set(final Enum e) {
        this.currentValue = e;
    }

    @Override
    public int xPos() {
        return x;
    }

    @Override
    public int yPos() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    private static class LabelAppearance {
        public int index;
        public String title;
        public String hint;
    }
}

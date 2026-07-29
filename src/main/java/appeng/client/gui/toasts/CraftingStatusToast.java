package appeng.client.gui.toasts;

import appeng.core.localization.GuiText;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public class CraftingStatusToast implements IToast {
	private final ItemStack itemStack;
	private final String label;
	private final boolean cancelled;
	private long firstDrawTime;
	private boolean newDisplay;

	/**
	 * @param itemStack what to draw as the icon
	 * @param label     amount and name, already formatted by the key's own type - a fluid job reads
	 *                  "1B Water", not "1000 Water"
	 */
	public CraftingStatusToast(@NotNull ItemStack itemStack, @NotNull String label, boolean cancelled) {
		this.itemStack = itemStack;
		this.label = label;
		this.cancelled = cancelled;
	}

	@NotNull
	public Visibility draw(@NotNull GuiToast toastGui, long delta)
	{
		if (this.newDisplay)
		{
			this.firstDrawTime = delta;
			this.newDisplay = false;
		}
		var minecraft = toastGui.getMinecraft();
		var fontRenderer = minecraft.fontRenderer;

		// Texture
		minecraft.getTextureManager().bindTexture(TEXTURE_TOASTS);
		GlStateManager.color(1.0F, 1.0F, 1.0F);
		toastGui.drawTexturedModalRect(0, 0, 0, 32, 160, 32);

		// Text
		var statusText = cancelled ? GuiText.CraftingToastCancelled : GuiText.CraftingToastDone;
		fontRenderer.drawString(statusText.getLocal(), 30, 7, -11534256);
		fontRenderer.drawString(this.label, 30, 18, -16777216);

		// Item
		RenderHelper.enableGUIStandardItemLighting();
		minecraft.getRenderItem().renderItemAndEffectIntoGUI(null, itemStack, 8, 8);

		return delta - this.firstDrawTime < 5000L ? Visibility.SHOW : Visibility.HIDE;
	}
}

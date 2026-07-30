package appeng.client.gui.toasts;

import appeng.core.localization.GuiText;
import net.minecraft.client.gui.FontRenderer;
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

	/**
	 * The toast slot is 160x32 and cannot be widened: 1.12.2's {@link IToast} has no {@code width()} (that
	 * arrives in later versions), and {@code GuiToast.ToastInstance.render} hardcodes 160 both for the slot
	 * and for the slide-in animation, so anything drawn past it would overlap the neighbouring toast and
	 * slide wrong. Long names are therefore trimmed with an ellipsis instead.
	 */
	private static final int WIDTH = 160;
	private static final int TEXT_X = 30;
	private static final int TEXT_MAX_WIDTH = WIDTH - TEXT_X - 5;
	private static final String ELLIPSIS = "...";

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
		fontRenderer.drawString(fit(fontRenderer, statusText.getLocal()), TEXT_X, 7, -11534256);
		fontRenderer.drawString(fit(fontRenderer, this.label), TEXT_X, 18, -16777216);

		// Item
		RenderHelper.enableGUIStandardItemLighting();
		minecraft.getRenderItem().renderItemAndEffectIntoGUI(null, itemStack, 8, 8);

		return delta - this.firstDrawTime < 5000L ? Visibility.SHOW : Visibility.HIDE;
	}

	/**
	 * Trims {@code text} to the toast's usable width, appending an ellipsis when it had to cut. The amount
	 * sits at the front of the label ("64 Redstone", "1B Water"), so trimming from the right keeps the
	 * number - the part that cannot be guessed from the icon. {@code trimStringToWidth} tracks section
	 * codes, so a coloured name is never cut mid-escape.
	 */
	private static String fit(FontRenderer fontRenderer, String text) {
		if (fontRenderer.getStringWidth(text) <= TEXT_MAX_WIDTH) {
			return text;
		}
		return fontRenderer.trimStringToWidth(text, TEXT_MAX_WIDTH - fontRenderer.getStringWidth(ELLIPSIS)) + ELLIPSIS;
	}
}

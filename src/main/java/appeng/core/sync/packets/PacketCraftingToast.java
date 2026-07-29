package appeng.core.sync.packets;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.toasts.CraftingStatusToast;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

/**
 * The crafting-completion / cancellation toast (fork-specific, no upstream equivalent -- CONTRACT.md §10).
 * Pinned signature: {@code PacketCraftingToast(GenericStack stack, boolean cancelled)}, called by
 * {@code CraftingCPUCluster} (wave 2).
 */
public class PacketCraftingToast extends AppEngPacket {
	private final GenericStack stack;
	private final boolean cancelled;

	public PacketCraftingToast(final ByteBuf stream) throws IOException {
		this.stack = GenericStack.readBuffer(stream);
		this.cancelled = stream.readBoolean();
	}

	public PacketCraftingToast(GenericStack stack, boolean cancelled) throws IOException {
		this.stack = stack;
		this.cancelled = cancelled;

		final ByteBuf data = Unpooled.buffer();

		data.writeInt(this.getPacketID());
		GenericStack.writeBuffer(stack, data);
		data.writeBoolean(cancelled);

		this.configureWrite(data);
	}

	@Override
	public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
		if (AEConfig.instance().isFeatureEnabled(AEFeature.CRAFTING_TOASTS)) {
			doCraftingToast();
		}
	}

	@SideOnly(Side.CLIENT)
	private void doCraftingToast() {
		final String label = stack.what().formatAmount(stack.amount(), AmountFormat.FULL)
				+ " " + stack.what().getDisplayName().getFormattedText();
		Minecraft.getMinecraft().getToastGui()
				.add(new CraftingStatusToast(stack.what().wrapForDisplayOrFilter(), label, cancelled));
	}

	@Override
	public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {}
}

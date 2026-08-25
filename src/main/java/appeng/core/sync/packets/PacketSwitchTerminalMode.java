package appeng.core.sync.packets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTerminalMode;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.interfaces.IWirelessTerminalContainer;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.WirelessTerminalModes;
import appeng.util.Platform;

/**
 * A mode button pressed on an open wireless terminal.
 *
 * <p>Unlike the key bindings, this one works on the terminal the screen was opened from rather than on the
 * first one found in the inventory - a player carrying two of them would otherwise switch the wrong one.</p>
 */
public class PacketSwitchTerminalMode extends AppEngPacket {

    private final ResourceLocation mode;

    public PacketSwitchTerminalMode(final ByteBuf stream) throws IOException {
        final DataInputStream dis = new DataInputStream(
                this.getPacketByteArray(stream, stream.readerIndex(), stream.readableBytes()));
        this.mode = new ResourceLocation(dis.readUTF());
    }

    public PacketSwitchTerminalMode(final ResourceLocation mode) throws IOException {
        this.mode = mode;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);
        dos.writeUTF(mode.toString());
        data.writeBytes(bos.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!(player.openContainer instanceof IWirelessTerminalContainer)
                || !(player.openContainer instanceof IInventorySlotAware)) {
            return;
        }

        final IWirelessTerminalMode target = AEApi.instance().registries().wirelessTerminalModes().getMode(this.mode);
        if (target == null) {
            return;
        }

        final ItemStack terminal = ((IWirelessTerminalContainer) player.openContainer).getTerminal();
        if (terminal.isEmpty()) {
            return;
        }

        if (!WirelessTerminalModes.isUnlocked(terminal, this.mode)) {
            player.sendMessage(PlayerMessages.TerminalModeNotUnlocked.get());
            return;
        }

        final IWirelessTermHandler handler = AEApi.instance().registries().wireless()
                .getWirelessTerminalHandler(terminal);
        if (handler == null) {
            return;
        }

        WirelessTerminalModes.setModeId(terminal, this.mode);

        // Told which slot rather than letting it be worked out: the overload that works it out loses the
        // bauble flag on the way, and a terminal worn as a bauble would be looked for in the backpack.
        final IInventorySlotAware where = (IInventorySlotAware) player.openContainer;
        Platform.openGUI(player, where.getInventorySlot(), (GuiBridge) handler.getGuiHandler(terminal),
                where.isBaubleSlot());
    }
}

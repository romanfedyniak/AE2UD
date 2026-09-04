package appeng.core.sync.packets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTerminalMode;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.WirelessTerminalAccess;
import appeng.helpers.WirelessTerminalModes;
import appeng.util.Platform;
import baubles.api.BaublesApi;

/**
 * A key binding asking for the wireless terminal to be opened in one particular mode.
 *
 * <p>The mode travels by name, not by number: the list of modes is built from a registry an addon can add to,
 * so two installs do not agree on what index three is.</p>
 */
public class PacketTerminalUse extends AppEngPacket {

    private final ResourceLocation mode;

    /** Why no terminal opened, ranked by {@link WirelessTerminalAccess.Complaints}. */
    private WirelessTerminalAccess.Complaints complaints;

    public PacketTerminalUse(final ByteBuf stream) throws IOException {
        final DataInputStream dis = new DataInputStream(
                this.getPacketByteArray(stream, stream.readerIndex(), stream.readableBytes()));
        this.mode = new ResourceLocation(dis.readUTF());
    }

    public PacketTerminalUse(final ResourceLocation mode) throws IOException {
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
        final IWirelessTerminalMode target = AEApi.instance().registries().wirelessTerminalModes().getMode(this.mode);
        if (target == null) {
            return;
        }

        this.complaints = new WirelessTerminalAccess.Complaints();

        // Every terminal the player has, not the first one found: one of them may be the only one that knows
        // this mode, or the only one still charged.
        final NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
        for (int i = 0; i < mainInventory.size(); i++) {
            if (this.tryOpen(mainInventory.get(i), i, player, false)) {
                return;
            }
        }

        if (Platform.isModLoaded("baubles") && this.tryBaubles(player)) {
            return;
        }

        this.complaints.tell(player);
    }

    @Optional.Method(modid = "baubles")
    private boolean tryBaubles(final EntityPlayer player) {
        for (int i = 0; i < BaublesApi.getBaublesHandler(player).getSlots(); i++) {
            if (this.tryOpen(BaublesApi.getBaublesHandler(player).getStackInSlot(i), i, player, true)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true once a screen has been opened, after which nothing else is looked at.
     */
    private boolean tryOpen(final ItemStack itemStack, final int slotIdx, final EntityPlayer player,
            final boolean isBauble) {
        if (itemStack.isEmpty()
                || !AEApi.instance().definitions().items().wirelessTerminal().isSameAs(itemStack)) {
            return false;
        }

        if (!WirelessTerminalModes.isUnlocked(itemStack, this.mode)) {
            this.complaints.add(PlayerMessages.TerminalModeNotUnlocked);
            return false;
        }

        final IWirelessTermHandler handler = AEApi.instance().registries().wireless()
                .getWirelessTerminalHandler(itemStack);
        if (handler == null) {
            return false;
        }

        final String unparsedKey = handler.getEncryptionKey(itemStack);
        if (unparsedKey.isEmpty()) {
            this.complaints.add(PlayerMessages.DeviceNotLinked);
            return false;
        }

        final ILocatable securityStation = AEApi.instance().registries().locatable()
                .getLocatableBy(Long.parseLong(unparsedKey));
        if (securityStation == null) {
            this.complaints.add(PlayerMessages.StationCanNotBeLocated);
            return false;
        }

        if (!handler.hasPower(player, 0.5, itemStack)) {
            this.complaints.add(PlayerMessages.DeviceNotPowered);
            return false;
        }

        // Written before the screen is asked for, because the screen is chosen by reading it back off the item.
        WirelessTerminalModes.setModeId(itemStack, this.mode);
        Platform.openGUI(player, slotIdx, (GuiBridge) handler.getGuiHandler(itemStack), isBauble);
        return true;
    }
}

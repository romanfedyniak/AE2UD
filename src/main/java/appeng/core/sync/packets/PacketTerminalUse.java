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

        final NonNullList<ItemStack> mainInventory = player.inventory.mainInventory;
        for (int i = 0; i < mainInventory.size(); i++) {
            final ItemStack is = mainInventory.get(i);
            if (this.isTerminal(is)) {
                this.openGui(is, i, player, false);
                return;
            }
        }

        if (Platform.isModLoaded("baubles")) {
            this.tryOpenBauble(player);
        }
    }

    /**
     * Any wireless terminal that carries modes, rather than one particular item - there is only the one now,
     * and the three left over from before convert themselves before a key can reach them.
     */
    private boolean isTerminal(final ItemStack is) {
        return !is.isEmpty()
                && AEApi.instance().definitions().items().wirelessTerminal().isSameAs(is);
    }

    @Optional.Method(modid = "baubles")
    private void tryOpenBauble(final EntityPlayer player) {
        for (int i = 0; i < BaublesApi.getBaublesHandler(player).getSlots(); i++) {
            final ItemStack is = BaublesApi.getBaublesHandler(player).getStackInSlot(i);
            if (this.isTerminal(is)) {
                this.openGui(is, i, player, true);
                break;
            }
        }
    }

    private void openGui(final ItemStack itemStack, final int slotIdx, final EntityPlayer player,
            final boolean isBauble) {
        if (!WirelessTerminalModes.isUnlocked(itemStack, this.mode)) {
            player.sendMessage(PlayerMessages.TerminalModeNotUnlocked.get());
            return;
        }

        final IWirelessTermHandler handler = AEApi.instance().registries().wireless()
                .getWirelessTerminalHandler(itemStack);
        if (handler == null) {
            return;
        }

        final String unparsedKey = handler.getEncryptionKey(itemStack);
        if (unparsedKey.isEmpty()) {
            player.sendMessage(PlayerMessages.DeviceNotLinked.get());
            return;
        }

        final long parsedKey = Long.parseLong(unparsedKey);
        final ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);
        if (securityStation == null) {
            player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());
            return;
        }

        if (!handler.hasPower(player, 0.5, itemStack)) {
            player.sendMessage(PlayerMessages.DeviceNotPowered.get());
            return;
        }

        // Written before the screen is asked for, because the screen is chosen by reading it back off the item.
        WirelessTerminalModes.setModeId(itemStack, this.mode);
        Platform.openGUI(player, slotIdx, (GuiBridge) handler.getGuiHandler(itemStack), isBauble);
    }
}

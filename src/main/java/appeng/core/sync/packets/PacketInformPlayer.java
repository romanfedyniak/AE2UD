package appeng.core.sync.packets;

import appeng.api.stacks.GenericStack;
import appeng.core.AppEng;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * "Crafting could not extract what it needed" player notification (fork-specific, no upstream equivalent --
 * CONTRACT.md §10). Pinned signature: {@code PacketInformPlayer(GenericStack expected, @Nullable GenericStack
 * actual, InfoType type)}, called by {@code CraftingTreeNode}/{@code MECraftingInventory} (wave 2).
 */
public class PacketInformPlayer extends AppEngPacket {
    private GenericStack actualItem = null;
    private GenericStack reportedItem = null;
    private final InfoType type;

    public PacketInformPlayer(ByteBuf stream) throws IOException {
        this.type = InfoType.values()[stream.readInt()];
        switch (type) {
            case PARTIAL_ITEM_EXTRACTION:
                this.reportedItem = GenericStack.readBuffer(stream);
                this.actualItem = GenericStack.readBuffer(stream);
                break;
            case NO_ITEMS_EXTRACTED:
                this.reportedItem = GenericStack.readBuffer(stream);
                break;
        }
    }

    public PacketInformPlayer(GenericStack expected, @Nullable GenericStack actual, InfoType type) throws IOException {
        this.reportedItem = expected;
        this.actualItem = actual;
        this.type = type;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeInt(type.ordinal());

        GenericStack.writeBuffer(reportedItem, data);
        if (actualItem != null) {
            GenericStack.writeBuffer(actualItem, data);
        }

        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (this.type == InfoType.PARTIAL_ITEM_EXTRACTION) {
            AppEng.proxy.getPlayers().get(0).sendStatusMessage(new TextComponentString("System reported " + reportedItem.amount() + " " + reportedItem.what().getDisplayName().getFormattedText() + " available but could only extract " + GenericStack.getStackSizeOrZero(actualItem)), false);
        } else if (this.type == InfoType.NO_ITEMS_EXTRACTED) {
            AppEng.proxy.getPlayers().get(0).sendStatusMessage(new TextComponentString("System reported " + reportedItem.amount() + " " + reportedItem.what().getDisplayName().getFormattedText() + " available but could not extract anything"), false);
        }
    }

    public enum InfoType {
        PARTIAL_ITEM_EXTRACTION, NO_ITEMS_EXTRACTED
    }
}

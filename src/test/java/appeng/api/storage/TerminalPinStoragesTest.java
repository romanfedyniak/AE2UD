package appeng.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class TerminalPinStoragesTest {

    @Test
    void defaultsAndClampsRows() {
        AtomicInteger saves = new AtomicInteger();
        IPlayerTerminalPins pins = TerminalPinStorages.forHost(saves::incrementAndGet)
                .forPlayer(UUID.randomUUID());

        assertEquals(1, pins.getCraftingRows());
        assertEquals(0, pins.getPlayerRows());

        pins.setCraftingRows(99);
        pins.setPlayerRows(-4);
        assertEquals(IPlayerTerminalPins.MAX_ROWS, pins.getCraftingRows());
        assertEquals(0, pins.getPlayerRows());
        assertEquals(1, saves.get());
    }

    @Test
    void persistsRowsPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ITerminalPinStorage source = TerminalPinStorages.forHost(null);
        source.forPlayer(first).setCraftingRows(4);
        source.forPlayer(first).setPlayerRows(3);
        source.forPlayer(second).setCraftingRows(2);

        NBTTagCompound tag = new NBTTagCompound();
        source.writeToNBT(tag, "pins");

        ITerminalPinStorage restored = TerminalPinStorages.forHost(null);
        restored.readFromNBT(tag, "pins");
        assertEquals(4, restored.forPlayer(first).getCraftingRows());
        assertEquals(3, restored.forPlayer(first).getPlayerRows());
        assertEquals(2, restored.forPlayer(second).getCraftingRows());
        assertEquals(0, restored.forPlayer(second).getPlayerRows());
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.stacks.AEKey;

/** Factory methods for persistent terminal pin storage. */
public final class TerminalPinStorages {

    private static final String ITEM_TAG = "terminalPins";

    private TerminalPinStorages() {
    }

    /** Creates storage for a block or part. The host calls read/write from its own NBT methods. */
    public static ITerminalPinStorage forHost(Runnable saveCallback) {
        return new Storage(saveCallback);
    }

    /** Creates storage backed directly by the supplied terminal item's NBT. */
    public static ITerminalPinStorage forItem(ItemStack stack, Runnable saveCallback) {
        Objects.requireNonNull(stack, "stack");
        Storage storage = new Storage(null);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            storage.readFromNBT(tag, ITEM_TAG);
        }
        storage.itemStack = stack;
        storage.externalSaveCallback = saveCallback;
        return storage;
    }

    private static final class Storage implements ITerminalPinStorage {
        private final Map<UUID, PlayerPins> players = new HashMap<>();
        private final Runnable saveCallback;
        private ItemStack itemStack;
        private Runnable externalSaveCallback;
        private boolean loading;

        private Storage(@Nullable Runnable saveCallback) {
            this.saveCallback = saveCallback;
        }

        @Override
        public IPlayerTerminalPins forPlayer(UUID playerId) {
            return players.computeIfAbsent(Objects.requireNonNull(playerId, "playerId"),
                    ignored -> new PlayerPins(this));
        }

        @Override
        public void readFromNBT(NBTTagCompound tag, String name) {
            players.clear();
            if (!tag.hasKey(name, Constants.NBT.TAG_LIST)) {
                return;
            }
            loading = true;
            try {
                NBTTagList list = tag.getTagList(name, Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound playerTag = list.getCompoundTagAt(i);
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(playerTag.getString("player"));
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    PlayerPins pins = new PlayerPins(this);
                    pins.craftingRows = clampRows(playerTag.getInteger("craftingRows"));
                    pins.playerRows = clampRows(playerTag.getInteger("playerRows"));
                    NBTTagList keys = playerTag.getTagList("keys", Constants.NBT.TAG_COMPOUND);
                    for (int j = 0; j < keys.tagCount(); j++) {
                        NBTTagCompound keyTag = keys.getCompoundTagAt(j);
                        int slot = keyTag.getInteger("slot");
                        if (slot >= 0 && slot < IPlayerTerminalPins.MAX_PINS) {
                            pins.keys[slot] = AEKey.fromTagGeneric(keyTag);
                        }
                    }
                    players.put(playerId, pins);
                }
            } finally {
                loading = false;
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag, String name) {
            NBTTagList list = new NBTTagList();
            for (Map.Entry<UUID, PlayerPins> entry : players.entrySet()) {
                PlayerPins pins = entry.getValue();
                NBTTagCompound playerTag = new NBTTagCompound();
                playerTag.setString("player", entry.getKey().toString());
                playerTag.setInteger("craftingRows", pins.craftingRows);
                playerTag.setInteger("playerRows", pins.playerRows);
                NBTTagList keys = new NBTTagList();
                for (int slot = 0; slot < pins.keys.length; slot++) {
                    AEKey key = pins.keys[slot];
                    if (key != null) {
                        NBTTagCompound keyTag = new NBTTagCompound();
                        keyTag.setInteger("slot", slot);
                        key.toTagGeneric(keyTag);
                        keys.appendTag(keyTag);
                    }
                }
                playerTag.setTag("keys", keys);
                list.appendTag(playerTag);
            }
            if (list.tagCount() == 0) {
                tag.removeTag(name);
            } else {
                tag.setTag(name, list);
            }
        }

        private void changed() {
            if (loading) {
                return;
            }
            if (itemStack != null) {
                NBTTagCompound tag = itemStack.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    itemStack.setTagCompound(tag);
                }
                writeToNBT(tag, ITEM_TAG);
                if (externalSaveCallback != null) {
                    externalSaveCallback.run();
                }
            } else if (saveCallback != null) {
                saveCallback.run();
            }
        }
    }

    private static final class PlayerPins implements IPlayerTerminalPins {
        private final Storage owner;
        private final AEKey[] keys = new AEKey[MAX_PINS];
        private int craftingRows = 1;
        private int playerRows;

        private PlayerPins(Storage owner) {
            this.owner = owner;
        }

        @Override
        public int getCraftingRows() {
            return craftingRows;
        }

        @Override
        public void setCraftingRows(int rows) {
            int value = clampRows(rows);
            if (craftingRows != value) {
                craftingRows = value;
                owner.changed();
            }
        }

        @Override
        public int getPlayerRows() {
            return playerRows;
        }

        @Override
        public void setPlayerRows(int rows) {
            int value = clampRows(rows);
            if (playerRows != value) {
                playerRows = value;
                owner.changed();
            }
        }

        @Override
        @Nullable
        public AEKey getPin(int slot) {
            checkSlot(slot);
            return keys[slot];
        }

        @Override
        public void setPin(int slot, @Nullable AEKey key) {
            checkSlot(slot);
            if (!Objects.equals(keys[slot], key)) {
                keys[slot] = key;
                owner.changed();
            }
        }
    }

    private static int clampRows(int rows) {
        return Math.max(0, Math.min(IPlayerTerminalPins.MAX_ROWS, rows));
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= IPlayerTerminalPins.MAX_PINS) {
            throw new IndexOutOfBoundsException("Pin slot " + slot);
        }
    }
}

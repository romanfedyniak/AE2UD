/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
 */

package appeng.api.upgrades;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.implementations.tiles.ISegmentedInventory;

/**
 * Access to the standard AE2 upgrade-card stacks.
 */
public final class UpgradeCards {

    private UpgradeCards() {
    }

    public static ItemStack capacity() {
        return stack(AEApi.instance().definitions().materials().cardCapacity());
    }

    public static ItemStack redstone() {
        return stack(AEApi.instance().definitions().materials().cardRedstone());
    }

    public static ItemStack crafting() {
        return stack(AEApi.instance().definitions().materials().cardCrafting());
    }

    public static ItemStack magnet() {
        return stack(AEApi.instance().definitions().materials().cardMagnet());
    }

    public static ItemStack sticky() {
        return stack(AEApi.instance().definitions().materials().cardSticky());
    }

    public static ItemStack fakeCrafting() {
        return stack(AEApi.instance().definitions().materials().cardFakeCrafting());
    }

    /** Not {@code void()}, which is a keyword. The item reads "Overflow Destruction Card". */
    public static ItemStack voidCard() {
        return stack(AEApi.instance().definitions().materials().cardVoid());
    }

    /** Gives a powered tool a larger battery. */
    public static ItemStack energy() {
        return stack(AEApi.instance().definitions().materials().cardEnergy());
    }

    public static ItemStack equalDistribution() {
        return stack(AEApi.instance().definitions().materials().cardEqualDistribution());
    }

    public static ItemStack fuzzy() {
        return stack(AEApi.instance().definitions().materials().cardFuzzy());
    }

    public static ItemStack speed() {
        return stack(AEApi.instance().definitions().materials().cardSpeed());
    }

    public static ItemStack inverter() {
        return stack(AEApi.instance().definitions().materials().cardInverter());
    }

    public static ItemStack patternExpansion() {
        return stack(AEApi.instance().definitions().materials().cardPatternExpansion());
    }

    public static ItemStack quantumLink() {
        return stack(AEApi.instance().definitions().materials().cardQuantumLink());
    }

    /**
     * Puts the held card into the machine the player is sneak-clicking, which is how every upgrade card is
     * installed without opening a screen. An addon's card calls this from {@code onItemUseFirst}:
     *
     * <pre>
     * if (UpgradeCards.installHeldCard(player, hand, world, pos, new Vec3d(hitX, hitY, hitZ))) {
     *     return world.isRemote ? EnumActionResult.PASS : EnumActionResult.SUCCESS;
     * }
     * </pre>
     *
     * @return true if that block takes upgrade cards and the card went in; on the client, true as soon as the
     *         block would take it, since the card itself only moves on the server.
     */
    public static boolean installHeldCard(final EntityPlayer player, final EnumHand hand, final World world,
            final BlockPos pos, final Vec3d hit) {
        final ItemStack held = player.getHeldItem(hand);

        if (held.isEmpty() || !AEApi.instance().registries().upgrades().isUpgradeCard(held)) {
            return false;
        }

        final TileEntity te = world.getTileEntity(pos);
        IItemHandler upgrades = null;
        Object host = null;

        if (te instanceof IPartHost) {
            final SelectedPart sp = ((IPartHost) te).selectPart(hit);
            if (sp.part instanceof IUpgradeableHost) {
                upgrades = ((ISegmentedInventory) sp.part).getInventoryByName("upgrades");
                host = sp.part;
            }
        } else if (te instanceof IUpgradeableHost) {
            upgrades = ((ISegmentedInventory) te).getInventoryByName("upgrades");
            host = te;
        }

        if (upgrades == null) {
            return false;
        }

        if (world.isRemote) {
            return true;
        }

        // The machine's window asks for build rights at the spot and on its network; so does this. Refused, the
        // click is still used up, so it opens no window either.
        if (!world.canMineBlockBody(player, pos) || !mayBuild(host, player)) {
            return true;
        }

        ItemStack remaining = held;
        for (int slot = 0; slot < upgrades.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = upgrades.insertItem(slot, remaining, false);
        }
        player.setHeldItem(hand, remaining);
        return true;
    }

    private static boolean mayBuild(final Object host, final EntityPlayer player) {
        final IGridNode node = host instanceof IActionHost ? ((IActionHost) host).getActionableNode() : null;
        if (node == null || node.getGrid() == null) {
            return true;
        }
        return ((ISecurityGrid) node.getGrid().getCache(ISecurityGrid.class)).hasPermission(player, SecurityPermissions.BUILD);
    }

    private static ItemStack stack(final IItemDefinition definition) {
        return definition.maybeStack(1).orElse(ItemStack.EMPTY);
    }
}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.networking;


import appeng.api.util.AEAxisAlignedBB;
import appeng.block.AEBaseTileBlock;
import appeng.helpers.AEGlassMaterial;
import appeng.helpers.ICustomCollision;
import appeng.tile.networking.TileCrystalResonanceGenerator;
import com.google.common.collect.Lists;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;


public class BlockCrystalResonanceGenerator extends AEBaseTileBlock implements ICustomCollision {

    private static final double TWO_PIXELS = 2.0 / 16.0;
    private static final double ONE_PIXEL = 1.0 / 16.0;

    public BlockCrystalResonanceGenerator() {
        super(AEGlassMaterial.INSTANCE);

        this.setFullSize(this.setOpaque(false));
        this.setLightOpacity(0);
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(final World w, final BlockPos pos, final Entity thePlayer, final boolean b) {
        return Lists.newArrayList(this.getShape(w, pos));
    }

    @Override
    public void addCollidingBlockToList(final World w, final BlockPos pos, final AxisAlignedBB bb, final List<AxisAlignedBB> out, final Entity e) {
        out.add(this.getShape(w, pos));
    }

    /**
     * A pillar standing on the face the block was placed against: two pixels in on the sides, and one pixel
     * short of the far face, whichever way it points.
     */
    private AxisAlignedBB getShape(final World w, final BlockPos pos) {
        final TileCrystalResonanceGenerator tile = this.getTileEntity(w, pos);
        final EnumFacing up = tile == null ? EnumFacing.UP : tile.getUp();
        final AEAxisAlignedBB bb = new AEAxisAlignedBB(TWO_PIXELS, TWO_PIXELS, TWO_PIXELS,
                1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS);

        switch (up) {
            case UP:
                bb.minY = 0;
                bb.maxY = 1.0 - ONE_PIXEL;
                break;
            case DOWN:
                bb.minY = ONE_PIXEL;
                bb.maxY = 1.0;
                break;
            case SOUTH:
                bb.minZ = 0;
                bb.maxZ = 1.0 - ONE_PIXEL;
                break;
            case NORTH:
                bb.minZ = ONE_PIXEL;
                bb.maxZ = 1.0;
                break;
            case EAST:
                bb.minX = 0;
                bb.maxX = 1.0 - ONE_PIXEL;
                break;
            case WEST:
                bb.minX = ONE_PIXEL;
                bb.maxX = 1.0;
                break;
        }

        return bb.getBoundingBox();
    }
}

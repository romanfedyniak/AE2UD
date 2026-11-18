/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.client.render.cablebus;


import appeng.api.parts.IPartBakedModel;
import appeng.api.parts.IPartModel;
import appeng.api.parts.cable.CableStyles;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.block.networking.BlockCableBus;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceArray;


public class CableBusBakedModel implements IBakedModel {

    /** One builder per cable style; which one draws a bus follows from its cable. */
    private final Map<ResourceLocation, CableBuilder> cableBuilders;

    private final FacadeBuilder facadeBuilder;

    private final Map<ResourceLocation, IBakedModel> partModels;

    private final TextureAtlasSprite particleTexture;

    private final TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();

    private final Cache<CableQuadsKey, List<BakedQuad>> cableModelCache;

    /**
     * Part quads turned to the side a part sits on, by the list the part model handed out (weak keys compare by
     * identity): a model that hands out the same list every time is turned once per side, not on every rebuild.
     */
    private final Cache<List<BakedQuad>, AtomicReferenceArray<List<BakedQuad>>> rotatedPartQuads = CacheBuilder.newBuilder()
            .weakKeys()
            .build();

    CableBusBakedModel(Map<ResourceLocation, CableBuilder> cableBuilders, FacadeBuilder facadeBuilder, Map<ResourceLocation, IBakedModel> partModels, TextureAtlasSprite particleTexture) {
        this.cableBuilders = cableBuilders;
        this.facadeBuilder = facadeBuilder;
        this.partModels = partModels;
        this.particleTexture = particleTexture;
        this.cableModelCache = CacheBuilder.newBuilder()
                .maximumWeight(5000)
                .weigher((Weigher<CableQuadsKey, List<BakedQuad>>) (k, v) -> v.size())
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        CableBusRenderState renderState = getRenderingState(state);

        if (renderState == null || side != null) {
            return Collections.emptyList();
        }

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();

        List<BakedQuad> quads = new ArrayList<>();

        // The core parts of the cable will only be rendered in the CUTOUT layer.
        // Facades will add them selves to what ever the block would be rendered with,
        // except when transparent facades are enabled, they are forced to TRANSPARENT.
        if (layer == BlockRenderLayer.CUTOUT) {

            // First, handle the cable at the center of the cable bus
            quads.addAll(this.cableQuads(renderState));

            // Then handle attachments
            for (EnumFacing facing : EnumFacing.values()) {
                final IPartModel partModel = renderState.getAttachments().get(facing);
                if (partModel == null) {
                    continue;
                }

                for (ResourceLocation model : partModel.getModels()) {
                    IBakedModel bakedModel = this.partModels.get(model);

                    if (bakedModel == null) {
                        throw new IllegalStateException("Trying to use an unregistered part model: " + model);
                    }

                    List<BakedQuad> partQuads;
                    if (bakedModel instanceof IPartBakedModel) {
                        partQuads = ((IPartBakedModel) bakedModel).getPartQuads(renderState.getPartFlags().get(facing), rand);
                    } else {
                        partQuads = bakedModel.getQuads(state, null, rand);
                    }

                    quads.addAll(this.rotated(partQuads, facing));
                }
            }
        }
        this.facadeBuilder.buildFacadeQuads(layer, renderState, rand, quads, this.partModels::get);

        return quads;
    }

    private List<BakedQuad> cableQuads(final CableBusRenderState renderState) {
        try {
            return this.cableModelCache.get(new CableQuadsKey(renderState), () -> {
                final List<BakedQuad> model = new ArrayList<>();
                this.addCableQuads(renderState, model);
                return model;
            });
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e.getCause());
        }
    }

    private List<BakedQuad> rotated(final List<BakedQuad> quads, final EnumFacing facing) {
        AtomicReferenceArray<List<BakedQuad>> bySide = this.rotatedPartQuads.getIfPresent(quads);
        if (bySide == null) {
            bySide = new AtomicReferenceArray<>(EnumFacing.values().length);
            this.rotatedPartQuads.put(quads, bySide);
        }

        List<BakedQuad> result = bySide.get(facing.ordinal());
        if (result == null) {
            result = new QuadRotator().rotateQuads(quads, facing, EnumFacing.UP);
            bySide.set(facing.ordinal(), result);
        }
        return result;
    }

    /**
     * What the cable's own quads are made from, and nothing else: the render state also carries raw channel
     * counts and part flags, which would split alike cables into entries of their own.
     */
    private static final class CableQuadsKey {
        private final AECableType cableType;
        private final CableCoreType coreType;
        private final AEColor cableColor;
        private final ResourceLocation cableStyle;
        private final EnumMap<EnumFacing, AECableType> connectionTypes;
        private final EnumSet<EnumFacing> cableBusAdjacent;
        private final EnumMap<EnumFacing, Integer> attachmentConnections;
        private final byte[] channelMarks = new byte[6];
        private final boolean attachmentsNeedCable;
        private final int hash;

        CableQuadsKey(final CableBusRenderState renderState) {
            this.cableType = renderState.getCableType();
            this.coreType = renderState.getCoreType();
            this.cableColor = renderState.getCableColor();
            this.cableStyle = renderState.getCableStyle();
            this.connectionTypes = renderState.getConnectionTypes();
            this.cableBusAdjacent = renderState.getCableBusAdjacent();
            this.attachmentConnections = renderState.getAttachmentConnections();
            this.attachmentsNeedCable = renderState.getAttachments().values().stream().anyMatch(IPartModel::requireCableConnection);
            if (this.cableType == AECableType.SMART || this.cableType == AECableType.DENSE_SMART) {
                for (final EnumFacing facing : renderState.getChannelsOnSide().keySet()) {
                    this.channelMarks[facing.ordinal()] = (byte) channelMarks(renderState, facing);
                }
            }
            this.hash = Objects.hash(this.cableType, this.coreType, this.cableColor, this.cableStyle, this.connectionTypes,
                    this.cableBusAdjacent, this.attachmentConnections, Arrays.hashCode(this.channelMarks), this.attachmentsNeedCable);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CableQuadsKey)) {
                return false;
            }
            final CableQuadsKey other = (CableQuadsKey) o;
            return this.hash == other.hash && this.cableType == other.cableType && this.coreType == other.coreType
                    && this.cableColor == other.cableColor && this.attachmentsNeedCable == other.attachmentsNeedCable
                    && Objects.equals(this.cableStyle, other.cableStyle) && Objects.equals(this.connectionTypes, other.connectionTypes)
                    && Objects.equals(this.cableBusAdjacent, other.cableBusAdjacent)
                    && Objects.equals(this.attachmentConnections, other.attachmentConnections)
                    && Arrays.equals(this.channelMarks, other.channelMarks);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    // Determines whether a cable is connected to exactly two sides that are opposite each other
    private static boolean isStraightLine(AECableType cableType, EnumMap<EnumFacing, AECableType> sides) {
        final Iterator<Entry<EnumFacing, AECableType>> it = sides.entrySet().iterator();
        if (!it.hasNext()) {
            return false; // No connections
        }

        final Entry<EnumFacing, AECableType> nextConnection = it.next();
        final EnumFacing firstSide = nextConnection.getKey();
        final AECableType firstType = nextConnection.getValue();

        if (!it.hasNext()) {
            return false; // Only a single connection
        }
        if (firstSide.getOpposite() != it.next().getKey()) {
            return false; // Connected to two sides that are not opposite each other
        }
        if (it.hasNext()) {
            return false; // Must not have any other connection points
        }

        final AECableType secondType = sides.get(firstSide.getOpposite());

        return firstType == secondType && cableType == firstType && cableType == secondType;
    }

    /**
     * A smart cable draws eight marks whatever it carries, so the count is scaled into those eight rather
     * than shown one for one. Rounded up, so a single channel always lights something.
     */
    private static int channelMarks(final CableBusRenderState renderState, final EnumFacing facing) {
        final int channels = renderState.getChannelsOnSide().get(facing);
        final int capacity = renderState.getChannelCapacity();
        if (channels <= 0 || capacity <= 0) {
            return 0;
        }

        return Math.min(8, (channels * 8 + capacity - 1) / capacity);
    }

    /** The builder of that bus's style, or AE2's own where an addon's style is not on this client. */
    private CableBuilder builderFor(CableBusRenderState renderState) {
        final CableBuilder builder = this.cableBuilders.get(renderState.getCableStyle());
        return builder == null ? this.cableBuilders.get(CableStyles.DEFAULT) : builder;
    }

    private void addCableQuads(CableBusRenderState renderState, List<BakedQuad> quadsOut) {
        final CableBuilder cableBuilder = this.builderFor(renderState);
        AECableType cableType = renderState.getCableType();
        if (cableType == AECableType.NONE) {
            return;
        }

        AEColor cableColor = renderState.getCableColor();
        EnumMap<EnumFacing, AECableType> connectionTypes = renderState.getConnectionTypes();

        // If the connection is straight, no busses are attached, and no covered core has been forced (in case of glass
        // cables), then render the cable as a simplified straight line.
        boolean noAttachments = !renderState.getAttachments().values().stream().anyMatch(IPartModel::requireCableConnection);
        if (noAttachments && isStraightLine(cableType, connectionTypes)) {
            EnumFacing facing = connectionTypes.keySet().iterator().next();

            switch (cableType) {
                case GLASS:
                    cableBuilder.addStraightGlassConnection(facing, cableColor, quadsOut);
                    break;
                case COVERED:
                    cableBuilder.addStraightCoveredConnection(facing, cableColor, quadsOut);
                    break;
                case SMART:
                    cableBuilder.addStraightSmartConnection(facing, cableColor, channelMarks(renderState, facing), quadsOut);
                    break;
                case DENSE_COVERED:
                    cableBuilder.addStraightDenseCoveredConnection(facing, cableColor, quadsOut);
                    break;
                case DENSE_SMART:
                    cableBuilder.addStraightDenseSmartConnection(facing, cableColor, channelMarks(renderState, facing), quadsOut);
                    break;
                default:
                    break;
            }

            return; // Don't render the other form of connection
        }

        cableBuilder.addCableCore(renderState.getCoreType(), cableColor, quadsOut);

        // Render all internal connections to attachments
        EnumMap<EnumFacing, Integer> attachmentConnections = renderState.getAttachmentConnections();
        for (EnumFacing facing : attachmentConnections.keySet()) {
            int distance = attachmentConnections.get(facing);
            int channels = renderState.getChannelsOnSide().get(facing);

            switch (cableType) {
                case GLASS:
                    cableBuilder.addConstrainedGlassConnection(facing, cableColor, distance, quadsOut);
                    break;
                case COVERED:
                    cableBuilder.addConstrainedCoveredConnection(facing, cableColor, distance, quadsOut);
                    break;
                case SMART:
                    cableBuilder.addConstrainedSmartConnection(facing, cableColor, distance, channelMarks(renderState, facing), quadsOut);
                    break;
                case DENSE_COVERED:
                case DENSE_SMART:
                    // Dense cables do not render connections to parts since none can be attached
                    break;
                default:
                    break;
            }
        }

        // Render all outgoing connections using the appropriate type
        for (final Entry<EnumFacing, AECableType> connection : connectionTypes.entrySet()) {
            final EnumFacing facing = connection.getKey();
            final AECableType connectionType = connection.getValue();
            final boolean cableBusAdjacent = renderState.getCableBusAdjacent().contains(facing);
            final int channels = renderState.getChannelsOnSide().get(facing);

            switch (cableType) {
                case GLASS:
                    cableBuilder.addGlassConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
                    break;
                case COVERED:
                    cableBuilder.addCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
                    break;
                case SMART:
                    cableBuilder.addSmartConnection(facing, cableColor, connectionType, cableBusAdjacent, channelMarks(renderState, facing), quadsOut);
                    break;
                case DENSE_COVERED:
                    cableBuilder.addDenseCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
                    break;
                case DENSE_SMART:
                    cableBuilder.addDenseSmartConnection(facing, cableColor, connectionType, cableBusAdjacent, channelMarks(renderState, facing), quadsOut);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Gets a list of texture sprites appropriate for particles (digging, etc.) given the render state for a cable bus.
     */
    public List<TextureAtlasSprite> getParticleTextures(CableBusRenderState renderState) {
        CableCoreType coreType = CableCoreType.fromCableType(renderState.getCableType());
        AEColor cableColor = renderState.getCableColor();

        List<TextureAtlasSprite> result = new ArrayList<>();

        if (coreType != null) {
            result.add(this.builderFor(renderState).getCoreTexture(coreType, cableColor));
        }

        // If no core is present, just use the first part that comes into play
        for (EnumFacing side : renderState.getAttachments().keySet()) {
            IPartModel partModel = renderState.getAttachments().get(side);

            for (ResourceLocation model : partModel.getModels()) {
                IBakedModel bakedModel = this.partModels.get(model);

                if (bakedModel == null) {
                    throw new IllegalStateException("Trying to use an unregistered part model: " + model);
                }

                TextureAtlasSprite particleTexture = bakedModel.getParticleTexture();

                // If a part sub-model has no particle texture (indicated by it being the missing texture),
                // don't add it, so we don't get ugly missing texture break particles.
                if (this.textureMap.getMissingSprite() != particleTexture) {
                    result.add(particleTexture);
                }
            }
        }

        return result;
    }

    private static CableBusRenderState getRenderingState(IBlockState state) {
        if (state == null || !(state instanceof IExtendedBlockState)) {
            return null;
        }

        IExtendedBlockState extendedBlockState = (IExtendedBlockState) state;
        return extendedBlockState.getValue(BlockCableBus.RENDER_STATE_PROPERTY);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.particleTexture;
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}

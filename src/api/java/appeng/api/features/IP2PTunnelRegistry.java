/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 - 2015 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.features;


import appeng.api.AEApi;
import appeng.api.config.TunnelType;
import appeng.api.parts.P2PTunnelModels;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * A Registry for how p2p Tunnels are attuned
 */
public interface IP2PTunnelRegistry
{

	/**
	 * Allows third parties to register items from their mod as potential
	 * attunements for AE's P2P Tunnels
	 *
	 * @param trigger - the item which triggers attunement. Nullable, but then ignored
	 * @param type - the type of tunnel. Nullable, but then ignored
	 */
	void addNewAttunement( @Nonnull ItemStack trigger, @Nullable TunnelType type );

	void addNewAttunement( @Nonnull String ModId, @Nullable TunnelType type );

	void addNewAttunement( @Nonnull Capability<?> cap, @Nullable TunnelType type );

	/**
	 * returns null if no attunement can be found.
	 *
	 * @param trigger attunement trigger
	 *
	 * @return null if no attunement can be found or attunement
	 */
	@Nullable
	TunnelType getTunnelTypeByItem( ItemStack trigger );

	TunnelType registerTunnelType( @Nonnull String enumName, @Nonnull ItemStack partStack );

	/**
	 * Registers a tunnel type together with the models its part is drawn with, which a tunnel of an addon's
	 * otherwise has to remember to register separately. Call it during pre-initialisation: part models
	 * registered later are never baked. Attunements are still added with {@link #addNewAttunement}.
	 */
	default TunnelType registerTunnelType( @Nonnull String enumName, @Nonnull ItemStack partStack, @Nonnull P2PTunnelModels models )
	{
		AEApi.instance().registries().partModels().registerModels( models.getModelLocations() );
		return this.registerTunnelType( enumName, partStack );
	}
}
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2UD contributors
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

package appeng.api.migration;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Rewrites a part on a cable bus saved by UEL, before the bus is read. It sees the part's item as it was saved, old
 * id and metadata included, because a part's own data often depends on which of the old parts it was. The item
 * itself is renamed and rewritten afterwards, as every saved stack is.
 */
@FunctionalInterface
public interface PartRewriter {

    /**
     * @param item  the part's item stack NBT, {@code def:N}
     * @param part  the part's own data, {@code extra:N}, changed in place
     */
    void rewrite(NBTTagCompound item, NBTTagCompound part);
}

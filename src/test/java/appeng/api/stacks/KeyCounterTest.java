package appeng.api.stacks;


import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public final class KeyCounterTest {

    private static final AEKey KEY = new TestKey();

    @Test
    public void additionSaturatesAtLongMaximum() {
        final KeyCounter counter = new KeyCounter();
        counter.set(KEY, Long.MAX_VALUE - 1);

        counter.add(KEY, 2);

        assertThat(counter.get(KEY), is(Long.MAX_VALUE));
    }

    @Test
    public void additionSaturatesAtLongMinimum() {
        final KeyCounter counter = new KeyCounter();
        counter.set(KEY, Long.MIN_VALUE + 1);

        counter.add(KEY, -2);

        assertThat(counter.get(KEY), is(Long.MIN_VALUE));
    }

    private static final class TestKey extends AEKey {

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("test", "key");
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public void toTag(final NBTTagCompound tag) {
        }

        @Override
        public void writeToPacket(final ByteBuf data) throws IOException {
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString("Test Key");
        }

        @Override
        public void addDrops(final long amount, final List<ItemStack> drops, @Nonnull final World world,
                @Nonnull final BlockPos pos) {
        }
    }
}

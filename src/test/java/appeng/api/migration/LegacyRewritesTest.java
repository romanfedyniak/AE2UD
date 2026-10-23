/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.api.migration;


import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;


public final class LegacyRewritesTest {

    private final Log log = new Log();

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.register();
        LegacyRewrites.remapItem("legacytest:renamed", "minecraft:stick");
        LegacyRewrites.remove("legacytest:gone");
        LegacyRewrites.addStackRewriter("legacytest:reshaped", stack -> {
            stack.setString("id", "minecraft:dye");
            stack.setShort("Damage", (short) 4);
        });
        LegacyRewrites.remapItem("legacytest:broken", "minecraft:stone");
        LegacyRewrites.addStackRewriter("legacytest:broken", stack -> {
            throw new IllegalStateException("bad shape");
        });
        LegacyRewrites.addTileRewriter("legacytest:machine", tile -> {
            tile.setString("id", "minecraft:chest");
            return tile;
        });
        LegacyRewrites.addTileRewriter("legacytest:broken_machine", tile -> {
            throw new IllegalStateException("bad shape");
        });
    }

    @AfterEach
    public void stop() {
        LegacyRewrites.stopWorld();
    }

    @Test
    public void nothingHappensOffALegacyWorld() {
        final NBTTagCompound stack = stack("legacytest:renamed");
        LegacyRewrites.rewriteStack(stack);
        assertThat(stack.getString("id"), is("legacytest:renamed"));

        final NBTTagCompound tile = tile("legacytest:machine");
        assertThat(LegacyRewrites.rewriteTile(tile).getString("id"), is("legacytest:machine"));
    }

    @Test
    public void renamedStackTakesItsNewId() {
        LegacyRewrites.startWorld(this.log);
        final NBTTagCompound stack = stack("legacytest:renamed");
        stack.setShort("Damage", (short) 3);
        LegacyRewrites.rewriteStack(stack);

        assertThat(stack.getString("id"), is("minecraft:stick"));
        assertThat(stack.getShort("Damage"), is((short) 3));
        assertThat(this.log.lines, empty());
    }

    @Test
    public void rewriterReshapesAStack() {
        LegacyRewrites.startWorld(this.log);
        final NBTTagCompound stack = stack("legacytest:reshaped");
        LegacyRewrites.rewriteStack(stack);

        assertThat(stack.getString("id"), is("minecraft:dye"));
        assertThat(stack.getShort("Damage"), is((short) 4));
    }

    @Test
    public void removedStackIsLoggedAndGone() {
        LegacyRewrites.startWorld(this.log);
        final NBTTagCompound stack = stack("legacytest:gone");
        LegacyRewrites.rewriteStack(stack);

        assertThat(stack.getString("id"), is("minecraft:air"));
        assertThat(stack.getByte("Count"), is((byte) 0));
        assertThat(this.log.lines, hasSize(1));
        assertThat(this.log.lines.get(0).startsWith("LOST item {"), is(true));
    }

    @Test
    public void failedStackRewriteKeepsTheOriginalInTheLog() {
        LegacyRewrites.startWorld(this.log);
        final NBTTagCompound stack = stack("legacytest:broken");
        stack.setString("Secret", "kept");
        LegacyRewrites.rewriteStack(stack);

        assertThat(stack.getString("id"), is("minecraft:air"));
        assertThat(this.log.originals, hasSize(1));
        assertThat(this.log.originals.get(0).getString("id"), is("legacytest:broken"));
        assertThat(this.log.originals.get(0).getString("Secret"), is("kept"));
    }

    @Test
    public void tileRewriterRuns() {
        LegacyRewrites.startWorld(this.log);
        assertThat(LegacyRewrites.rewriteTile(tile("legacytest:machine")).getString("id"), is("minecraft:chest"));
    }

    @Test
    public void failedTileRewriteLeavesABareTile() {
        LegacyRewrites.startWorld(this.log);
        final NBTTagCompound tile = tile("legacytest:broken_machine");
        tile.setString("Items", "lots");
        final NBTTagCompound result = LegacyRewrites.rewriteTile(tile);

        assertThat(new ArrayList<>(result.getKeySet()).size(), is(4));
        assertThat(result.getInteger("y"), is(64));
        assertThat(this.log.originals.get(0).getString("Items"), is("lots"));
        assertThat(this.log.lines, contains("FAILED tile legacytest:broken_machine at 1 64 -2"));
    }

    static NBTTagCompound stack(final String id) {
        final NBTTagCompound stack = new NBTTagCompound();
        stack.setString("id", id);
        stack.setByte("Count", (byte) 1);
        return stack;
    }

    private static NBTTagCompound tile(final String id) {
        final NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", id);
        tile.setInteger("x", 1);
        tile.setInteger("y", 64);
        tile.setInteger("z", -2);
        return tile;
    }

    static final class Log implements MigrationLog {
        final List<String> lines = new ArrayList<>();
        final List<NBTTagCompound> originals = new ArrayList<>();

        @Override
        public void note(final String message) {
            this.lines.add("NOTE " + message);
        }

        @Override
        public void lost(final String message) {
            this.lines.add("LOST " + message);
        }

        @Override
        public void failed(final String what, final NBTTagCompound original, final Throwable error) {
            this.lines.add("FAILED " + what);
            this.originals.add(original);
        }
    }
}

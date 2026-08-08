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

package appeng.crafting.tree;


import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.tree.CraftingPlanSource.Kind;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/**
 * The crafting plan as a tree: what is needed, and for each of those, how it is obtained. Built on the
 * server from what the solver already worked out, then sent to whoever asked to look at it.
 * <p>
 * Building and reading are iterative on purpose. A deep plan is exactly the case this screen exists for,
 * and recursion over one would run out of stack before it ran out of anything else.
 */
public final class CraftingPlanTree {

    /**
     * Guards against a corrupt or hostile packet rather than against a big plan - a real tree is allowed to
     * be enormous, which is why the payload is compressed instead of trimmed.
     */
    private static final int MAX_NODES = 1_000_000;
    private static final int MAX_DEPTH = 256;
    private static final int MAX_CHILDREN = 4096;
    private static final int MAX_KEYS = 65_536;

    private final CraftingPlanNode root;

    public CraftingPlanTree(final CraftingPlanNode root) {
        this.root = root;
    }

    public CraftingPlanNode getRoot() {
        return this.root;
    }

    /**
     * @return null when the job never produced a tree, which happens if it failed before it started.
     */
    @Nullable
    public static CraftingPlanTree of(final CraftingJob job) {
        final CraftingTreeNode solverRoot = job.getTree();
        if (solverRoot == null) {
            return null;
        }

        final GenericStack output = job.getOutput();
        final CraftingPlanNode root = new CraftingPlanNode(solverRoot.getWhat(), output.amount());

        final Deque<Pending> pending = new ArrayDeque<>();
        pending.push(new Pending(solverRoot, root));

        while (!pending.isEmpty()) {
            final Pending current = pending.pop();
            addSources(current.solverNode, current.planNode, pending, job.getCraftingGrid());
        }

        return new CraftingPlanTree(root);
    }

    private static void addSources(final CraftingTreeNode solverNode, final CraftingPlanNode planNode,
            final Deque<Pending> pending, final ICraftingGrid grid) {
        for (final var entry : solverNode.getUsed()) {
            if (entry.getLongValue() > 0) {
                planNode.getSources().add(
                        new CraftingPlanSource(Kind.STORAGE, entry.getKey(), entry.getLongValue(), 0));
            }
        }

        if (solverNode.getEmitted() > 0) {
            planNode.getSources().add(
                    new CraftingPlanSource(Kind.EMITTER, solverNode.getWhat(), solverNode.getEmitted(), 0));
        }

        if (solverNode.getMissing() > 0) {
            planNode.getSources().add(
                    new CraftingPlanSource(Kind.MISSING, solverNode.getWhat(), solverNode.getMissing(), 0));
        }

        for (final CraftingTreeProcess process : solverNode.getProcesses()) {
            final long crafts = process.getCrafts();
            // A pattern that was considered and dropped is not part of the plan, and showing it would
            // describe work that never happens.
            if (crafts <= 0) {
                continue;
            }

            final GenericStack made = process.getAmountCrafted(solverNode.getWhat());
            final long produced = made == null ? 0 : made.amount() * crafts;
            final CraftingPlanSource source =
                    new CraftingPlanSource(Kind.CRAFT, solverNode.getWhat(), produced, crafts);
            source.setMachine(machineFor(grid, process.getDetails()));
            planNode.getSources().add(source);

            for (final Object2LongMap.Entry<CraftingTreeNode> input : process.getInputs().object2LongEntrySet()) {
                final CraftingTreeNode child = input.getKey();
                final CraftingPlanNode childPlan =
                        new CraftingPlanNode(child.getWhat(), input.getLongValue() * crafts);
                source.getInputs().add(childPlan);
                pending.push(new Pending(child, childPlan));
            }
        }
    }

    /**
     * The first machine that would take this pattern - the one a player would go looking for.
     */
    @Nullable
    private static AEKey machineFor(final ICraftingGrid grid, final ICraftingPatternDetails pattern) {
        for (final ICraftingMedium medium : grid.getMediums(pattern)) {
            final ItemStack icon = medium.getMachineIdentity().getIcon();
            if (!icon.isEmpty()) {
                return AEItemKey.of(icon);
            }
        }
        return null;
    }

    public void write(final ByteBuf buf) throws IOException {
        final Object2IntMap<AEKey> keyIds = new Object2IntOpenHashMap<>();
        keyIds.defaultReturnValue(-1);
        final List<AEKey> keys = new ArrayList<>();

        final ByteBuf body = buf.alloc().buffer();
        try {
            final Deque<Object> stack = new ArrayDeque<>();
            stack.push(this.root);

            while (!stack.isEmpty()) {
                final Object next = stack.pop();

                if (next instanceof CraftingPlanNode node) {
                    body.writeInt(keyId(keyIds, keys, node.getWhat()));
                    body.writeLong(node.getAmount());
                    body.writeInt(node.getSources().size());
                    pushReversed(stack, node.getSources());
                } else {
                    final CraftingPlanSource source = (CraftingPlanSource) next;
                    body.writeByte(source.getKind().ordinal());
                    body.writeInt(keyId(keyIds, keys, source.getWhat()));
                    body.writeLong(source.getAmount());
                    body.writeLong(source.getCrafts());
                    body.writeInt(source.getMachine() == null ? -1 : keyId(keyIds, keys, source.getMachine()));
                    body.writeInt(source.getInputs().size());
                    pushReversed(stack, source.getInputs());
                }
            }

            buf.writeInt(keys.size());
            for (final AEKey key : keys) {
                AEKey.writeKey(buf, key);
            }
            buf.writeBytes(body);
        } finally {
            body.release();
        }
    }

    public static CraftingPlanTree read(final ByteBuf buf) throws IOException {
        final int keyCount = buf.readInt();
        if (keyCount < 0 || keyCount > MAX_KEYS) {
            throw new IOException("Crafting plan tree declares " + keyCount + " keys");
        }
        final AEKey[] keys = new AEKey[keyCount];
        for (int i = 0; i < keyCount; i++) {
            keys[i] = AEKey.readKey(buf);
        }

        final Counter nodes = new Counter();
        final Frame rootFrame = readNode(buf, keys, nodes);

        final Deque<Frame> stack = new ArrayDeque<>();
        pushFrame(stack, rootFrame);

        while (!stack.isEmpty()) {
            final Frame frame = stack.peek();
            if (frame.remaining == 0) {
                stack.pop();
                continue;
            }
            frame.remaining--;

            if (frame.node != null) {
                final Frame child = readSource(buf, keys);
                frame.node.getSources().add(child.source);
                pushFrame(stack, child);
            } else {
                final Frame child = readNode(buf, keys, nodes);
                frame.source.getInputs().add(child.node);
                pushFrame(stack, child);
            }
        }

        return new CraftingPlanTree(rootFrame.node);
    }

    /**
     * The declared child count is stored on the frame and the list is filled as children arrive, so an empty
     * frame is never pushed and the depth limit counts real nesting.
     */
    private static void pushFrame(final Deque<Frame> stack, final Frame frame) throws IOException {
        if (frame.remaining == 0) {
            return;
        }
        if (stack.size() >= MAX_DEPTH) {
            throw new IOException("Crafting plan tree is deeper than " + MAX_DEPTH);
        }
        stack.push(frame);
    }

    private static Frame readNode(final ByteBuf buf, final AEKey[] keys, final Counter nodes) throws IOException {
        if (++nodes.value > MAX_NODES) {
            throw new IOException("Crafting plan tree holds more than " + MAX_NODES + " nodes");
        }

        final AEKey what = key(keys, buf.readInt());
        final long amount = buf.readLong();
        final int sources = childCount(buf.readInt());

        return new Frame(new CraftingPlanNode(what, amount), null, sources);
    }

    private static Frame readSource(final ByteBuf buf, final AEKey[] keys) throws IOException {
        final int kind = buf.readByte();
        final Kind[] kinds = Kind.values();
        if (kind < 0 || kind >= kinds.length) {
            throw new IOException("Crafting plan tree names an unknown source kind " + kind);
        }

        final AEKey what = key(keys, buf.readInt());
        final long amount = buf.readLong();
        final long crafts = buf.readLong();
        final int machineId = buf.readInt();
        final int inputs = childCount(buf.readInt());

        final CraftingPlanSource source = new CraftingPlanSource(kinds[kind], what, amount, crafts);
        if (machineId >= 0) {
            source.setMachine(key(keys, machineId));
        }
        return new Frame(null, source, inputs);
    }

    private static int childCount(final int count) throws IOException {
        if (count < 0 || count > MAX_CHILDREN) {
            throw new IOException("Crafting plan tree declares " + count + " children for one entry");
        }
        return count;
    }

    private static AEKey key(final AEKey[] keys, final int id) throws IOException {
        if (id < 0 || id >= keys.length) {
            throw new IOException("Crafting plan tree names key " + id + " of " + keys.length);
        }
        return keys[id];
    }

    private static int keyId(final Object2IntMap<AEKey> keyIds, final List<AEKey> keys, final AEKey key) {
        int id = keyIds.getInt(key);
        if (id == -1) {
            id = keys.size();
            keys.add(key);
            keyIds.put(key, id);
        }
        return id;
    }

    private static void pushReversed(final Deque<Object> stack, final List<?> children) {
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(children.get(i));
        }
    }

    private static final class Pending {
        private final CraftingTreeNode solverNode;
        private final CraftingPlanNode planNode;

        private Pending(final CraftingTreeNode solverNode, final CraftingPlanNode planNode) {
            this.solverNode = solverNode;
            this.planNode = planNode;
        }
    }

    private static final class Frame {
        private final CraftingPlanNode node;
        private final CraftingPlanSource source;
        private int remaining;

        private Frame(final CraftingPlanNode node, final CraftingPlanSource source, final int remaining) {
            this.node = node;
            this.source = source;
            this.remaining = remaining;
        }
    }

    private static final class Counter {
        private int value;
    }
}

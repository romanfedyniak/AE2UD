/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026 AE2UD contributors
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package appeng.crafting.solver;


import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Which keys the request can possibly reach, and an order to decide them in.
 * <p>
 * The graph is over keys rather than over requests for keys, which is the whole reason a solve does not
 * depend on how much was ordered: one node per thing, however many times it is wanted.
 * <p>
 * An edge means "settle this one first". Two kinds are drawn, and the second is easy to miss: a key points
 * at every ingredient of every pattern that could make it, and also at every <em>other</em> output of those
 * patterns. Without that second kind a byproduct could be decided before the pattern that would have given
 * it away for nothing, and be crafted separately.
 */
public final class SolverGraph {

    private final Map<AEKey, List<SolverPattern>> patterns;
    private final Map<AEKey, Set<AEKey>> edges;
    private final List<AEKey> order;
    private final Set<AEKey> cyclic;
    /**
     * Keys some ingredient can be satisfied by and nothing else. Scarce stock goes to these first, because a
     * slot that would take an alternative can be sent to one instead - see {@code CraftingSolver}.
     */
    private final Set<AEKey> exclusive;
    private final Set<AEKey> emitted;

    private SolverGraph(final Map<AEKey, List<SolverPattern>> patterns, final Map<AEKey, Set<AEKey>> edges,
            final List<AEKey> order, final Set<AEKey> cyclic, final Set<AEKey> exclusive,
            final Set<AEKey> emitted) {
        this.patterns = patterns;
        this.edges = edges;
        this.order = Collections.unmodifiableList(order);
        this.cyclic = Collections.unmodifiableSet(cyclic);
        this.exclusive = Collections.unmodifiableSet(exclusive);
        this.emitted = Collections.unmodifiableSet(emitted);
    }

    public static SolverGraph of(final AEKey root, final ICraftingSource source, final SolverLimits limits) {
        final Map<AEKey, List<SolverPattern>> patterns = new LinkedHashMap<>();
        final Map<AEKey, Set<AEKey>> edges = new LinkedHashMap<>();
        final Set<AEKey> exclusive = new LinkedHashSet<>();
        final Set<AEKey> emitted = new LinkedHashSet<>();
        final Deque<AEKey> pending = new ArrayDeque<>();
        int edgeCount = 0;

        pending.add(root);
        patterns.put(root, null);

        while (!pending.isEmpty()) {
            final AEKey key = pending.poll();

            // An emitter settles a key outright, so its patterns are not part of the problem at all.
            final boolean promised = source.canEmit(key);

            if (promised) {
                emitted.add(key);
            }

            final List<SolverPattern> made = promised
                    ? Collections.<SolverPattern>emptyList()
                    : source.patternsFor(key);

            patterns.put(key, made);
            final Set<AEKey> out = edges.computeIfAbsent(key, k -> new LinkedHashSet<>());

            for (final SolverPattern pattern : made) {
                for (final SolverIngredient ingredient : pattern.getInputs()) {
                    if (!ingredient.hasChoice()) {
                        exclusive.add(ingredient.getOptions().get(0).what());
                    }

                    for (final GenericStack option : ingredient.getOptions()) {
                        edgeCount += visit(key, option.what(), out, patterns, pending);
                    }
                }

                // A byproduct is settled after the output the pattern was chosen for, so it can be found
                // already made rather than crafted again. Drawn from the primary output only: from every
                // output would have two of them pointing at each other, which is a cycle that is not there.
                final AEKey primary = pattern.getOutputs().get(0).what();
                final Set<AEKey> fromPrimary = primary.equals(key)
                        ? out
                        : edges.computeIfAbsent(primary, k -> new LinkedHashSet<>());

                for (final GenericStack produced : pattern.getOutputs()) {
                    if (!produced.what().equals(primary)) {
                        edgeCount += visit(primary, produced.what(), fromPrimary, patterns, pending);
                    }
                }
            }

            if (patterns.size() > limits.getMaxNodes()) {
                throw new SolverTooLargeException("More than " + limits.getMaxNodes()
                        + " things can be reached from this request");
            }

            if (edgeCount > limits.getMaxEdges()) {
                throw new SolverTooLargeException("More than " + limits.getMaxEdges()
                        + " connections between them");
            }
        }

        final List<AEKey> order = new ArrayList<>(patterns.size());
        final Set<AEKey> cyclic = kahn(patterns.keySet(), edges, order);

        return new SolverGraph(patterns, edges, order, cyclic, exclusive, emitted);
    }

    private static int visit(final AEKey from, final AEKey to, final Set<AEKey> out,
            final Map<AEKey, List<SolverPattern>> patterns, final Deque<AEKey> pending) {
        // A pattern eating what it makes gets an edge to itself on purpose: Kahn then cannot place the node,
        // which is precisely the answer wanted - a cycle of one is still a cycle.
        if (!patterns.containsKey(to)) {
            patterns.put(to, null);
            pending.add(to);
        }

        return out.add(to) ? 1 : 0;
    }

    /**
     * Parents before dependencies. Whatever is left over is in a cycle, and is reported rather than ordered.
     */
    private static Set<AEKey> kahn(final Set<AEKey> nodes, final Map<AEKey, Set<AEKey>> edges,
            final List<AEKey> order) {
        final Map<AEKey, Integer> incoming = new LinkedHashMap<>();

        for (final AEKey node : nodes) {
            incoming.putIfAbsent(node, 0);
        }

        for (final Set<AEKey> targets : edges.values()) {
            for (final AEKey target : targets) {
                incoming.merge(target, 1, Integer::sum);
            }
        }

        final Deque<AEKey> ready = new ArrayDeque<>();

        for (final Map.Entry<AEKey, Integer> entry : incoming.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        while (!ready.isEmpty()) {
            final AEKey node = ready.poll();
            order.add(node);

            for (final AEKey target : edges.getOrDefault(node, Collections.emptySet())) {
                if (incoming.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }

        if (order.size() == nodes.size()) {
            return Collections.emptySet();
        }

        final Set<AEKey> cyclic = new LinkedHashSet<>(nodes);
        cyclic.removeAll(order);
        return cyclic;
    }

    /**
     * Every key the request can reach, parents before dependencies. A key caught in a cycle is not in here.
     */
    public List<AEKey> getOrder() {
        return this.order;
    }

    /**
     * The keys Kahn could not place, which is exactly the keys in a cycle. Empty on the ordinary path.
     */
    public Set<AEKey> getCyclic() {
        return this.cyclic;
    }

    public List<SolverPattern> patternsFor(final AEKey what) {
        final List<SolverPattern> found = this.patterns.get(what);
        return found == null ? Collections.emptyList() : found;
    }

    public boolean isExclusive(final AEKey what) {
        return this.exclusive.contains(what);
    }

    /**
     * Whether a level emitter promises this key. Asked once while the graph is built, so the solver never
     * has to go back to the network mid-pass.
     */
    public boolean isEmitted(final AEKey what) {
        return this.emitted.contains(what);
    }

    public int size() {
        return this.patterns.size();
    }
}

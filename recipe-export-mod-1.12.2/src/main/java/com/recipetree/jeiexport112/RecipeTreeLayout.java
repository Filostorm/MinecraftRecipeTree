package com.recipetree.jeiexport112;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure top-down forest layout for planner nodes.
 *
 * <p>The caller owns the node model and supplies only node sizes and child relationships. Invalid
 * or unexpectedly large trees are rejected explicitly instead of being partially rendered.</p>
 */
final class RecipeTreeLayout {
    private RecipeTreeLayout() {
    }

    interface Adapter<N> {
        Size size(N node);

        List<N> children(N node);
    }

    static final class Size {
        final int width;
        final int height;

        Size(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Node size must be positive");
            }
            this.width = width;
            this.height = height;
        }
    }

    static final class Limits {
        final int maximumDepth;
        final int maximumChildrenPerNode;
        final int maximumNodes;

        Limits(int maximumDepth, int maximumChildrenPerNode, int maximumNodes) {
            if (maximumDepth < 0) {
                throw new IllegalArgumentException("Maximum depth must be nonnegative");
            }
            if (maximumChildrenPerNode < 0) {
                throw new IllegalArgumentException("Maximum children must be nonnegative");
            }
            if (maximumNodes <= 0) {
                throw new IllegalArgumentException("Maximum nodes must be positive");
            }
            this.maximumDepth = maximumDepth;
            this.maximumChildrenPerNode = maximumChildrenPerNode;
            this.maximumNodes = maximumNodes;
        }
    }

    static final class PlacedNode<N> {
        final N node;
        final int left;
        final int top;
        final int width;
        final int height;
        final int parentIndex;
        final int rootIndex;

        private PlacedNode(
                N node,
                int left,
                int top,
                int width,
                int height,
                int parentIndex,
                int rootIndex) {
            this.node = node;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.parentIndex = parentIndex;
            this.rootIndex = rootIndex;
        }
    }

    static final class Result<N> {
        final List<PlacedNode<N>> nodes;
        final int width;
        final int height;

        private Result(List<PlacedNode<N>> nodes, int width, int height) {
            this.nodes = Collections.unmodifiableList(new ArrayList<PlacedNode<N>>(nodes));
            this.width = width;
            this.height = height;
        }
    }

    static <N> Result<N> layout(
            List<N> roots,
            Adapter<N> adapter,
            int nodeGap,
            int levelGap,
            int rootGap,
            Limits limits) {
        if (roots == null || adapter == null || limits == null) {
            throw new IllegalArgumentException("Roots, adapter, and limits must not be null");
        }
        if (nodeGap < 0 || levelGap < 0 || rootGap < 0) {
            throw new IllegalArgumentException("Layout gaps must be nonnegative");
        }
        if (roots.isEmpty()) {
            return new Result<N>(Collections.<PlacedNode<N>>emptyList(), 1, 1);
        }

        List<Draft<N>> drafts = new ArrayList<Draft<N>>(roots.size());
        Map<N, Boolean> seen = new IdentityHashMap<N, Boolean>();
        MutableCount count = new MutableCount();
        for (N root : roots) {
            if (root == null) {
                throw new IllegalArgumentException("Forest roots must not contain null");
            }
            drafts.add(buildDraft(root, 0, adapter, nodeGap, limits, seen, count));
        }

        List<Integer> depthHeights = new ArrayList<Integer>();
        for (Draft<N> draft : drafts) {
            collectDepthHeights(draft, 0, depthHeights);
        }
        List<Integer> depthTops = new ArrayList<Integer>(depthHeights.size());
        int nextTop = 0;
        for (Integer height : depthHeights) {
            depthTops.add(nextTop);
            nextTop = safeCoordinateAdd(nextTop, safeCoordinateAdd(height.intValue(), levelGap));
        }

        List<PlacedNode<N>> placed = new ArrayList<PlacedNode<N>>(count.value);
        int nextLeft = 0;
        int contentHeight = 1;
        for (int rootIndex = 0; rootIndex < drafts.size(); rootIndex++) {
            List<RawNode<N>> raw = new ArrayList<RawNode<N>>();
            flatten(drafts.get(rootIndex), 0.0, 0, -1, rootIndex, depthTops, raw);
            double minimumLeft = Double.POSITIVE_INFINITY;
            double maximumRight = Double.NEGATIVE_INFINITY;
            for (RawNode<N> node : raw) {
                minimumLeft = Math.min(minimumLeft, node.left);
                maximumRight = Math.max(maximumRight, node.left + node.width);
            }
            int treeWidth = Math.max(1, checkedCeiling(maximumRight - minimumLeft));
            int parentOffset = placed.size();
            for (RawNode<N> node : raw) {
                int left = safeCoordinateAdd(nextLeft, checkedRound(node.left - minimumLeft));
                int parentIndex = node.parentIndex < 0 ? -1 : parentOffset + node.parentIndex;
                placed.add(new PlacedNode<N>(
                        node.node,
                        left,
                        node.top,
                        node.width,
                        node.height,
                        parentIndex,
                        node.rootIndex));
                contentHeight = Math.max(
                        contentHeight,
                        safeCoordinateAdd(node.top, node.height));
            }
            nextLeft = safeCoordinateAdd(nextLeft, treeWidth);
            if (rootIndex + 1 < drafts.size()) {
                nextLeft = safeCoordinateAdd(nextLeft, rootGap);
            }
        }
        return new Result<N>(placed, Math.max(1, nextLeft), contentHeight);
    }

    private static <N> Draft<N> buildDraft(
            N node,
            int depth,
            Adapter<N> adapter,
            int nodeGap,
            Limits limits,
            Map<N, Boolean> seen,
            MutableCount count) {
        if (depth > limits.maximumDepth) {
            throw new IllegalArgumentException(
                    "Recipe tree depth exceeds configured maximum " + limits.maximumDepth);
        }
        if (seen.put(node, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Recipe tree contains a cycle or shared node instance");
        }
        count.value++;
        if (count.value > limits.maximumNodes) {
            throw new IllegalArgumentException(
                    "Recipe tree node count exceeds configured maximum " + limits.maximumNodes);
        }

        Size size = adapter.size(node);
        if (size == null) {
            throw new IllegalArgumentException("Recipe tree adapter returned a null node size");
        }
        List<N> suppliedChildren = adapter.children(node);
        if (suppliedChildren == null) {
            throw new IllegalArgumentException("Recipe tree adapter returned null children");
        }
        if (suppliedChildren.size() > limits.maximumChildrenPerNode) {
            throw new IllegalArgumentException(
                    "Recipe tree node has " + suppliedChildren.size()
                            + " children; configured maximum is " + limits.maximumChildrenPerNode);
        }

        List<Draft<N>> children = new ArrayList<Draft<N>>(suppliedChildren.size());
        for (N child : suppliedChildren) {
            if (child == null) {
                throw new IllegalArgumentException("Recipe tree children must not contain null");
            }
            children.add(buildDraft(
                    child, depth + 1, adapter, nodeGap, limits, seen, count));
        }
        if (children.isEmpty()) {
            return Draft.leaf(node, size);
        }

        List<Double> combinedMinimums = new ArrayList<Double>();
        List<Double> combinedMaximums = new ArrayList<Double>();
        for (Draft<N> child : children) {
            double offset = 0.0;
            if (!combinedMinimums.isEmpty()) {
                int sharedDepths = Math.min(combinedMaximums.size(), child.minimumContour.size());
                for (int contourDepth = 0; contourDepth < sharedDepths; contourDepth++) {
                    offset = Math.max(
                            offset,
                            combinedMaximums.get(contourDepth).doubleValue() + nodeGap
                                    - child.minimumContour.get(contourDepth).doubleValue());
                }
            }
            child.offsetX = offset;
            mergeContour(combinedMinimums, combinedMaximums, child, offset);
        }

        double childrenCenter = (children.get(0).offsetX
                + children.get(children.size() - 1).offsetX) / 2.0;
        for (Draft<N> child : children) {
            child.offsetX -= childrenCenter;
        }
        for (int contourDepth = 0; contourDepth < combinedMinimums.size(); contourDepth++) {
            combinedMinimums.set(
                    contourDepth,
                    combinedMinimums.get(contourDepth).doubleValue() - childrenCenter);
            combinedMaximums.set(
                    contourDepth,
                    combinedMaximums.get(contourDepth).doubleValue() - childrenCenter);
        }

        List<Double> minimumContour = new ArrayList<Double>(combinedMinimums.size() + 1);
        List<Double> maximumContour = new ArrayList<Double>(combinedMaximums.size() + 1);
        minimumContour.add(-size.width / 2.0);
        maximumContour.add(size.width / 2.0);
        minimumContour.addAll(combinedMinimums);
        maximumContour.addAll(combinedMaximums);
        return new Draft<N>(node, size, children, minimumContour, maximumContour);
    }

    private static <N> void mergeContour(
            List<Double> minimums,
            List<Double> maximums,
            Draft<N> child,
            double offset) {
        for (int depth = 0; depth < child.minimumContour.size(); depth++) {
            double minimum = child.minimumContour.get(depth).doubleValue() + offset;
            double maximum = child.maximumContour.get(depth).doubleValue() + offset;
            if (depth >= minimums.size()) {
                minimums.add(minimum);
                maximums.add(maximum);
            } else {
                minimums.set(depth, Math.min(minimums.get(depth).doubleValue(), minimum));
                maximums.set(depth, Math.max(maximums.get(depth).doubleValue(), maximum));
            }
        }
    }

    private static <N> void collectDepthHeights(
            Draft<N> draft,
            int depth,
            List<Integer> heights) {
        while (heights.size() <= depth) heights.add(0);
        heights.set(depth, Math.max(heights.get(depth).intValue(), draft.size.height));
        for (Draft<N> child : draft.children) {
            collectDepthHeights(child, depth + 1, heights);
        }
    }

    private static <N> void flatten(
            Draft<N> draft,
            double centerX,
            int depth,
            int parentIndex,
            int rootIndex,
            List<Integer> depthTops,
            List<RawNode<N>> result) {
        int nodeIndex = result.size();
        result.add(new RawNode<N>(
                draft.node,
                centerX - draft.size.width / 2.0,
                depthTops.get(depth).intValue(),
                draft.size.width,
                draft.size.height,
                parentIndex,
                rootIndex));
        for (Draft<N> child : draft.children) {
            flatten(
                    child,
                    centerX + child.offsetX,
                    depth + 1,
                    nodeIndex,
                    rootIndex,
                    depthTops,
                    result);
        }
    }

    private static int checkedCeiling(double value) {
        if (!Double.isFinite(value) || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Recipe tree is too wide to lay out");
        }
        return (int) Math.ceil(value);
    }

    private static int checkedRound(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Recipe tree coordinate is out of range");
        }
        return (int) Math.round(value);
    }

    private static int safeCoordinateAdd(int left, int right) {
        if (right > 0 && left > Integer.MAX_VALUE - right) {
            throw new IllegalArgumentException("Recipe tree dimensions exceed integer coordinates");
        }
        return left + right;
    }

    private static final class MutableCount {
        int value;
    }

    private static final class Draft<N> {
        final N node;
        final Size size;
        final List<Draft<N>> children;
        final List<Double> minimumContour;
        final List<Double> maximumContour;
        double offsetX;

        Draft(
                N node,
                Size size,
                List<Draft<N>> children,
                List<Double> minimumContour,
                List<Double> maximumContour) {
            this.node = node;
            this.size = size;
            this.children = children;
            this.minimumContour = minimumContour;
            this.maximumContour = maximumContour;
        }

        static <N> Draft<N> leaf(N node, Size size) {
            return new Draft<N>(
                    node,
                    size,
                    Collections.<Draft<N>>emptyList(),
                    Collections.singletonList(-size.width / 2.0),
                    Collections.singletonList(size.width / 2.0));
        }
    }

    private static final class RawNode<N> {
        final N node;
        final double left;
        final int top;
        final int width;
        final int height;
        final int parentIndex;
        final int rootIndex;

        RawNode(
                N node,
                double left,
                int top,
                int width,
                int height,
                int parentIndex,
                int rootIndex) {
            this.node = node;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.parentIndex = parentIndex;
            this.rootIndex = rootIndex;
        }
    }
}

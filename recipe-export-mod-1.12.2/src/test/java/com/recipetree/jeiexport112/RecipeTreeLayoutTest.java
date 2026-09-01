package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecipeTreeLayoutTest {
    private static final RecipeTreeLayout.Adapter<Node> ADAPTER =
            new RecipeTreeLayout.Adapter<Node>() {
                @Override
                public RecipeTreeLayout.Size size(Node node) {
                    return new RecipeTreeLayout.Size(node.width, node.height);
                }

                @Override
                public List<Node> children(Node node) {
                    return node.children;
                }
            };

    @Test
    public void laysOutACompactTopDownTreeWithoutSiblingOverlap() {
        Node first = node("first", 10, 20);
        Node second = node("second", 20, 15);
        Node root = node("root", 30, 10, first, second);

        RecipeTreeLayout.Result<Node> result = layout(root);

        assertEquals(3, result.nodes.size());
        RecipeTreeLayout.PlacedNode<Node> placedRoot = result.nodes.get(0);
        RecipeTreeLayout.PlacedNode<Node> placedFirst = result.nodes.get(1);
        RecipeTreeLayout.PlacedNode<Node> placedSecond = result.nodes.get(2);
        assertEquals(-1, placedRoot.parentIndex);
        assertEquals(0, placedFirst.parentIndex);
        assertEquals(0, placedSecond.parentIndex);
        assertEquals(0, placedRoot.top);
        assertEquals(18, placedFirst.top);
        assertEquals(18, placedSecond.top);
        assertTrue(placedFirst.left + placedFirst.width + 6 <= placedSecond.left);

        int rootCenterTwice = placedRoot.left * 2 + placedRoot.width;
        int childrenCenterTwice = ((placedFirst.left * 2 + placedFirst.width)
                + (placedSecond.left * 2 + placedSecond.width)) / 2;
        assertTrue(Math.abs(rootCenterTwice - childrenCenterTwice) <= 1);
    }

    @Test
    public void alignsForestLevelsAndSeparatesRootTrees() {
        Node firstChild = node("first child", 8, 7);
        Node firstRoot = node("first root", 14, 10, firstChild);
        Node secondChild = node("second child", 12, 4);
        Node secondRoot = node("second root", 18, 25, secondChild);

        RecipeTreeLayout.Result<Node> result = RecipeTreeLayout.layout(
                Arrays.asList(firstRoot, secondRoot),
                ADAPTER,
                6,
                5,
                11,
                new RecipeTreeLayout.Limits(4, 4, 20));

        assertEquals(4, result.nodes.size());
        assertEquals(0, result.nodes.get(0).rootIndex);
        assertEquals(0, result.nodes.get(1).rootIndex);
        assertEquals(1, result.nodes.get(2).rootIndex);
        assertEquals(1, result.nodes.get(3).rootIndex);
        assertEquals(30, result.nodes.get(1).top);
        assertEquals(30, result.nodes.get(3).top);
        assertEquals(-1, result.nodes.get(2).parentIndex);
        assertEquals(2, result.nodes.get(3).parentIndex);

        int firstTreeRight = Math.max(
                result.nodes.get(0).left + result.nodes.get(0).width,
                result.nodes.get(1).left + result.nodes.get(1).width);
        int secondTreeLeft = Math.min(
                result.nodes.get(2).left,
                result.nodes.get(3).left);
        assertTrue(firstTreeRight + 11 <= secondTreeLeft);
        assertEquals(37, result.height);
    }

    @Test
    public void anEmptyForestHasStableMinimumBounds() {
        RecipeTreeLayout.Result<Node> result = RecipeTreeLayout.layout(
                Collections.<Node>emptyList(),
                ADAPTER,
                6,
                5,
                11,
                new RecipeTreeLayout.Limits(4, 4, 20));

        assertTrue(result.nodes.isEmpty());
        assertEquals(1, result.width);
        assertEquals(1, result.height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTreesDeeperThanTheConfiguredMaximum() {
        Node grandchild = node("grandchild", 8, 8);
        Node child = node("child", 8, 8, grandchild);
        Node root = node("root", 8, 8, child);

        RecipeTreeLayout.layout(
                Collections.singletonList(root),
                ADAPTER,
                1,
                1,
                1,
                new RecipeTreeLayout.Limits(1, 4, 20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNodesWithTooManyChildren() {
        Node root = node(
                "root",
                8,
                8,
                node("one", 8, 8),
                node("two", 8, 8),
                node("three", 8, 8));

        RecipeTreeLayout.layout(
                Collections.singletonList(root),
                ADAPTER,
                1,
                1,
                1,
                new RecipeTreeLayout.Limits(2, 2, 20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsForestsAboveTheTotalNodeBound() {
        RecipeTreeLayout.layout(
                Arrays.asList(node("one", 8, 8), node("two", 8, 8)),
                ADAPTER,
                1,
                1,
                1,
                new RecipeTreeLayout.Limits(2, 2, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCyclesAndSharedNodeInstances() {
        Node root = node("root", 8, 8);
        root.children.add(root);

        layout(root);
    }

    private static RecipeTreeLayout.Result<Node> layout(Node root) {
        return RecipeTreeLayout.layout(
                Collections.singletonList(root),
                ADAPTER,
                6,
                8,
                12,
                new RecipeTreeLayout.Limits(8, 8, 100));
    }

    private static Node node(String name, int width, int height, Node... children) {
        return new Node(name, width, height, Arrays.asList(children));
    }

    private static final class Node {
        final String name;
        final int width;
        final int height;
        final List<Node> children;

        Node(String name, int width, int height, List<Node> children) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.children = new ArrayList<Node>(children);
        }
    }
}

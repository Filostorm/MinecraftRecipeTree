package com.recipetree.jeiexport112;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Canonicalizes public NBT without depending on compound hash-map iteration order. */
final class NbtCanonicalizer {
    private NbtCanonicalizer() {
    }

    static String canonical(NBTBase tag) {
        StringBuilder result = new StringBuilder();
        append(result, tag);
        return result.toString();
    }

    private static void append(StringBuilder target, NBTBase tag) {
        if (tag == null) {
            target.append("null");
            return;
        }
        target.append(tag.getId()).append(':');
        if (tag instanceof NBTTagCompound) {
            appendCompound(target, (NBTTagCompound) tag);
        } else if (tag instanceof NBTTagList) {
            appendList(target, (NBTTagList) tag);
        } else if (tag instanceof NBTTagByteArray) {
            appendByteArray(target, (NBTTagByteArray) tag);
        } else if (tag instanceof NBTTagIntArray) {
            appendIntArray(target, (NBTTagIntArray) tag);
        } else {
            appendLengthPrefixed(target, tag.toString());
        }
    }

    private static void appendCompound(StringBuilder target, NBTTagCompound compound) {
        Set<String> keySet = compound.getKeySet();
        List<String> keys = new ArrayList<String>(keySet);
        Collections.sort(keys);
        target.append('{').append(keys.size()).append(':');
        for (String key : keys) {
            appendLengthPrefixed(target, key);
            append(target, compound.getTag(key));
        }
        target.append('}');
    }

    private static void appendList(StringBuilder target, NBTTagList list) {
        int size = list.tagCount();
        target.append('[').append(size).append(':');
        NBTTagList copy = (NBTTagList) list.copy();
        NBTBase[] elements = new NBTBase[size];
        for (int index = size - 1; index >= 0; index--) {
            elements[index] = copy.removeTag(index);
        }
        for (NBTBase element : elements) append(target, element);
        target.append(']');
    }

    private static void appendByteArray(StringBuilder target, NBTTagByteArray tag) {
        byte[] values = tag.getByteArray();
        target.append('[').append(values.length).append(':');
        for (byte value : values) target.append((int) value).append(',');
        target.append(']');
    }

    private static void appendIntArray(StringBuilder target, NBTTagIntArray tag) {
        int[] values = tag.getIntArray();
        target.append('[').append(values.length).append(':');
        for (int value : values) target.append(value).append(',');
        target.append(']');
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe);
    }
}

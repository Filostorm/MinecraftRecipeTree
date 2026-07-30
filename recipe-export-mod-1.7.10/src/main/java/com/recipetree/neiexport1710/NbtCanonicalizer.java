package com.recipetree.neiexport1710;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Canonicalizes the public NBT tree API without depending on compound hash-map iteration order. */
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
        byte type = tag.getId();
        target.append(type).append(':');
        if (tag instanceof NBTTagCompound) {
            appendCompound(target, (NBTTagCompound) tag);
        } else if (tag instanceof NBTTagList) {
            appendList(target, (NBTTagList) tag);
        } else if (tag instanceof NBTTagByteArray) {
            appendByteArray(target, (NBTTagByteArray) tag);
        } else {
            appendLengthPrefixed(target, tag.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendCompound(StringBuilder target, NBTTagCompound compound) {
        Set<String> keySet = (Set<String>) compound.func_150296_c();
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
        // 1.7.10 has no public non-destructive generic list getter. Copying and removing from
        // that copy is still entirely public API and preserves every NBT subtype recursively.
        NBTTagList copy = (NBTTagList) list.copy();
        NBTBase[] elements = new NBTBase[size];
        for (int index = size - 1; index >= 0; index--) {
            elements[index] = copy.removeTag(index);
        }
        for (int index = 0; index < size; index++) {
            append(target, elements[index]);
        }
        target.append(']');
    }

    private static void appendByteArray(
            StringBuilder target, NBTTagByteArray tag) {
        byte[] values = tag.func_150292_c();
        target.append('[').append(values.length).append(':');
        for (byte value : values) target.append((int) value).append(',');
        target.append(']');
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe);
    }
}

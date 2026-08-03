package com.recipetree.neiexport1710;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;

final class PrimitiveRefs {
    private long[] produced = new long[4];
    private long[] used = new long[4];
    private int producedSize;
    private int usedSize;

    void add(boolean output, int category, int recipe) {
        long packed = ((long) category << 32) | (recipe & 0xffffffffL);
        if (output) {
            if (producedSize == produced.length) {
                produced = Arrays.copyOf(produced, produced.length << 1);
            }
            produced[producedSize++] = packed;
        } else {
            if (usedSize == used.length) {
                used = Arrays.copyOf(used, used.length << 1);
            }
            used[usedSize++] = packed;
        }
    }

    void write(JsonWriter writer) throws IOException {
        write(writer, "p", produced, producedSize);
        write(writer, "u", used, usedSize);
    }

    private static void write(JsonWriter writer, String name, long[] values, int size) throws IOException {
        writer.name(name).beginArray();
        for (int index = 0; index < size; index++) {
            long packed = values[index];
            writer.beginArray().value((int) (packed >>> 32)).value((int) packed).endArray();
        }
        writer.endArray();
    }
}

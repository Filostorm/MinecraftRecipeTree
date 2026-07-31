package com.recipetree.jeiexport112;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;

/** Two primitive long arrays avoid one int[2] allocation for every reverse-index edge. */
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
        writeArray(writer, "p", produced, producedSize);
        writeArray(writer, "u", used, usedSize);
    }

    private static void writeArray(JsonWriter writer, String name, long[] values, int size) throws IOException {
        writer.name(name).beginArray();
        for (int i = 0; i < size; i++) {
            long packed = values[i];
            writer.beginArray();
            writer.value((int) (packed >>> 32));
            writer.value((int) packed);
            writer.endArray();
        }
        writer.endArray();
    }
}

package com.recipetree.jeiexport;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class Naming {
    private Naming() {
    }

    /** File-system safe name: lowercase ascii, [a-z0-9._-] only, length-capped. */
    static String sanitize(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_").replaceAll("_{2,}", "_");
        if (out.length() > 80) {
            out = out.substring(0, 80) + "_" + hash8(s);
        }
        return out.isEmpty() ? "x" : out;
    }

    static String hash8(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Reserve a unique directory under recipes/ for a recipe category. */
    static String uniqueRecipeDir(ExportContext ctx, ResourceLocation categoryUid) {
        String base = sanitize(categoryUid.getNamespace() + "_" + categoryUid.getPath());
        String candidate = base;
        int n = 2;
        while (!ctx.usedPaths.add("recipes/" + candidate + "/")) {
            candidate = base + "_" + n++;
        }
        return candidate;
    }
}

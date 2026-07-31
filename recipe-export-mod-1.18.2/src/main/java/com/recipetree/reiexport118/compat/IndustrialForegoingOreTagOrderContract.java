package com.recipetree.reiexport118.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Exact MM2 contract for Industrial Foregoing's generated ore-recipe tag order. */
public final class IndustrialForegoingOreTagOrderContract {
    public static final String MOD_ID = "industrialforegoing";
    public static final String MOD_VERSION = "3.3.1.7";
    public static final String MOD_JAR_SHA256 =
            "5ff7dce05c3c3233fae8213bb8a23b4b5957495ec6adbd07143f862f9ba7a35a";

    public static final String TARGET_CLASS =
            "com.buuz135.industrial.plugin.jei.JEICustomPlugin";
    public static final String TARGET_RESOURCE =
            "com/buuz135/industrial/plugin/jei/JEICustomPlugin.class";
    public static final String TARGET_CLASS_SHA256 =
            "1768aa72c4292031dfbd09b41d125e3284281a5cb2eb1284c296b951d8b4a1a2";
    public static final String REGISTER_RECIPES =
            "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V";
    public static final String GET_TAG_NAMES_TARGET =
            "Lnet/minecraftforge/registries/tags/ITagManager;getTagNames()"
                    + "Ljava/util/stream/Stream;";
    public static final String RAW_MATERIAL_PREFIX = "forge:raw_materials/";
    public static final List<String> EXPECTED_VALID_RAW_TAG_IDS = List.of(
            "forge:raw_materials/adamantium",
            "forge:raw_materials/aetherium",
            "forge:raw_materials/aluminum",
            "forge:raw_materials/arcanite",
            "forge:raw_materials/beryllium",
            "forge:raw_materials/cadmium",
            "forge:raw_materials/calorite",
            "forge:raw_materials/chromium",
            "forge:raw_materials/cobalt",
            "forge:raw_materials/copper",
            "forge:raw_materials/densite",
            "forge:raw_materials/desh",
            "forge:raw_materials/draconium",
            "forge:raw_materials/gold",
            "forge:raw_materials/imortite",
            "forge:raw_materials/iridium",
            "forge:raw_materials/iron",
            "forge:raw_materials/jimmium",
            "forge:raw_materials/kharaxium",
            "forge:raw_materials/lead",
            "forge:raw_materials/lithium",
            "forge:raw_materials/magnesium",
            "forge:raw_materials/manganese",
            "forge:raw_materials/mithril",
            "forge:raw_materials/molybdenum",
            "forge:raw_materials/neodymium",
            "forge:raw_materials/nickel",
            "forge:raw_materials/orichalcum",
            "forge:raw_materials/osmium",
            "forge:raw_materials/ostrum",
            "forge:raw_materials/palladium",
            "forge:raw_materials/platinum",
            "forge:raw_materials/potentium",
            "forge:raw_materials/rune",
            "forge:raw_materials/scandium",
            "forge:raw_materials/silver",
            "forge:raw_materials/thorium",
            "forge:raw_materials/tin",
            "forge:raw_materials/titanium",
            "forge:raw_materials/tungsten",
            "forge:raw_materials/uranium",
            "forge:raw_materials/uru",
            "forge:raw_materials/vanadium",
            "forge:raw_materials/vibranium",
            "forge:raw_materials/vincyte",
            "forge:raw_materials/zinc");

    private IndustrialForegoingOreTagOrderContract() {
    }

    /**
     * Sorts the complete item-tag snapshot and audits the subset that Industrial Foregoing will
     * convert into raw, fermenter, and sieve recipe entries.
     */
    public static <T> CanonicalOrder<T> canonicalize(
            List<T> source,
            Function<? super T, String> idExtractor,
            Predicate<? super T> validRawTag
    ) {
        if (source == null) {
            throw new IllegalStateException("Industrial Foregoing item-tag source is null");
        }
        if (idExtractor == null) {
            throw new IllegalStateException("Industrial Foregoing tag-ID extractor is null");
        }
        if (validRawTag == null) {
            throw new IllegalStateException("Industrial Foregoing raw-tag predicate is null");
        }

        List<KeyedValue<T>> keyed = new ArrayList<>(source.size());
        Set<String> uniqueIds = new HashSet<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            T value = source.get(index);
            if (value == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing item-tag source contains null at index=" + index);
            }
            String id = idExtractor.apply(value);
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        "Industrial Foregoing item-tag ID is blank at index=" + index);
            }
            if (!uniqueIds.add(id)) {
                throw new IllegalStateException(
                        "Industrial Foregoing item-tag source contains duplicate ID=" + id);
            }
            keyed.add(new KeyedValue<>(id, value));
        }

        List<KeyedValue<T>> ordered = new ArrayList<>(keyed);
        ordered.sort(Comparator.comparing(KeyedValue::id));

        List<String> validRawTagIds = new ArrayList<>();
        List<T> orderedValues = new ArrayList<>(ordered.size());
        for (KeyedValue<T> entry : ordered) {
            orderedValues.add(entry.value());
            if (entry.id().startsWith(RAW_MATERIAL_PREFIX)
                    && validRawTag.test(entry.value())) {
                validRawTagIds.add(entry.id());
            }
        }
        requireExactValidRawDomain(validRawTagIds);

        boolean inputAlreadyCanonical = true;
        for (int index = 0; index < keyed.size(); index++) {
            if (!keyed.get(index).id().equals(ordered.get(index).id())) {
                inputAlreadyCanonical = false;
                break;
            }
        }
        return new CanonicalOrder<>(
                List.copyOf(orderedValues),
                List.copyOf(validRawTagIds),
                inputAlreadyCanonical);
    }

    private static void requireExactValidRawDomain(List<String> observed) {
        if (EXPECTED_VALID_RAW_TAG_IDS.equals(observed)) {
            return;
        }
        Set<String> observedSet = Set.copyOf(observed);
        Set<String> expectedSet = Set.copyOf(EXPECTED_VALID_RAW_TAG_IDS);
        List<String> missing = EXPECTED_VALID_RAW_TAG_IDS.stream()
                .filter(id -> !observedSet.contains(id))
                .toList();
        List<String> extra = observed.stream()
                .filter(id -> !expectedSet.contains(id))
                .toList();
        throw new IllegalStateException(
                "Industrial Foregoing valid raw-material tag domain drift: expected="
                        + EXPECTED_VALID_RAW_TAG_IDS.size()
                        + ", actual=" + observed.size()
                        + ", missing=" + missing
                        + ", extra=" + extra
                        + ", observedOrder=" + observed);
    }

    private record KeyedValue<T>(String id, T value) {
    }

    public record CanonicalOrder<T>(
            List<T> values,
            List<String> validRawTagIds,
            boolean inputAlreadyCanonical
    ) {
    }

}

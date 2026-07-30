package com.recipetree.jeiexport112;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Repairs identity metadata from legacy HEI helpers whose IDs do not cover all semantic fields.
 *
 * <p>The adapters are intentionally selected by exact class name. A recognized adapter is
 * fail-closed: if the installed mod changes its API or supplies an invalid value, the caller logs
 * an {@code ingredient unique id} failure and rejects that ingredient. Falling back to the helper
 * ID would merge distinct recipe values and corrupt the reverse recipe index.</p>
 */
final class LegacyIngredientIdentity {
    private static final String ASPECT_LIST = "thaumcraft.api.aspects.AspectList";
    private static final String MANA =
            "kport.modularmagic.common.integration.jei.ingredient.Mana";
    private static final String METEOR =
            "github.alecsio.mmceaddons.common.integration.jei.ingredient.Meteor";
    private static final String FLUX =
            "github.alecsio.mmceaddons.common.integration.jei.ingredient.Flux";
    private static final String LIFE_ESSENCE =
            "kport.modularmagic.common.integration.jei.ingredient.LifeEssence";
    private static final String BIOME =
            "github.alecsio.mmceaddons.common.integration.jei.ingredient.Biome";
    private static final String DEMON_WILL =
            "kport.modularmagic.common.integration.jei.ingredient.DemonWill";
    private static final String IMPETUS =
            "kport.modularmagic.common.integration.jei.ingredient.Impetus";
    private static final String VILLAGER_CAREER =
            "net.minecraftforge.fml.common.registry.VillagerRegistry$VillagerCareer";

    private LegacyIngredientIdentity() {
    }

    static Identity adapt(Object ingredient, String helperUid, String helperResourceId,
                          String helperDisplayName, String helperModId,
                          NestedIngredientIdentity nestedIdentity) {
        String className = ingredient.getClass().getName();
        if (ASPECT_LIST.equals(className)) {
            return aspectList(ingredient);
        }
        if (MANA.equals(className)) {
            return mana();
        }
        if (METEOR.equals(className)) {
            return meteor(ingredient, nestedIdentity);
        }
        if (FLUX.equals(className)) {
            return flux(number(invoke(ingredient, "chunkRange"), className + "#chunkRange").intValue());
        }
        if (LIFE_ESSENCE.equals(className)) {
            return lifeEssence(bool(invoke(ingredient, "isPerTick"), className + "#isPerTick"));
        }
        if (BIOME.equals(className)) {
            return biome(ingredient);
        }
        if (DEMON_WILL.equals(className)) {
            return demonWill(ingredient);
        }
        if (IMPETUS.equals(className)) {
            return impetus();
        }
        if (VILLAGER_CAREER.equals(className)) {
            return villagerCareer(ingredient, helperDisplayName);
        }
        return new Identity(helperUid, helperResourceId, helperDisplayName, helperModId);
    }

    private static Identity aspectList(Object ingredient) {
        Object aspects = invoke(ingredient, "getAspects");
        if (aspects == null || !aspects.getClass().isArray() || Array.getLength(aspects) != 1) {
            throw new IllegalArgumentException("ThaumicJEI aspect identity requires exactly one aspect");
        }
        Object aspect = Array.get(aspects, 0);
        return aspect(text(invoke(aspect, "getTag"), "Aspect#getTag"),
                text(invoke(aspect, "getName"), "Aspect#getName"));
    }

    static Identity aspect(String rawTag, String displayName) {
        String tag = lowerResourcePart(text(rawTag, "Aspect#getTag"));
        String name = text(displayName, "Aspect#getName");
        return fixed("aspect:" + tag, "thaumcraft:aspect/" + tag, name, "thaumcraft");
    }

    static Identity mana() {
        return fixed("mana", "modularmachinery:mana", "Mana", "modularmachinery");
    }

    static Identity impetus() {
        return fixed("impetus", "modularmachinery:impetus", "Impetus", "modularmachinery");
    }

    static Identity lifeEssence(boolean perTick) {
        String qualifier = perTick ? "per_tick" : "per_operation";
        String label = perTick ? "Life Essence (per tick)" : "Life Essence (per operation)";
        return fixed("life_essence:" + qualifier, "modularmachinery:life_essence", label,
                "modularmachinery");
    }

    static Identity flux(int chunkRange) {
        if (chunkRange < 0) {
            throw new IllegalArgumentException("MMCE Addons Flux chunk range must be non-negative, got " +
                    chunkRange);
        }
        return fixed("flux:chunk_range=" + chunkRange, "modularmachineryaddons:flux",
                "Flux (chunk range " + chunkRange + ")", "modularmachineryaddons");
    }

    private static Identity biome(Object ingredient) {
        String registryName = text(invoke(ingredient, "getRegistryName"), "Biome#getRegistryName");
        String name = optionalText(invoke(ingredient, "getName"));
        if (name == null) {
            // MMCE Addons performs its canonical registry-name lookup lazily from getTooltip().
            // Invoke that public initializer so the exported label matches what HEI displays.
            invoke(ingredient, "getTooltip");
            name = text(invoke(ingredient, "getName"), "Biome#getName after getTooltip");
        }
        return biome(registryName, name);
    }

    static Identity biome(String registryName, String displayName) {
        String resourceId = resourceLocation(registryName, "Biome#getRegistryName");
        String name = text(displayName, "Biome#getName");
        String descriptor = token(resourceId) + token(name);
        return fixed("biome:" + resourceId + ":" + sha256(descriptor), resourceId, name,
                namespace(resourceId));
    }

    private static Identity demonWill(Object ingredient) {
        Object willType = invoke(ingredient, "getWillType");
        if (willType == null) {
            throw new IllegalArgumentException("DemonWill#getWillType returned null");
        }
        String typeName = lowerResourcePart(text(publicField(willType, "name"),
                "EnumDemonWillType.name"));
        return demonWill(typeName);
    }

    static Identity demonWill(String typeName) {
        String type = lowerResourcePart(text(typeName, "Demon Will type"));
        return fixed("demon_will:" + type, "modularmachinery:demon_will/" + type,
                title(type) + " Will", "modularmachinery");
    }

    private static Identity meteor(Object ingredient, NestedIngredientIdentity nestedIdentity) {
        if (nestedIdentity == null) {
            throw new IllegalStateException("Meteor identity requires a nested ItemStack identity provider");
        }
        Object catalyst = invoke(ingredient, "getCatalystStack");
        String catalystIdentity = text(nestedIdentity.identity(catalyst),
                "Meteor catalyst ItemStack identity");
        Number radius = number(invoke(ingredient, "getRadius"), "Meteor#getRadius");
        Number strength = number(invoke(ingredient, "getExplosionStrength"),
                "Meteor#getExplosionStrength");
        Object rawComponents = invoke(ingredient, "getComponents");
        if (!(rawComponents instanceof List)) {
            throw new IllegalStateException("Meteor#getComponents did not return a List");
        }
        List<MeteorComponent> components = new ArrayList<MeteorComponent>();
        for (Object rawComponent : (List<?>) rawComponents) {
            components.add(new MeteorComponent(
                    text(invoke(rawComponent, "getOreName"), "MeteorComponent#getOreName"),
                    number(invoke(rawComponent, "getWeight"), "MeteorComponent#getWeight").intValue()));
        }
        return meteor(catalystIdentity, radius.intValue(), strength, components);
    }

    static Identity meteor(String catalystIdentity, int radius, Number explosionStrength,
                           List<MeteorComponent> components) {
        String catalyst = text(catalystIdentity, "Meteor catalyst identity");
        if (radius < 0) {
            throw new IllegalArgumentException("Meteor radius must be non-negative, got " + radius);
        }
        String strength = decimal(explosionStrength, "Meteor explosion strength");
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Meteor must contain at least one weighted component");
        }

        StringBuilder descriptor = new StringBuilder();
        descriptor.append(token(catalyst));
        descriptor.append(radius).append(';').append(token(strength));
        StringBuilder composition = new StringBuilder();
        for (MeteorComponent component : components) {
            if (component == null) {
                throw new IllegalArgumentException("Meteor component list contains null");
            }
            String ore = text(component.oreName, "Meteor component ore name");
            if (component.weight <= 0) {
                throw new IllegalArgumentException("Meteor component " + ore +
                        " must have positive weight, got " + component.weight);
            }
            descriptor.append(token(ore)).append(component.weight).append(';');
            if (composition.length() > 0) {
                composition.append(", ");
            }
            composition.append(ore).append('@').append(component.weight);
        }
        String fingerprint = sha256(descriptor.toString());
        String label = "Meteor (radius " + radius + ", strength " + strength +
                ", components " + composition + ")";
        return fixed("meteor:" + fingerprint, "modularmachineryaddons:meteor", label,
                "modularmachineryaddons");
    }

    private static Identity villagerCareer(Object ingredient, String helperDisplayName) {
        String careerName = text(invoke(ingredient, "getName"), "VillagerCareer#getName");
        Object profession = declaredField(ingredient, "profession");
        if (profession == null) {
            throw new IllegalArgumentException("VillagerCareer.profession was null");
        }
        String professionId = resourceLocation(String.valueOf(invoke(profession, "getRegistryName")),
                "VillagerProfession#getRegistryName");
        return villagerCareer(professionId, careerName, helperDisplayName);
    }

    static Identity villagerCareer(String professionId, String careerName, String displayName) {
        String profession = resourceLocation(professionId, "Villager profession id");
        String career = text(careerName, "Villager career name");
        String label = text(displayName, "Villager career display name");
        String uid = "villager_career:" + profession + ":" + token(career);
        String path = "career/" + namespace(profession) + "/" + resourcePath(profession) + "/" +
                lowerResourcePart(career);
        return fixed(uid, "jeivillagers:" + path, label, "jeivillagers");
    }

    private static Identity fixed(String uid, String resourceId, String displayName, String modId) {
        return new Identity(text(uid, "legacy ingredient UID"),
                resourceLocation(resourceId, "legacy ingredient resource ID"),
                text(displayName, "legacy ingredient display name"),
                lowerResourcePart(text(modId, "legacy ingredient mod ID")));
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            throw new IllegalArgumentException("cannot invoke " + methodName + " on null");
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            FatalErrors.rethrowIfFatal(cause);
            throw new IllegalStateException(target.getClass().getName() + "#" + methodName +
                    " failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(target.getClass().getName() + "#" + methodName +
                    " is unavailable", exception);
        }
    }

    private static Object publicField(Object target, String fieldName) {
        try {
            return target.getClass().getField(fieldName).get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(target.getClass().getName() + "." + fieldName +
                    " is unavailable", exception);
        }
    }

    private static Object declaredField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(target.getClass().getName() + "." + fieldName +
                    " is unavailable", exception);
        } catch (SecurityException exception) {
            throw new IllegalStateException("access denied for " + target.getClass().getName() + "." +
                    fieldName, exception);
        }
    }

    private static Number number(Object value, String source) {
        if (!(value instanceof Number)) {
            throw new IllegalStateException(source + " returned non-numeric value " + value);
        }
        return (Number) value;
    }

    private static boolean bool(Object value, String source) {
        if (!(value instanceof Boolean)) {
            throw new IllegalStateException(source + " returned non-boolean value " + value);
        }
        return ((Boolean) value).booleanValue();
    }

    private static String optionalText(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String text(Object value, String source) {
        String text = value instanceof String ? ((String) value).trim() : null;
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(source + " was null/blank");
        }
        return text;
    }

    private static String decimal(Number value, String source) {
        if (value == null || value instanceof Double && !Double.isFinite(value.doubleValue()) ||
                value instanceof Float && !Float.isFinite(value.floatValue())) {
            throw new IllegalArgumentException(source + " must be finite, got " + value);
        }
        try {
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(source + " is not a decimal: " + value, exception);
        }
    }

    private static String resourceLocation(String value, String source) {
        String text = text(value, source);
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException(source + " is not a namespaced resource ID: " + text);
        }
        String namespace = lowerResourcePart(text.substring(0, colon));
        String path = lowerResourcePath(text.substring(colon + 1));
        return namespace + ':' + path;
    }

    private static String namespace(String resourceId) {
        return resourceId.substring(0, resourceId.indexOf(':'));
    }

    private static String resourcePath(String resourceId) {
        return resourceId.substring(resourceId.indexOf(':') + 1);
    }

    private static String lowerResourcePart(String value) {
        String lower = text(value, "resource identifier").toLowerCase(Locale.ROOT);
        if (!lower.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid resource identifier component: " + value);
        }
        return lower;
    }

    private static String lowerResourcePath(String value) {
        String lower = text(value, "resource path").toLowerCase(Locale.ROOT);
        if (!lower.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid resource path: " + value);
        }
        return lower;
    }

    private static String title(String value) {
        String[] words = value.split("[_-]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String token(String value) {
        return value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte next : digest) {
                result.append(String.format(Locale.ROOT, "%02x", next & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM is missing SHA-256", exception);
        }
    }

    interface NestedIngredientIdentity {
        String identity(Object ingredient);
    }

    static final class Identity {
        final String uid;
        final String resourceId;
        final String displayName;
        final String modId;

        Identity(String uid, String resourceId, String displayName, String modId) {
            this.uid = uid;
            this.resourceId = resourceId;
            this.displayName = displayName;
            this.modId = modId;
        }
    }

    static final class MeteorComponent {
        final String oreName;
        final int weight;

        MeteorComponent(String oreName, int weight) {
            this.oreName = oreName;
            this.weight = weight;
        }
    }
}

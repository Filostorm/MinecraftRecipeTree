package com.recipetree.neiexport1710;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Exact IC2 2.2.828 {@code CropCard} identity contract.
 *
 * <p>The methods are deliberately resolved from the public API base class, not
 * from a crop's runtime class. GTNH contains package-private concrete crop
 * classes with public overrides. A {@link Method} declared by one of those
 * inaccessible classes cannot be invoked from this package, while invoking the
 * public base declaration still performs normal virtual dispatch.</p>
 */
final class CropIdentityContract {
    static final String CROP_CARD_CLASS = "ic2.api.crops.CropCard";

    private final Class<?> cropCardClass;
    private final Method ownerMethod;
    private final Method nameMethod;

    private CropIdentityContract(Class<?> cropCardClass,
                                 Method ownerMethod,
                                 Method nameMethod) {
        this.cropCardClass = cropCardClass;
        this.ownerMethod = ownerMethod;
        this.nameMethod = nameMethod;
    }

    static CropIdentityContract load(ClassLoader loader) throws ExportFailure {
        final Class<?> cropCardClass;
        try {
            cropCardClass = Class.forName(CROP_CARD_CLASS, false, loader);
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not load exact IC2 CropCard API class " + CROP_CARD_CLASS,
                    cause);
        }
        return bind(cropCardClass, true);
    }

    /** Package-private seam for focused contract tests without an IC2 test dependency. */
    static CropIdentityContract bindForTesting(Class<?> cropCardClass)
            throws ExportFailure {
        return bind(cropCardClass, false);
    }

    Class<?> cropCardClass() {
        return cropCardClass;
    }

    private static CropIdentityContract bind(Class<?> cropCardClass,
                                             boolean requireExactName)
            throws ExportFailure {
        if (cropCardClass == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 CropCard API class is null");
        }
        if (requireExactName && !CROP_CARD_CLASS.equals(cropCardClass.getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "expected IC2 CropCard API class " + CROP_CARD_CLASS
                            + ", got " + cropCardClass.getName());
        }
        int classModifiers = cropCardClass.getModifiers();
        if (!Modifier.isPublic(classModifiers) || !Modifier.isAbstract(classModifiers)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    cropCardClass.getName() + " must remain a public abstract class");
        }

        try {
            Method owner = requireExactApiMethod(cropCardClass, "owner", false);
            Method name = requireExactApiMethod(cropCardClass, "name", true);
            return new CropIdentityContract(cropCardClass, owner, name);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "could not bind exact public IC2 CropCard owner()/name() contract",
                    cause);
        }
    }

    private static Method requireExactApiMethod(Class<?> cropCardClass,
                                                String name,
                                                boolean requireAbstract)
            throws Exception {
        Method method = cropCardClass.getDeclaredMethod(name);
        int modifiers = method.getModifiers();
        if (method.getReturnType() != String.class
                || !Modifier.isPublic(modifiers)
                || Modifier.isStatic(modifiers)
                || Modifier.isAbstract(modifiers) != requireAbstract
                || method.isBridge()
                || method.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    cropCardClass.getName() + "." + name
                            + " must remain a public "
                            + (requireAbstract ? "abstract " : "concrete ")
                            + "instance method returning java.lang.String");
        }
        // Do not call setAccessible(true): the exact pinned API declaration is
        // public, and accessibility relaxation would hide future contract drift.
        return method;
    }

    String requireCanonicalId(Object crop, Map<String, Object> cropsById)
            throws ExportFailure {
        if (crop == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 crop cache contains a null CropCard");
        }
        if (!cropCardClass.isInstance(crop)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop cache value is not an exact " + cropCardClass.getName()
                            + ": " + crop.getClass().getName());
        }

        final String owner;
        final String name;
        try {
            owner = requireReturnedString(ownerMethod.invoke(crop), crop, "owner");
            name = requireReturnedString(nameMethod.invoke(crop), crop, "name");
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            String detail = cause.getMessage();
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "could not invoke exact IC2 CropCard owner()/name() contract for "
                            + crop.getClass().getName() + ": "
                            + cause.getClass().getName()
                            + (detail == null || detail.isEmpty() ? "" : ": " + detail),
                    cause);
        }

        String id = frame('O', owner) + frame('N', name);
        Object existing = cropsById.get(id);
        if (existing != null && existing != crop) {
            throw new ExportFailure("HANDLER_DUPLICATE",
                    "IC2 CropCard owner/name identity is duplicated: " + id);
        }
        if (existing == null) {
            cropsById.put(id, crop);
        }
        return id;
    }

    private static String requireReturnedString(Object value, Object crop, String component)
            throws ExportFailure {
        if (!(value instanceof String)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 CropCard " + crop.getClass().getName() + "." + component
                            + "() returned " + (value == null ? "null" : value.getClass().getName())
                            + "; expected a nonnull java.lang.String");
        }
        String text = (String) value;
        if (text.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 CropCard " + crop.getClass().getName() + "." + component
                            + "() returned an empty identity component");
        }
        if (isBoundaryWhitespace(text.charAt(0))
                || isBoundaryWhitespace(text.charAt(text.length() - 1))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 CropCard " + crop.getClass().getName() + "." + component
                            + "() contains leading or trailing whitespace; identity is not normalized");
        }
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isISOControl(current)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 CropCard " + crop.getClass().getName() + "." + component
                                + "() contains an ISO control character");
            }
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= text.length()
                        || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    throw invalidUnicode(crop, component);
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw invalidUnicode(crop, component);
            }
        }
        return text;
    }

    private static boolean isBoundaryWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static ExportFailure invalidUnicode(Object crop, String component) {
        return new ExportFailure("RECIPE_SEMANTICS",
                "IC2 CropCard " + crop.getClass().getName() + "." + component
                        + "() contains an unpaired UTF-16 surrogate");
    }

    private static String frame(char component, String value) {
        int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return new StringBuilder(value.length() + 16)
                .append(component)
                .append(utf8Bytes)
                .append(':')
                .append(value)
                .toString();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}

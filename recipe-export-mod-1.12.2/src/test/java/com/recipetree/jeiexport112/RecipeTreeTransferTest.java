package com.recipetree.jeiexport112;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.IIngredientRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RecipeTreeTransferTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void portableTreeRestoresAsOneHistoryEntry() throws Exception {
        String key = ProjectEEmcPhase.EMC_KEY;
        String json = "{"
                + "\"format\":\"minecraft-recipe-tree\","
                + "\"version\":1,"
                + "\"pack\":{\"minecraftVersion\":\"1.12.2\"},"
                + "\"rootKey\":\"" + key + "\","
                + "\"direction\":\"inputs\","
                + "\"productionPlan\":{\"amount\":12,\"windowSeconds\":1},"
                + "\"selections\":[{\"path\":[],\"itemKey\":\"" + key + "\","
                + "\"source\":{\"kind\":\"recipe\",\"recipeKey\":\"test:root\"}}]}";

        RecipeTreeProgress.RecipeHistoryEntry entry =
                RecipeTreeTransfer.fromJson(json, bridge());

        assertEquals(key, entry.getItemIdentity());
        assertEquals(12, entry.getAmount());
        assertEquals("test:root", entry.getRecipeIdentity());
        assertEquals(1, entry.getRoots().size());
        assertEquals(1, entry.getSelections().size());
        assertEquals("test:root", entry.getSelections().get(0).getRecipeIdentity());
    }

    @Test
    public void incompatiblePortableTreeIsRejectedExplicitly() {
        String json = "{\"format\":\"minecraft-recipe-tree\",\"version\":2}";
        IOException error = assertThrows(IOException.class,
                () -> RecipeTreeTransfer.fromJson(json, bridge()));
        assertEquals("version must be 1", error.getMessage());
    }

    @Test
    public void shareFolderListsOnlyPortableTreeFilesNewestFirst() throws Exception {
        Path directory = temporary.newFolder("shares").toPath();
        Path older = directory.resolve("older.mrtree.json");
        Path newer = directory.resolve("newer.mrtree.json");
        Files.write(older, "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(newer, "{}".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(older,
                java.nio.file.attribute.FileTime.fromMillis(1000L));
        Files.setLastModifiedTime(newer,
                java.nio.file.attribute.FileTime.fromMillis(2000L));
        Files.write(directory.resolve("ignore.json"), "{}".getBytes(StandardCharsets.UTF_8));

        List<Path> files = RecipeTreeTransfer.listShareFiles(directory);

        assertEquals(2, files.size());
        assertEquals(newer, files.get(0));
        assertEquals(older, files.get(1));
    }

    @SuppressWarnings("unchecked")
    private static RecipeTreeViewerBridge bridge() {
        final IRecipeRegistry recipes = proxy(IRecipeRegistry.class, null);
        IJeiRuntime runtime = proxy(IJeiRuntime.class, recipes);
        IIngredientRegistry ingredients = proxy(IIngredientRegistry.class, null);
        return new RecipeTreeViewerBridge(runtime, ingredients);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final IRecipeRegistry recipes) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if ("getRecipeRegistry".equals(method.getName())) return recipes;
                        if ("toString".equals(method.getName())) return type.getSimpleName();
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) return proxy == arguments[0];
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) return false;
                        if (returnType == int.class) return 0;
                        if (returnType == long.class) return 0L;
                        return null;
                    }
                });
    }
}

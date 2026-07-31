package com.recipetree.jeiexport112;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class ModCompatibilityContractTest {
    @Test
    public void processedMetadataMatchesTheCompileTimeCompatibilityContract() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("mcmod.info");
        assertNotNull(stream);
        JsonArray root;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(reader).getAsJsonArray();
        }
        JsonObject metadata = root.get(0).getAsJsonObject();

        assertEquals(JeiExportMod.VERSION, metadata.get("version").getAsString());
        assertEquals("1.12.2", metadata.get("mcversion").getAsString());
        assertEquals(JeiExportMod.JEI_DEPENDENCY,
                metadata.getAsJsonArray("dependencies").get(0).getAsString());
    }
}

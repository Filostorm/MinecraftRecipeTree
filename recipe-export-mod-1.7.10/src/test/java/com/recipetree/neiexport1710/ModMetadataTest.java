package com.recipetree.neiexport1710;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModMetadataTest {
    @Test
    public void processedMetadataUsesForgeParseableExactNeiVersion() throws Exception {
        InputStream stream = ModMetadataTest.class.getResourceAsStream("/mcmod.info");
        assertNotNull("processed mcmod.info must be on the test runtime classpath", stream);

        JsonArray metadata;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            metadata = new JsonParser().parse(reader).getAsJsonArray();
        }
        String dependency = metadata.get(0).getAsJsonObject()
                .getAsJsonArray("dependencies").get(0).getAsString();
        assertEquals("required-after:NotEnoughItems@[2.8.44-GTNH]", dependency);

        String referenceText = dependency.substring("required-after:".length());
        ArtifactVersion reference = VersionParser.parseVersionReference(referenceText);
        assertTrue(reference.containsVersion(version("2.8.44-GTNH")));
        assertFalse(reference.containsVersion(version("2.8.43-GTNH")));
        assertFalse(reference.containsVersion(version("2.8.45-GTNH")));
    }

    private static ArtifactVersion version(String version) {
        return new DefaultArtifactVersion("NotEnoughItems", version);
    }
}

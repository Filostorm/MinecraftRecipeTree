# Recipe Tree JEI Exporter (NeoForge 1.21.1)

Client-side NeoForge mod that exports JEI’s complete ingredient and recipe registry, rendered
previews, living entities, sampled loot, block drops, and villager trades to
`<gameDir>/jei-exports/`.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.x
- JEI 19.21.2.313 through 19.x
- Java 21

The strict publication profile is `generic-jei-1.21.1`. Its default export uses 4× ingredient
canvases and 2× recipe layouts, records exact pack identity, retains complete diagnostics, and
requires one preview PNG for every declared recipe.

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test build
# -> build/libs/jeiexport-1.0.0.jar
```

## Use

1. Put the JAR in a NeoForge 1.21.1 modpack’s `mods` directory alongside JEI 19.x.
2. Start or join a single-player world.
3. Run `/jeiexport all`.
4. Wait for the completion message, then upload the unchanged `jei-exports` directory.

Pack identity is read from CurseForge, Prism/MultiMC, or Modrinth launcher metadata. Automated
launchers can override it with both of these JVM properties:

```text
-Djeiexport.packName=My Modpack
-Djeiexport.packVersion=1.2.3
```

For an unattended CurseForge smoke test, add these JVM arguments to the profile, then launch the
profile normally from CurseForge:

```text
-Djeiexport.auto=all
-Djeiexport.createWorld=true
-Djeiexport.worldFolder=RecipeTree-Exporter-Test
-Djeiexport.worldName=RecipeTreeExport
-Djeiexport.exitOnComplete=true
```

This creates one new disposable single-player world, starts the full export after JEI is ready,
and closes Minecraft only after a successful export. The exporter refuses to reuse an existing
world folder. These options are disabled unless explicitly set.

Exports are transactional: a failed or cancelled run leaves the previous `jei-exports/` snapshot
untouched and retains its staging directory for diagnosis.

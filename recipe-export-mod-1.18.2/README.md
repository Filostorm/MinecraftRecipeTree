# Minecraft 1.18.2 recipe exporter

This Forge mod exports item, recipe, and rendering data from REI for Minecraft Recipe Tree.

The project requires Java 17 and the Multiblock Madness 2 mod JARs. Those third-party JARs are intentionally not included in this repository. The included Gradle wrapper pins the compatible Gradle runtime.

By default, the build reads them from `../export-instances/multiblock-madness-2/mods/`. Set `MM2_MODS_DIR` or pass `-Pmm2ModsDir=/path/to/mods` to use another directory. Configuration fails with an explicit error when the directory is unavailable.

Run the automated tests from this directory:

```bash
./gradlew -Pmm2ModsDir=/path/to/multiblock-madness-2/mods test
```

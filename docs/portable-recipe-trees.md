# Portable recipe trees

Minecraft Recipe Tree shares use the versioned `minecraft-recipe-tree` JSON format and the
`.mrtree.json` filename suffix. The format moves the same ingredient tree between the web viewer,
the mobile app, and the in-game JEI viewer.

Recipe selections carry two identities:

- `recipeKey`: JEI's stable `category-id|recipe-id` identity, used across platforms and dataset
  publications.
- `ref`: the optional `[categoryIndex, recipeIndex]` location used as a fast path when reopening
  the exact same web dataset publication.

The importer still verifies the stable identity before accepting a web reference. This prevents a
reference from silently selecting a different recipe after a pack publication changes.

## Web and mobile app

Open the graph controls and choose **Share**.

- **Share current tree** opens the native share sheet on mobile. On web it uses file sharing when
  supported, otherwise it downloads a `.mrtree.json` file and attempts to copy the JSON.
- On mobile, **Import file** opens the system document picker and imports the selected tree.
- On web, drop a `.mrtree.json` file onto the import target, choose **Import file**, or paste JSON.
- On desktop web, **Save into modpack instance** asks for the instance folder and writes the tree
  to `config/recipe-tree-shares`. This direct folder handoff requires browser folder-access support;
  unsupported browsers download the tree instead.

Imports are limited to 1 MiB, 2,048 selected sources, and 64 levels. Minecraft versions must match.
Recipes are resolved by stable identity, so a share can be opened in another publication only when
that publication contains the same items and JEI recipes.

## Minecraft 1.20.1 mod

The recipe-tree screen has **Share** and **Import file** buttons.

- **Share** copies the JSON to the system clipboard and writes
  `config/recipe-tree-shares/last-tree.mrtree.json`.
- **Import file** automatically reads the newest `.mrtree.json` file from
  `config/recipe-tree-shares` and reconstructs the JEI recipe branches. If that folder has no tree,
  it falls back to JSON from the system clipboard.

The in-game viewer currently imports ingredient-directed trees. Mob-drop and mining sources from a
web tree are left collapsed because the JEI planner does not represent those as recipe pages.

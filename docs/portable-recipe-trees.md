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
- Paste copied history JSON into **Open shared tree history**, or choose/drop the received
  `.mrtree.json` history file.

Histories are limited to 1 MiB, 2,048 selected sources, and 64 levels. The selected site publication
must match every pack identity field carried by the history, including its exact publication or pack
version when present. Recipes are also resolved by stable identity.

## Minecraft 1.20.1 mod

The recipe-tree screen has a **Share** button.

- **Share** copies the active tree-history snapshot to the system clipboard and writes
  `config/recipe-tree-shares/current-tree-history.mrtree.json`.
- Histories with multiple starting outputs include each independent root and its selected recipe
  branches in the same file.
- The confirmation screen tells the sender where the recipient should paste the history on the site
  and identifies the launcher-resolved pack version they must select.

The in-game viewer currently imports ingredient-directed trees. Mob-drop and mining sources from a
web tree are left collapsed because the JEI planner does not represent those as recipe pages.

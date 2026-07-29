import type {DatasetDescriptor} from './datasetCatalog';
import type {Recipe} from '../types';

export const MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID =
  '04c674ab74eeeaea151c9b985191f09e2be42156a879bb0493e2e29f94f3d46a';

/**
 * RecipeStages assignments from MeatballCraft 0.18.6's authoritative
 * CraftTweaker scripts, checked against every corresponding recipe ID in the
 * immutable publication above.
 */
export const MEATBALLCRAFT_RECIPE_STAGES: Readonly<Record<string, string>> = Object.freeze({
  'crafttweaker:activatecursedjewel_staged': 'activatecursedjewel_stage',
  'crafttweaker:addnetherskyamber': 'bloodmasterstage',
  'crafttweaker:addrealmstoneaoa': 'divinestage',
  'crafttweaker:addtwilightclock': 'divinestage',
  'crafttweaker:arbiterguard1_staged': 'arbiterguard1_stage',
  'crafttweaker:arbiterguard2_staged': 'arbiterguard2_stage',
  'crafttweaker:arbiterguard3_staged': 'arbiterguard3_stage',
  'crafttweaker:arbiterguard4_staged': 'arbiterguard4_stage',
  'crafttweaker:arbiterguard5_staged': 'arbiterguard5_stage',
  'crafttweaker:arbiterguard6_staged': 'arbiterguard6_stage',
  'crafttweaker:arbiterguard7_staged': 'arbiterguard7_stage',
  'crafttweaker:arbiterguard8_staged': 'arbiterguard8_stage',
  'crafttweaker:arbiterguard9_staged': 'arbiterguard9_stage',
  'crafttweaker:arbiterrelic1_staged': 'arbiterrelic1_stage',
  'crafttweaker:arbiterrelic2_staged': 'arbiterrelic2_stage',
  'crafttweaker:arbiterrelic3_staged': 'arbiterrelic3_stage',
  'crafttweaker:arbiterrelic4_staged': 'arbiterrelic4_stage',
  'crafttweaker:arbiterrelic5_staged': 'arbiterrelic5_stage',
  'crafttweaker:arbiterrelic6_staged': 'arbiterrelic6_stage',
  'crafttweaker:arbiterrelic7_staged': 'arbiterrelic7_stage',
  'crafttweaker:attunethefluxconstruct_staged': 'attunethefluxconstruct_stage',
  'crafttweaker:barongempuzzle_staged': 'barongempuzzle_stage',
  'crafttweaker:brightsteel_chest_gs': 'brightsteelforging',
  'crafttweaker:brightsteel_feet_gs': 'brightsteelforging',
  'crafttweaker:brightsteel_head_gs': 'brightsteelforging',
  'crafttweaker:brightsteel_legs_gs': 'brightsteelforging',
  'crafttweaker:catalizedatore_staged': 'catalizedatore_stage',
  'crafttweaker:combopotionsicy_staged': 'combopotionsicy_stage',
  'crafttweaker:ct_shaped-77837803': 'brightsteelforging',
  'crafttweaker:ct_shaped209258480': 'brightsteelforging',
  'crafttweaker:ct_shaped379964378': 'brightsteelforging',
  'crafttweaker:ct_shaped387723737': 'brightsteelforging',
  'crafttweaker:ct_shaped876563354': 'brightsteelforging',
  'crafttweaker:ct_shapeless-515093402': 'divinestage',
  'crafttweaker:ct_shapeless1544047514': 'brightsteelforging',
  'crafttweaker:ct_shapeless584517755': 'draconicstage',
  'crafttweaker:deeplandspuzzlestone_staged': 'deeplandspuzzlestone_stage',
  'crafttweaker:dragonscalesheart_staged': 'dragonscalesheart_stage',
  'crafttweaker:dupestandardlove_staged': 'dupestandardlove_stage',
  'crafttweaker:dupestandardloved_staged': 'dupestandardloved_stage',
  'crafttweaker:dupestandardlover_staged': 'dupestandardlover_stage',
  'crafttweaker:eldritchcharmsfordaemc_staged': 'eldritchcharmsfordaemc_stage',
  'crafttweaker:eldunaricallstonepuzzle_staged': 'eldunaricallstonepuzzle_stage',
  'crafttweaker:entropicstonepuzzle_staged': 'entropicstonepuzzle_stage',
  'crafttweaker:europapuzzle1_staged': 'europapuzzle1_stage',
  'crafttweaker:europapuzzle2_staged': 'europapuzzle2_stage',
  'crafttweaker:europapuzzle3_staged': 'europapuzzle3_stage',
  'crafttweaker:europapuzzle4_staged': 'europapuzzle4_stage',
  'crafttweaker:europapuzzle5_staged': 'europapuzzle5_stage',
  'crafttweaker:europapuzzle6_staged': 'europapuzzle6_stage',
  'crafttweaker:europapuzzle7_staged': 'europapuzzle7_stage',
  'crafttweaker:europapuzzlecombine_staged': 'europapuzzlecombine_stage',
  'crafttweaker:ezmithminiteboii_staged': 'ezmithminiteboii_stage',
  'crafttweaker:ezpzwandsbbynos': 'hardmode',
  'crafttweaker:falacerpuzzle_staged': 'falacerpuzzle_stage',
  'crafttweaker:furnaceguyfinalform_staged': 'furnaceguyfinalform_stage',
  'crafttweaker:furnaceguylives_staged': 'furnaceguylives_stage',
  'crafttweaker:gluttonybanner_staged': 'gluttonybanner_stage',
  'crafttweaker:hatorpuzzle_staged': 'hatorpuzzle_stage',
  'crafttweaker:haumeapuzzle_staged': 'haumeapuzzle_stage',
  'crafttweaker:imscottmalkinson': 'hardmode',
  'crafttweaker:infusethatstormhoms_staged': 'infusethatstormhoms_stage',
  'crafttweaker:lawkingofpirates_staged': 'lawkingofpirates_stage',
  'crafttweaker:lawkingofpiratesnone_staged': 'lawkingofpiratesnone_stage',
  'crafttweaker:lawkingofpiratesone_staged': 'lawkingofpiratesone_stage',
  'crafttweaker:leftsacredsaplingmystery_staged': 'leftsacredsaplingmystery_stage',
  'crafttweaker:make_table_basic': 'extendedcrafting',
  'crafttweaker:makeapothecarykey_staged': 'makeapothecarykey_stage',
  'crafttweaker:makebettergrandcrystals': 'hardmode',
  'crafttweaker:makedivinestonesedna': 'sedna',
  'crafttweaker:makemarkofsacrifice': 'lostcitiesstage',
  'crafttweaker:makerheniapuzzlestep1_staged': 'makerheniapuzzlestep1_stage',
  'crafttweaker:makerosidianblend_staged': 'makerosidianblend_stage',
  'crafttweaker:maketaintedloop': 'hardmode',
  'crafttweaker:makethatanimatorboii_staged': 'makethatanimatorboii_stage',
  'crafttweaker:makethattributeboi_staged': 'makethattributeboi_stage',
  'crafttweaker:makeweaponsmitharbiter': 'hardmode',
  'crafttweaker:meatballmonument1_staged': 'meatballmonument1_stage',
  'crafttweaker:meatballmonument2_staged': 'meatballmonument2_stage',
  'crafttweaker:meatballmonument3_staged': 'meatballmonument3_stage',
  'crafttweaker:meatballmonument4_staged': 'meatballmonument4_stage',
  'crafttweaker:meatballmonument5_staged': 'meatballmonument5_stage',
  'crafttweaker:meatballmonument6_staged': 'meatballmonument6_stage',
  'crafttweaker:meatballmonument7_staged': 'meatballmonument7_stage',
  'crafttweaker:meatballmonument8_staged': 'meatballmonument8_stage',
  'crafttweaker:meatballmonument9_staged': 'meatballmonument9_stage',
  'crafttweaker:minor_vethea_binding': 'MinorVetheaBinding',
  'crafttweaker:modular_controller': 'modularstage',
  'crafttweaker:mushroompuzzle_staged': 'mushroompuzzle_stage',
  'crafttweaker:mysteriummysteryez_staged': 'mysteriummysteryez_stage',
  'crafttweaker:orcuspuzzle1_staged': 'orcuspuzzle1_stage',
  'crafttweaker:orcuspuzzle2_staged': 'orcuspuzzle2_stage',
  'crafttweaker:osirisarmor1_staged': 'osirisarmor1_stage',
  'crafttweaker:osirisarmor2_staged': 'osirisarmor2_stage',
  'crafttweaker:osirisarmor3_staged': 'osirisarmor3_stage',
  'crafttweaker:osirisarmor4_staged': 'osirisarmor4_stage',
  'crafttweaker:pressspice_staged': 'pressspice_stage',
  'crafttweaker:ptahpuzzlepharos_staged': 'ptahpuzzlepharos_stage',
  'crafttweaker:rainbowstone_staged': 'rainbowstone_stage',
  'crafttweaker:recursion_focusing_fabrial_staged': 'recursion_focusing_fabrial_stage',
  'crafttweaker:rightbannersboi_staged': 'rightbannersboi_stage',
  'crafttweaker:rightsacredsaplingmystery_staged': 'rightsacredsaplingmystery_stage',
  'crafttweaker:runandormysteryez_staged': 'runandormysteryez_stage',
  'crafttweaker:runesofliberation1_staged': 'runesofliberation1_stage',
  'crafttweaker:secretbookrecycler_staged': 'secretbookrecycler_stage',
  'crafttweaker:secretcobblebranch_staged': 'secretcobblebranch_stage',
  'crafttweaker:sednapuzzle_staged': 'sednapuzzle_stage',
  'crafttweaker:sharpboneprecasia_staged': 'sharpboneprecasia_stage',
  'crafttweaker:sparkledaoak_staged': 'sparkledaoak_stage',
  'crafttweaker:stellararmorhidden_staged': 'stellararmorhidden_stage',
  'crafttweaker:superforesteradventure': 'hardmode',
  'crafttweaker:tabletcomboftw_staged': 'tabletcomboftw_stage',
  'crafttweaker:truetongue_staged': 'truetongue_stage',
  'crafttweaker:trulyaterriblesacrifice_staged': 'trulyaterriblesacrifice_stage',
  'crafttweaker:undeadfuelpuzzle_staged': 'undeadfuelpuzzle_stage',
  'crafttweaker:undeadsoiltravixte_staged': 'undeadsoiltravixte_stage',
  'crafttweaker:vetheapuzzle_staged': 'vetheapuzzle_stage',
  'crafttweaker:warrenblindfoldpzzl_staged': 'warrenblindfoldpzzl_stage',
  'draconicevolution:fusion_crafting_core': 'draconicstage',
});

const RECIPE_STAGE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}$/;
let loggedCompatibilityPublication = false;

export function isValidRecipeStage(value: unknown): value is string {
  return typeof value === 'string' && RECIPE_STAGE_PATTERN.test(value);
}

export function applyRecipeStageMetadata(
  recipe: Recipe,
  descriptor: DatasetDescriptor,
): Recipe {
  if (recipe.stage !== undefined && !isValidRecipeStage(recipe.stage)) {
    throw new Error(
      `Recipe ${JSON.stringify(recipe.id ?? '<unknown>')} has an invalid stage identifier.`,
    );
  }
  if (
    descriptor.slug !== 'meatballcraft' ||
    descriptor.publicationId !== MEATBALLCRAFT_STAGE_COMPATIBILITY_PUBLICATION_ID ||
    !recipe.id
  ) {
    return recipe;
  }
  const compatibleStage = MEATBALLCRAFT_RECIPE_STAGES[recipe.id];
  if (!compatibleStage) return recipe;
  if (recipe.stage !== undefined && recipe.stage !== compatibleStage) {
    throw new Error(
      `Recipe ${JSON.stringify(recipe.id)} declares stage ${JSON.stringify(recipe.stage)} but ` +
        `the immutable MeatballCraft compatibility manifest declares ${JSON.stringify(compatibleStage)}.`,
    );
  }
  if (!loggedCompatibilityPublication) {
    loggedCompatibilityPublication = true;
    console.info(
      'Applying the verified MeatballCraft 0.18.6 RecipeStages compatibility manifest.',
      {
        publicationId: descriptor.publicationId,
        recipeAssignments: Object.keys(MEATBALLCRAFT_RECIPE_STAGES).length,
      },
    );
  }
  return recipe.stage === compatibleStage ? recipe : {...recipe, stage: compatibleStage};
}

export function recipeStageLabel(stage: string): string {
  if (!isValidRecipeStage(stage)) {
    throw new Error(`Cannot display invalid recipe stage ${JSON.stringify(stage)}.`);
  }
  return stage;
}

export function isRecipeVisibleForStages(
  recipe: Recipe | undefined,
  hiddenStages: ReadonlySet<string>,
): boolean {
  return Boolean(recipe && (!recipe.stage || !hiddenStages.has(recipe.stage)));
}

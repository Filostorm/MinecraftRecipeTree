import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {recipeImagePath, useData} from '../data/DataContext';
import {recipePresentationKind} from '../data/recipePresentation';
import {displayIngredientName} from '../data/ingredientTags';
import {
  formatIngredientQuantityPrefix,
  shouldShowIngredientQuantity,
} from '../data/ingredientQuantities';
import {recipeDisplayTitle} from '../data/recipeTitles';
import {theme} from '../theme';
import {slotSummary} from '../data/slotSummary';
import {Recipe} from '../types';
import {useUi} from '../ui/UiContext';
import {signalTarget} from '../analytics/signal';
import {
  materialInputSummary,
  type GraphDirection,
} from '../graph/direction';
import {ItemIcon, pixelated} from './ItemIcon';
import {RecipePreviewImage} from './RecipePreviewImage';
import {
  RECIPE_CARD_BORDER_WIDTH,
  RECIPE_CARD_PADDING,
  responsiveRecipePreviewSize,
} from './recipePreviewSizing';

export function RecipeCard({
  recipe,
  dir,
  catTitle,
  onPress,
  graphDirection = 'inputs',
  actionSubject,
  usageOutputSubject,
  availableCardWidth,
  grouped = false,
}: {
  recipe: Recipe;
  dir: string;
  catTitle?: string;
  onPress?: () => void;
  graphDirection?: GraphDirection;
  /** Item name anchoring the graph request, used by the visible action hint. */
  actionSubject?: string;
  /** Primary recipe product that will replace the usage anchor as the graph root. */
  usageOutputSubject?: string;
  /** Measured width of the full-width recipe-list container in CSS/layout pixels. */
  availableCardWidth: number;
  /** Render inside a category frame that supplies the outer border and corner radius. */
  grouped?: boolean;
}) {
  const data = useData();
  const presentation = recipePresentationKind(recipe);
  if (presentation === 'failure') {
    return (
      <View style={styles.card}>
        <Text style={styles.errText}>recipe failed to export{recipe.id ? ` (${recipe.id})` : ''}</Text>
      </View>
    );
  }
  const previewSize = responsiveRecipePreviewSize(
    recipe.w ?? 160,
    recipe.h ?? 60,
    data.manifest.settings.recipeScale,
    availableCardWidth,
  );
  const inputs = materialInputSummary(recipe);
  const outputs = slotSummary(recipe.out);
  const displayTitle = catTitle ? recipeDisplayTitle(catTitle, recipe) : undefined;
  return (
    <TouchableOpacity
      {...(onPress ? signalTarget(`recipe.open-graph.${graphDirection}`) : {})}
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityLabel={
        onPress
          ? graphDirection === 'outputs'
            ? `Make ${usageOutputSubject ?? 'the primary product'} the tree root using ${actionSubject ?? 'this item'} through ${displayTitle ?? 'this recipe'}`
            : `Start an ingredient tree for ${actionSubject ?? 'this item'} with ${displayTitle ?? 'this recipe'}`
          : undefined
      }
      activeOpacity={onPress ? 0.72 : 1}
      disabled={!onPress}
      onPress={onPress}
      style={[
        styles.card,
        onPress && styles.cardAction,
        grouped && styles.cardGrouped,
      ]}>
      {displayTitle ? <Text style={styles.catTitle}>{displayTitle}</Text> : null}
      {presentation === 'image' && recipe.img ? (
        <RecipePreviewImage
          uri={data.imageUrl(recipeImagePath(dir, recipe.img))!}
          context={recipe.id ?? `${catTitle ?? dir} recipe`}
          style={[
            {width: previewSize.width, height: previewSize.height},
            styles.recipeImage,
            pixelated as object,
          ]}
          resizeMode="contain"
        />
      ) : (
        <Text style={styles.previewUnavailable}>
          Structured recipe · layout preview unavailable
        </Text>
      )}
      {(inputs.length > 0 || outputs.length > 0) && (
        <View style={styles.recipeItems}>
          {inputs.map(item => (
            <ItemChip
              key={`input-${item.key}`}
              itemKey={item.key}
              amount={item.amount}
              variableAmount={item.variableAmount}
              variants={item.variants}
              tag={item.tag}
              probability={item.probability}
              probabilityRole="consume"
              interactive={!onPress}
            />
          ))}
          <Text style={styles.recipeArrow}>→</Text>
          {outputs.map(item => (
            <ItemChip
              key={`output-${item.key}`}
              itemKey={item.key}
              amount={item.amount}
              variableAmount={item.variableAmount}
              variants={item.variants}
              tag={item.tag}
              probability={item.probability}
              probabilityRole="produce"
              highlight
              interactive={!onPress}
            />
          ))}
        </View>
      )}
      {recipe.id ? (
        <Text style={styles.recipeId} numberOfLines={1}>
          {recipe.id}
        </Text>
      ) : null}
      {onPress ? (
        <Text style={styles.cardActionHint}>
          {graphDirection === 'outputs'
            ? `Tap to make ${usageOutputSubject ?? 'the primary product'} the new root`
            : 'Tap to add recipe to the graph'}
        </Text>
      ) : null}
    </TouchableOpacity>
  );
}

export function ItemChip({
  itemKey,
  amount,
  variableAmount,
  variants,
  tag,
  probability,
  probabilityRole = 'produce',
  highlight,
  interactive = true,
  onPress,
  accessibilityLabel,
}: {
  itemKey: string;
  amount?: number | null;
  variableAmount?: boolean;
  variants?: number;
  tag?: string;
  /** Undefined is deterministic; null is an unresolved stochastic aggregate. */
  probability?: number | null;
  /** Whether the occurrence probability describes input consumption or output production. */
  probabilityRole?: 'consume' | 'produce';
  highlight?: boolean;
  interactive?: boolean;
  /** Overrides the normal item-detail action, for contextual ingredient selection. */
  onPress?: () => void;
  accessibilityLabel?: string;
}) {
  const data = useData();
  const {openItem} = useUi();
  const item = data.itemsByKey.get(itemKey);
  const name = item?.n ?? itemKey.split('|').pop() ?? itemKey;
  const displayName = displayIngredientName(
    name,
    tag,
    data.descriptor.minecraftVersion,
  );
  const content = (
    <>
      <ItemIcon item={item} itemKey={itemKey} size={16} />
      <Text style={styles.chipText} numberOfLines={1}>
        {amount !== undefined && shouldShowIngredientQuantity(itemKey, amount)
          ? `${formatIngredientQuantityPrefix(itemKey, amount)} `
          : ''}
        {displayName}
        {!tag && variants != null && variants > 1
          ? ` (+${variants - 1} alternatives${variableAmount ? '; quantities vary' : ''})`
          : variableAmount
            ? ' (alternative quantities vary)'
            : ''}
        {probability === null
          ? ` · stochastic ${probabilityRole} chance unknown`
          : probability === undefined
            ? ''
            : ` · ${String(Math.round(probability * 10_000) / 100)}% ${probabilityRole} chance`}
      </Text>
    </>
  );
  if (!interactive) {
    return <View style={[styles.chip, highlight && styles.chipHighlight]}>{content}</View>;
  }
  return (
    <TouchableOpacity
      {...signalTarget(onPress ? 'graph.source-picker.alternative.open' : 'item-detail.item.open')}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? `Open ${displayName}`}
      style={[styles.chip, highlight && styles.chipHighlight]}
      onPress={event => {
        event.stopPropagation();
        if (onPress) onPress();
        else openItem(itemKey);
      }}>
      {content}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.panelAlt,
    borderColor: theme.border,
    borderWidth: RECIPE_CARD_BORDER_WIDTH,
    borderRadius: 10,
    padding: RECIPE_CARD_PADDING,
    marginBottom: 10,
    alignSelf: 'flex-start',
    maxWidth: '100%',
  },
  cardAction: {borderColor: theme.borderLight},
  cardGrouped: {
    alignSelf: 'stretch',
    backgroundColor: 'transparent',
    borderWidth: 0,
    borderRadius: 0,
    marginBottom: 0,
  },
  cardActionHint: {
    color: theme.accent,
    fontSize: 10,
    fontWeight: '700',
    marginTop: 8,
  },
  catTitle: {color: theme.textDim, fontSize: 11, marginBottom: 6},
  recipeImage: {borderRadius: 4},
  previewUnavailable: {
    color: theme.textDim,
    fontSize: 11,
    fontStyle: 'italic',
    marginBottom: 8,
  },
  recipeItems: {
    marginTop: 8,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 6,
  },
  recipeArrow: {color: theme.textDim, fontSize: 14},
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.panel,
    borderColor: theme.border,
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 3,
    gap: 5,
    maxWidth: 240,
  },
  chipHighlight: {borderColor: theme.accent},
  chipText: {color: theme.text, fontSize: 11},
  errText: {color: theme.danger, fontSize: 12},
  recipeId: {color: theme.textDim, fontSize: 10, marginTop: 6},
});

import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {formatIngredientQuantity} from '../data/ingredientQuantities';
import {PROJECTE_EMC_KEY} from '../data/projecteEmc';
import {theme} from '../theme';
import {ItemIcon} from './ItemIcon';

/** A code-rendered preview keeps 11,000+ synthetic EMC recipes out of the image pack. */
export function ProjecteEmcPreview({
  emc,
  outputItemKey,
  outputAmount,
}: {
  emc: number;
  outputItemKey: string;
  outputAmount: number;
}) {
  return (
    <View
      accessibilityLabel={`${formatIngredientQuantity(PROJECTE_EMC_KEY, emc)} creates ${formatIngredientQuantity(outputItemKey, outputAmount)}`}
      style={styles.preview}>
      <View style={styles.ingredient}>
        <ItemIcon itemKey={PROJECTE_EMC_KEY} size={32} />
        <Text style={styles.amount} numberOfLines={1}>
          {formatIngredientQuantity(PROJECTE_EMC_KEY, emc)}
        </Text>
      </View>
      <Text style={styles.arrow}>→</Text>
      <View style={styles.ingredient}>
        <ItemIcon itemKey={outputItemKey} size={32} />
        <Text style={styles.amount} numberOfLines={1}>
          {formatIngredientQuantity(outputItemKey, outputAmount)}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  preview: {
    width: 160,
    height: 60,
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panel,
  },
  ingredient: {
    width: 52,
    alignItems: 'center',
    gap: 2,
  },
  amount: {
    maxWidth: 52,
    color: theme.textDim,
    fontSize: 8,
    fontWeight: '700',
  },
  arrow: {
    color: theme.accent,
    fontSize: 18,
    fontWeight: '800',
  },
});

import React, {useMemo, useState} from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {signalTarget} from '../analytics/signal';
import {theme} from '../theme';
import type {RecipeStructure} from '../types';
import {contentTextScale} from '../ui/contentZoom';
import {ItemIcon} from './ItemIcon';
import {itemIconSizeForContentScale} from './itemIconSizing';
import {useData} from '../data/DataContext';
import {useUi} from '../ui/UiContext';
import {
  MAX_MULTIBLOCK_PREVIEW_CELLS,
  previewStructureCells,
  projectStructureCells,
} from './multiblockProjection';

const PREVIEW_HEIGHT = 220;

export function MultiblockPreview({
  structure,
  availableWidth,
  contentScale = 1,
}: {
  structure: RecipeStructure;
  availableWidth: number;
  contentScale?: number;
}) {
  const data = useData();
  const {openItem} = useUi();
  const [rotation, setRotation] = useState(0);
  const targetWidth = Math.max(180, Math.min(860, availableWidth));
  const previewHeight = Math.max(
    PREVIEW_HEIGHT,
    Math.min(PREVIEW_HEIGHT * 3, PREVIEW_HEIGHT * contentScale),
  );
  const textScale = contentTextScale(contentScale);
  const [measuredWidth, setMeasuredWidth] = useState<number | null>(null);
  const width = Math.max(180, Math.min(targetWidth, measuredWidth ?? targetWidth));
  const visibleCells = useMemo(
    () => previewStructureCells(structure.cells),
    [structure.cells],
  );
  const projected = useMemo(
    () => projectStructureCells(visibleCells, width, previewHeight, rotation),
    [previewHeight, rotation, visibleCells, width],
  );
  const clipped = visibleCells.length < structure.cells.length;

  return (
    <View
      style={[styles.wrapper, {width: targetWidth}]}
      onLayout={event => {
        const nextWidth = event.nativeEvent.layout.width;
        if (Math.abs(nextWidth - (measuredWidth ?? targetWidth)) > 0.5) {
          setMeasuredWidth(nextWidth);
        }
      }}>
      <View style={styles.headingRow}>
        <View>
          <Text style={styles.heading}>MULTIBLOCK PREVIEW</Text>
          <Text style={styles.summary}>
            {structure.size.join(' × ')} · {structure.total.toLocaleString()} blocks ·{' '}
            {structure.blocks.length.toLocaleString()} types · one valid layout
          </Text>
        </View>
        <View style={styles.rotationControls}>
          <TouchableOpacity
            {...signalTarget('recipe.multiblock.rotate-left')}
            accessibilityRole="button"
            accessibilityLabel="Rotate multiblock preview left"
            style={styles.rotateButton}
            onPress={event => {
              event.stopPropagation();
              setRotation(value => value - 1);
            }}>
            <Text style={styles.rotateText}>↺</Text>
          </TouchableOpacity>
          <TouchableOpacity
            {...signalTarget('recipe.multiblock.rotate-right')}
            accessibilityRole="button"
            accessibilityLabel="Rotate multiblock preview right"
            style={styles.rotateButton}
            onPress={event => {
              event.stopPropagation();
              setRotation(value => value + 1);
            }}>
            <Text style={styles.rotateText}>↻</Text>
          </TouchableOpacity>
        </View>
      </View>
      <View
        accessible
        accessibilityRole="image"
        accessibilityLabel={`${structure.size.join(' by ')} multiblock containing ${structure.total} blocks`}
        style={[styles.canvas, {width, height: previewHeight}]}>
        <View pointerEvents="none" style={styles.groundShadow} />
        {projected.map((cell, index) => {
          const [x, y, z, itemKey] = cell.source;
          return (
            <View
              key={`${x}:${y}:${z}:${itemKey}:${index}`}
              style={[
                styles.blockSprite,
                {
                  left: cell.left,
                  top: cell.top,
                  width: cell.size,
                  height: cell.size,
                  zIndex: cell.layer,
                },
              ]}>
              <ItemIcon
                item={data.itemsByKey.get(itemKey)}
                itemKey={itemKey}
                size={cell.size}
              />
              {itemKey === structure.controller ? (
                <View pointerEvents="none" style={styles.controllerMarker} />
              ) : null}
            </View>
          );
        })}
      </View>
      {clipped ? (
        <Text style={styles.previewNote}>
          Exterior preview shows {MAX_MULTIBLOCK_PREVIEW_CELLS} of{' '}
          {structure.cells.length.toLocaleString()} positions; counts below are exact.
        </Text>
      ) : null}
      <Text style={styles.partsHeading}>BLOCK COUNT · ONE VALID BUILD</Text>
      <View style={styles.parts}>
        {structure.blocks.map(([itemKey, count]) => {
          const item = data.itemsByKey.get(itemKey);
          const name = item?.n ?? itemKey.split('|').pop() ?? itemKey;
          return (
            <TouchableOpacity
              {...signalTarget('recipe.multiblock.part.open')}
              key={itemKey}
              accessibilityRole="button"
              accessibilityLabel={`Open ${name}, ${count} blocks required`}
              style={[
                styles.partChip,
                {
                  gap: Math.max(4, 5 * contentScale),
                  maxWidth: 240 * contentScale,
                  paddingHorizontal: Math.max(5, 6 * contentScale),
                  paddingVertical: Math.max(3, 3 * contentScale),
                },
                itemKey === structure.controller && styles.partChipController,
              ]}
              onPress={event => {
                event.stopPropagation();
                openItem(itemKey);
              }}>
              <ItemIcon
                item={item}
                itemKey={itemKey}
                size={itemIconSizeForContentScale(contentScale)}
              />
              <Text
                style={[
                  styles.partText,
                  {fontSize: Math.max(9, 11 * textScale)},
                ]}
                numberOfLines={1}>
                {count.toLocaleString()}× {name}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
      <Text style={styles.partsNote}>
        The green dot marks the controller. Alternative positions use the same deterministic
        representative block as the in-game structure preview.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {marginTop: 10, maxWidth: '100%'},
  headingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 7,
  },
  heading: {color: theme.textDim, fontSize: 10, fontWeight: '800', letterSpacing: 0.5},
  summary: {color: theme.text, fontSize: 11, marginTop: 3},
  rotationControls: {flexDirection: 'row', gap: 5},
  rotateButton: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 30,
    height: 30,
    borderRadius: 7,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  rotateText: {color: theme.accent, fontSize: 18, lineHeight: 20},
  canvas: {
    overflow: 'hidden',
    position: 'relative',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: '#0b1016',
  },
  groundShadow: {
    position: 'absolute',
    left: '18%',
    right: '18%',
    bottom: 14,
    height: 18,
    borderRadius: 999,
    backgroundColor: 'rgba(0, 0, 0, 0.42)',
    transform: [{scaleY: 0.45}],
  },
  blockSprite: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  controllerMarker: {
    position: 'absolute',
    top: 1,
    right: 1,
    width: 7,
    height: 7,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#c8ffe0',
    backgroundColor: theme.accent,
  },
  previewNote: {color: theme.textDim, fontSize: 9, marginTop: 5},
  partsHeading: {
    color: theme.textDim,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
    marginTop: 11,
    marginBottom: 6,
  },
  parts: {flexDirection: 'row', flexWrap: 'wrap', gap: 6},
  partChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    maxWidth: 240,
    paddingHorizontal: 6,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.panel,
  },
  partChipController: {borderColor: theme.accent},
  partText: {color: theme.text, fontSize: 11},
  partsNote: {color: theme.textDim, fontSize: 9, lineHeight: 13, marginTop: 6},
});

import React, {useMemo, useRef, useState} from 'react';
import {PanResponder, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {signalTarget} from '../analytics/signal';
import {theme} from '../theme';
import type {RecipeStructure} from '../types';
import {contentTextScale} from '../ui/contentZoom';
import {ItemIcon} from './ItemIcon';
import {itemIconSizeForContentScale} from './itemIconSizing';
import {useData} from '../data/DataContext';
import {useUi} from '../ui/UiContext';
import {
  cellsForStructureLayer,
  MAX_MULTIBLOCK_PREVIEW_CELLS,
  previewStructureCells,
  projectStructureCells,
  screenPanOffset,
  structureLayerLevels,
} from './multiblockProjection';

const PREVIEW_HEIGHT = 220;

export function MultiblockPreview({
  structure,
  availableWidth,
  contentScale = 1,
  compact = false,
}: {
  structure: RecipeStructure;
  availableWidth: number;
  contentScale?: number;
  /** Render only the placed-block canvas for an expanded graph node. */
  compact?: boolean;
}) {
  const data = useData();
  const {openItem} = useUi();
  const [rotation, setRotation] = useState(0);
  const [selectedLayer, setSelectedLayer] = useState<number | null>(null);
  const [panOffset, setPanOffsetState] = useState({x: 0, y: 0});
  const panOffsetRef = useRef(panOffset);
  const panOriginRef = useRef(panOffset);
  const setPanOffset = (next: {x: number; y: number}) => {
    panOffsetRef.current = next;
    setPanOffsetState(next);
  };
  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => false,
        onMoveShouldSetPanResponder: (_event, gesture) =>
          Math.abs(gesture.dx) > 3 || Math.abs(gesture.dy) > 3,
        onMoveShouldSetPanResponderCapture: (_event, gesture) =>
          Math.abs(gesture.dx) > 3 || Math.abs(gesture.dy) > 3,
        onPanResponderGrant: event => {
          event.stopPropagation();
          panOriginRef.current = panOffsetRef.current;
        },
        onPanResponderMove: (event, gesture) => {
          event.stopPropagation();
          setPanOffset(screenPanOffset(panOriginRef.current, gesture.dx, gesture.dy));
        },
        onPanResponderRelease: event => event.stopPropagation(),
        onPanResponderTerminate: event => event.stopPropagation(),
        onPanResponderTerminationRequest: () => false,
      }),
    [],
  );
  const targetWidth = Math.max(180, Math.min(860, availableWidth));
  const previewHeight = compact
    ? PREVIEW_HEIGHT
    : Math.max(
        PREVIEW_HEIGHT,
        Math.min(PREVIEW_HEIGHT * 3, PREVIEW_HEIGHT * contentScale),
      );
  const textScale = contentTextScale(contentScale);
  const [measuredWidth, setMeasuredWidth] = useState<number | null>(null);
  const width = Math.max(180, Math.min(targetWidth, measuredWidth ?? targetWidth));
  const layers = useMemo(() => structureLayerLevels(structure.cells), [structure.cells]);
  const effectiveLayer =
    selectedLayer !== null && layers.includes(selectedLayer) ? selectedLayer : null;
  const layerCells = useMemo(
    () => cellsForStructureLayer(structure.cells, effectiveLayer),
    [effectiveLayer, structure.cells],
  );
  const visibleCells = useMemo(
    () => previewStructureCells(layerCells),
    [layerCells],
  );
  const projected = useMemo(
    () => projectStructureCells(visibleCells, width, previewHeight, rotation),
    [previewHeight, rotation, visibleCells, width],
  );
  const clipped = visibleCells.length < layerCells.length;
  const controllerItem = data.itemsByKey.get(structure.controller);
  const machineName =
    controllerItem?.n ?? structure.controller.split('|').pop() ?? structure.controller;
  const layerNumber =
    effectiveLayer === null ? null : layers.indexOf(effectiveLayer) + 1;
  const layerDescription =
    effectiveLayer === null
      ? 'all layers'
      : `layer ${layerNumber} of ${layers.length}`;
  const isCentered = panOffset.x === 0 && panOffset.y === 0;

  return (
    <View
      style={[styles.wrapper, compact && styles.wrapperCompact, {width: targetWidth}]}
      onLayout={event => {
        const nextWidth = event.nativeEvent.layout.width;
        if (Math.abs(nextWidth - (measuredWidth ?? targetWidth)) > 0.5) {
          setMeasuredWidth(nextWidth);
        }
      }}>
      {!compact ? (
        <View style={styles.headingRow}>
          <View>
            <Text style={styles.heading}>MULTIBLOCK PREVIEW</Text>
            <Text style={styles.machineName} numberOfLines={1}>
              {machineName}
            </Text>
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
      ) : null}
      {!compact ? (
        <View style={styles.previewControls}>
          <LayerControls
            layers={layers}
            selectedLayer={effectiveLayer}
            onSelect={setSelectedLayer}
          />
          <View style={styles.panControls}>
            <Text style={styles.panHint}>Drag to pan</Text>
            <TouchableOpacity
              {...signalTarget('recipe.multiblock.pan-reset')}
              accessibilityRole="button"
              accessibilityLabel="Center multiblock preview"
              disabled={isCentered}
              style={[styles.centerButton, isCentered && styles.controlDisabled]}
              onPress={event => {
                event.stopPropagation();
                setPanOffset({x: 0, y: 0});
              }}>
              <Text style={styles.centerButtonText}>Center</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : null}
      <View style={[styles.canvasFrame, {width, height: previewHeight}]}>
        <View
          {...panResponder.panHandlers}
          accessible
          accessibilityRole="image"
          accessibilityLabel={`${machineName}, ${structure.size.join(' by ')} multiblock containing ${structure.total} blocks, showing ${layerDescription}`}
          accessibilityHint="Drag in any screen direction to pan the preview"
          style={[styles.canvas, {width, height: previewHeight}]}>
          <View
            pointerEvents="none"
            style={[
              styles.panLayer,
              {transform: [{translateX: panOffset.x}, {translateY: panOffset.y}]},
            ]}>
            <View style={styles.groundShadow} />
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
        </View>
        {compact ? (
          <>
            <View pointerEvents="none" style={styles.compactMachineBadge}>
              <Text style={styles.compactMachineLabel}>MACHINE</Text>
              <Text style={styles.compactMachineName} numberOfLines={1}>
                {machineName}
              </Text>
            </View>
            <View style={styles.compactControls}>
              <LayerControls
                compact
                layers={layers}
                selectedLayer={effectiveLayer}
                onSelect={setSelectedLayer}
              />
              <TouchableOpacity
                {...signalTarget('recipe.multiblock.pan-reset')}
                accessibilityRole="button"
                accessibilityLabel="Center multiblock preview"
                disabled={isCentered}
                style={[styles.compactCenterButton, isCentered && styles.controlDisabled]}
                onPress={event => {
                  event.stopPropagation();
                  setPanOffset({x: 0, y: 0});
                }}>
                <Text style={styles.compactCenterText}>⌖</Text>
              </TouchableOpacity>
            </View>
          </>
        ) : null}
      </View>
      {!compact && clipped ? (
        <Text style={styles.previewNote}>
          Preview shows {MAX_MULTIBLOCK_PREVIEW_CELLS} of{' '}
          {layerCells.length.toLocaleString()} positions; counts below are exact.
        </Text>
      ) : null}
      {!compact ? (
        <>
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
        </>
      ) : null}
    </View>
  );
}

function LayerControls({
  layers,
  selectedLayer,
  onSelect,
  compact = false,
}: {
  layers: number[];
  selectedLayer: number | null;
  onSelect: (layer: number | null) => void;
  compact?: boolean;
}) {
  const selectedIndex = selectedLayer === null ? -1 : layers.indexOf(selectedLayer);
  const selectPrevious = () => {
    if (layers.length === 0) return;
    onSelect(
      selectedIndex < 0
        ? layers[layers.length - 1]
        : layers[Math.max(0, selectedIndex - 1)],
    );
  };
  const selectNext = () => {
    if (layers.length === 0) return;
    onSelect(
      selectedIndex < 0
        ? layers[0]
        : layers[Math.min(layers.length - 1, selectedIndex + 1)],
    );
  };
  const label = selectedLayer === null
    ? 'All layers'
    : `Layer ${selectedIndex + 1}/${layers.length}`;
  const buttonStyle = compact ? styles.compactLayerButton : styles.layerButton;
  const buttonTextStyle = compact ? styles.compactLayerButtonText : styles.layerButtonText;

  return (
    <View
      accessibilityRole="toolbar"
      accessibilityLabel="Multiblock layer controls"
      style={[styles.layerControls, compact && styles.layerControlsCompact]}>
      <TouchableOpacity
        {...signalTarget('recipe.multiblock.layer-all')}
        accessibilityRole="button"
        accessibilityLabel="Show all multiblock layers"
        style={[buttonStyle, selectedLayer === null && styles.controlSelected]}
        onPress={event => {
          event.stopPropagation();
          onSelect(null);
        }}>
        <Text style={buttonTextStyle}>All</Text>
      </TouchableOpacity>
      <TouchableOpacity
        {...signalTarget('recipe.multiblock.layer-previous')}
        accessibilityRole="button"
        accessibilityLabel="Show previous multiblock layer"
        disabled={selectedLayer !== null && selectedIndex <= 0}
        style={[
          buttonStyle,
          selectedLayer !== null && selectedIndex <= 0 && styles.controlDisabled,
        ]}
        onPress={event => {
          event.stopPropagation();
          selectPrevious();
        }}>
        <Text style={buttonTextStyle}>−</Text>
      </TouchableOpacity>
      <View style={compact ? styles.compactLayerReadout : styles.layerReadout}>
        <Text
          style={compact ? styles.compactLayerReadoutText : styles.layerReadoutText}
          numberOfLines={1}>
          {label}
        </Text>
      </View>
      <TouchableOpacity
        {...signalTarget('recipe.multiblock.layer-next')}
        accessibilityRole="button"
        accessibilityLabel="Show next multiblock layer"
        disabled={selectedLayer !== null && selectedIndex >= layers.length - 1}
        style={[
          buttonStyle,
          selectedLayer !== null && selectedIndex >= layers.length - 1 && styles.controlDisabled,
        ]}
        onPress={event => {
          event.stopPropagation();
          selectNext();
        }}>
        <Text style={buttonTextStyle}>+</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {marginTop: 10, maxWidth: '100%'},
  wrapperCompact: {marginTop: 0, alignSelf: 'center'},
  headingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 7,
  },
  heading: {color: theme.textDim, fontSize: 10, fontWeight: '800', letterSpacing: 0.5},
  machineName: {color: theme.accent, fontSize: 13, fontWeight: '800', marginTop: 2},
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
  previewControls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 7,
    marginBottom: 7,
  },
  layerControls: {flexDirection: 'row', alignItems: 'center', gap: 4},
  layerControlsCompact: {gap: 2},
  layerButton: {
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 30,
    height: 28,
    paddingHorizontal: 7,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  layerButtonText: {color: theme.text, fontSize: 11, fontWeight: '800'},
  layerReadout: {
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 82,
    height: 28,
    paddingHorizontal: 7,
    borderRadius: 6,
    backgroundColor: theme.panel,
  },
  layerReadoutText: {color: theme.text, fontSize: 10, fontWeight: '700'},
  controlSelected: {borderColor: theme.accent, backgroundColor: 'rgba(90, 201, 131, 0.16)'},
  controlDisabled: {opacity: 0.35},
  panControls: {flexDirection: 'row', alignItems: 'center', gap: 6},
  panHint: {color: theme.textDim, fontSize: 9},
  centerButton: {
    alignItems: 'center',
    justifyContent: 'center',
    height: 28,
    paddingHorizontal: 8,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: theme.panel,
  },
  centerButtonText: {color: theme.text, fontSize: 10, fontWeight: '700'},
  canvasFrame: {position: 'relative'},
  canvas: {
    overflow: 'hidden',
    position: 'relative',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: '#0b1016',
  },
  panLayer: {position: 'absolute', top: 0, right: 0, bottom: 0, left: 0},
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
  compactMachineBadge: {
    position: 'absolute',
    top: 6,
    left: 6,
    right: 6,
    paddingHorizontal: 7,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: 'rgba(11, 16, 22, 0.88)',
  },
  compactMachineLabel: {
    color: theme.textDim,
    fontSize: 7,
    fontWeight: '800',
    letterSpacing: 0.4,
  },
  compactMachineName: {color: theme.accent, fontSize: 10, fontWeight: '800'},
  compactControls: {
    position: 'absolute',
    left: 6,
    right: 6,
    bottom: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 3,
  },
  compactLayerButton: {
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 22,
    height: 24,
    paddingHorizontal: 4,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: 'rgba(11, 16, 22, 0.92)',
  },
  compactLayerButtonText: {color: theme.text, fontSize: 9, fontWeight: '800'},
  compactLayerReadout: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 62,
    height: 24,
    paddingHorizontal: 3,
    borderRadius: 5,
    backgroundColor: 'rgba(11, 16, 22, 0.92)',
  },
  compactLayerReadoutText: {color: theme.text, fontSize: 8, fontWeight: '700'},
  compactCenterButton: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 24,
    height: 24,
    borderRadius: 5,
    borderWidth: 1,
    borderColor: theme.borderLight,
    backgroundColor: 'rgba(11, 16, 22, 0.92)',
  },
  compactCenterText: {color: theme.accent, fontSize: 13, lineHeight: 15},
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

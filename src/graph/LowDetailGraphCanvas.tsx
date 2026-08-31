import React, {useCallback, useEffect, useRef} from 'react';
import {Platform, StyleSheet, View} from 'react-native';
import {useData} from '../data/DataContext';
import {projecteEmcIconItemKey} from '../data/projecteEmc';
import {resolvedThemeColor} from '../theme';
import type {LaidNode} from './layout';
import {lowDetailRasterGeometry} from './lowDetailRaster';
import type {GraphTransform} from './panGesture';

interface CachedRasterIcon {
  image: HTMLImageElement;
  status: 'loading' | 'ready' | 'failed';
}

interface RasterRenderState {
  nodes: readonly LaidNode[];
  transform: GraphTransform;
  viewport: {w: number; h: number};
}

const FALLBACK_COLORS = [
  '#7d5ba6',
  '#5b8aa6',
  '#5ba67d',
  '#a6915b',
  '#a65b5b',
  '#5b5fa6',
  '#86a65b',
];

function fallbackColor(key: string): string {
  let hash = 0;
  for (let index = 0; index < key.length; index += 1) {
    hash = (hash * 31 + key.charCodeAt(index)) | 0;
  }
  return FALLBACK_COLORS[Math.abs(hash) % FALLBACK_COLORS.length];
}

/**
 * Paint dense far-zoom trees into one viewport-sized canvas. The canvas is
 * intentionally one CSS pixel per bitmap pixel: at this zoom every icon is
 * already only a few pixels wide, while a retina backing store would multiply
 * the per-frame fill cost without adding readable detail.
 */
export const LowDetailGraphCanvas = React.memo(function LowDetailGraphCanvas({
  nodes,
  transform,
  viewport,
}: {
  nodes: readonly LaidNode[];
  transform: GraphTransform;
  viewport: {w: number; h: number};
}) {
  const data = useData();
  const hostRef = useRef<View | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const iconsRef = useRef(new Map<string, CachedRasterIcon>());
  const datasetIdentityRef = useRef(data.datasetIdentity);
  const animationFrameRef = useRef(0);
  const renderStateRef = useRef<RasterRenderState>({nodes, transform, viewport});
  renderStateRef.current = {nodes, transform, viewport};

  const clearIconCache = useCallback(() => {
    for (const icon of iconsRef.current.values()) {
      icon.image.onload = null;
      icon.image.onerror = null;
    }
    iconsRef.current.clear();
  }, []);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const state = renderStateRef.current;
    if (state.viewport.w <= 0 || state.viewport.h <= 0) return;

    const width = Math.max(1, Math.ceil(state.viewport.w));
    const height = Math.max(1, Math.ceil(state.viewport.h));
    if (canvas.width !== width) canvas.width = width;
    if (canvas.height !== height) canvas.height = height;
    canvas.style.width = `${String(state.viewport.w)}px`;
    canvas.style.height = `${String(state.viewport.h)}px`;

    const context = canvas.getContext('2d');
    if (!context) {
      console.error('The far-zoom graph canvas could not create a 2D rendering context.');
      return;
    }
    context.clearRect(0, 0, width, height);
    context.imageSmoothingEnabled = false;

    const scheduleDraw = () => {
      if (animationFrameRef.current !== 0) return;
      const animationWindow = canvas.ownerDocument.defaultView;
      if (!animationWindow?.requestAnimationFrame) {
        console.error('The far-zoom graph canvas requires requestAnimationFrame support.');
        draw();
        return;
      }
      animationFrameRef.current = animationWindow.requestAnimationFrame(() => {
        animationFrameRef.current = 0;
        draw();
      });
    };

    for (const node of state.nodes) {
      const geometry = lowDetailRasterGeometry(node, state.transform);
      if (
        geometry.left > width ||
        geometry.top > height ||
        geometry.left + geometry.width < 0 ||
        geometry.top + geometry.height < 0
      ) {
        continue;
      }

      const isRoot = node.item.id === 'root';
      const expanded = node.kind === 'source';
      context.globalAlpha = isRoot ? 1 : 0.78;
      context.fillStyle = resolvedThemeColor(expanded ? 'panelAlt' : 'panel');
      context.fillRect(geometry.left, geometry.top, geometry.width, geometry.height);
      context.strokeStyle = isRoot
        ? resolvedThemeColor('radialRoot')
        : expanded
          ? resolvedThemeColor('accent')
          : resolvedThemeColor('borderLight');
      context.lineWidth = Math.max(1, (isRoot ? 4 : 2) * state.transform.scale);
      context.strokeRect(geometry.left, geometry.top, geometry.width, geometry.height);

      const iconItemKey = projecteEmcIconItemKey(node.item.key);
      const item = data.itemsByKey.get(iconItemKey);
      const uri = data.imageUrl(item?.icon);
      let icon = uri ? iconsRef.current.get(uri) : undefined;
      if (uri && !icon) {
        const image = new Image();
        icon = {image, status: 'loading'};
        iconsRef.current.set(uri, icon);
        image.decoding = 'async';
        image.onload = () => {
          icon!.status = 'ready';
          scheduleDraw();
        };
        image.onerror = () => {
          icon!.status = 'failed';
          console.error('A far-zoom graph item icon failed to load.', {
            itemKey: node.item.key,
            uri,
          });
          data.reportItemIconFailure({
            uri,
            itemKey: item?.k ?? node.item.key,
            label: item?.n ?? node.item.key,
            detail: 'Canvas image load failed.',
          });
          scheduleDraw();
        };
        image.src = uri;
      }

      context.globalAlpha = 0.9;
      if (icon?.status === 'ready') {
        context.drawImage(
          icon.image,
          geometry.iconLeft,
          geometry.iconTop,
          geometry.iconSize,
          geometry.iconSize,
        );
      } else {
        context.fillStyle = fallbackColor(iconItemKey);
        context.fillRect(
          geometry.iconLeft,
          geometry.iconTop,
          geometry.iconSize,
          geometry.iconSize,
        );
      }
    }
    context.globalAlpha = 1;
  }, [data]);

  useEffect(() => {
    if (Platform.OS !== 'web') return undefined;
    const host = hostRef.current as unknown as HTMLElement | null;
    if (!host) {
      console.error('The far-zoom graph canvas host did not mount.');
      return undefined;
    }
    const canvas = host.ownerDocument.createElement('canvas');
    canvas.setAttribute('aria-hidden', 'true');
    canvas.style.position = 'absolute';
    canvas.style.inset = '0';
    canvas.style.pointerEvents = 'none';
    canvas.style.imageRendering = 'pixelated';
    host.appendChild(canvas);
    canvasRef.current = canvas;
    draw();
    return () => {
      const animationWindow = canvas.ownerDocument.defaultView;
      if (animationFrameRef.current !== 0 && animationWindow?.cancelAnimationFrame) {
        animationWindow.cancelAnimationFrame(animationFrameRef.current);
      }
      animationFrameRef.current = 0;
      clearIconCache();
      canvasRef.current = null;
      canvas.remove();
    };
  }, [clearIconCache, draw]);

  useEffect(() => {
    draw();
  }, [draw, nodes, transform, viewport]);

  useEffect(() => {
    if (datasetIdentityRef.current === data.datasetIdentity) return;
    datasetIdentityRef.current = data.datasetIdentity;
    clearIconCache();
    draw();
  }, [clearIconCache, data.datasetIdentity, draw]);

  return (
    <View
      ref={hostRef}
      testID="far-zoom-raster"
      pointerEvents="none"
      style={styles.canvas}
    />
  );
});

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  },
});

import React, {useEffect, useState} from 'react';
import {Image, ImageProps, StyleSheet, Text, View, type ViewStyle} from 'react-native';
import {theme} from '../theme';
import {pixelated} from './ItemIcon';

type RecipePreviewImageProps = Pick<ImageProps, 'style' | 'resizeMode'> & {
  uri: string;
  backgroundUri?: string;
  context: string;
};

/** A JEI layout image that turns transport failures into explicit UI and diagnostics. */
export function RecipePreviewImage({
  uri,
  backgroundUri,
  context,
  style,
  resizeMode = 'contain',
}: RecipePreviewImageProps) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [backgroundUri, uri]);

  if (failed) {
    return <Text style={styles.failure}>JEI layout preview failed to load</Text>;
  }
  const image = (
    <Image
      source={{uri}}
      style={backgroundUri ? [styles.layer, pixelated as object] : style}
      resizeMode={resizeMode}
      onError={event => {
        console.error('Required JEI layout preview failed to load.', {
          context,
          uri,
          error: event.nativeEvent.error,
        });
        setFailed(true);
      }}
    />
  );
  if (!backgroundUri) return image;
  return (
    <View style={[styles.composite, style as ViewStyle]}>
      <Image
        source={{uri: backgroundUri}}
        style={[styles.layer, pixelated as object]}
        resizeMode={resizeMode}
        onError={event => {
          console.error('Required shared JEI layout background failed to load.', {
            context,
            uri: backgroundUri,
            error: event.nativeEvent.error,
          });
          setFailed(true);
        }}
      />
      {image}
    </View>
  );
}

const styles = StyleSheet.create({
  failure: {color: theme.danger, fontSize: 11, fontWeight: '600'},
  composite: {overflow: 'hidden'},
  layer: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    width: '100%',
    height: '100%',
  },
});

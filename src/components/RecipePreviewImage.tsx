import React, {useEffect, useState} from 'react';
import {Image, ImageProps, StyleSheet, Text} from 'react-native';
import {theme} from '../theme';

type RecipePreviewImageProps = Pick<ImageProps, 'style' | 'resizeMode'> & {
  uri: string;
  context: string;
};

/** A JEI layout image that turns transport failures into explicit UI and diagnostics. */
export function RecipePreviewImage({
  uri,
  context,
  style,
  resizeMode = 'contain',
}: RecipePreviewImageProps) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [uri]);

  if (failed) {
    return <Text style={styles.failure}>JEI layout preview failed to load</Text>;
  }
  return (
    <Image
      source={{uri}}
      style={style}
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
}

const styles = StyleSheet.create({
  failure: {color: theme.danger, fontSize: 11, fontWeight: '600'},
});

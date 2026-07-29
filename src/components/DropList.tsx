import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {theme} from '../theme';
import {DropStat} from '../types';
import {ItemChip} from './RecipeCard';

export function formatDropStat(s: DropStat): string {
  const pct = s.c >= 0.999 ? '100%' : s.c < 0.001 ? '<0.1%' : `${(s.c * 100).toFixed(s.c < 0.1 ? 1 : 0)}%`;
  const amount = s.min === s.max ? `${s.min}` : `${s.min}–${s.max}`;
  return `${pct} · ×${amount} · avg ${s.avg.toFixed(2)}/ea`;
}

/** One "item + odds" row, used for mob drops and block drops. */
export function DropRow({stat}: {stat: DropStat}) {
  return (
    <View style={styles.row}>
      <ItemChip itemKey={stat.k} />
      <Text style={styles.stat}>{formatDropStat(stat)}</Text>
    </View>
  );
}

export function DropList({title, drops}: {title?: string; drops: DropStat[]}) {
  if (!drops.length) return null;
  return (
    <View style={styles.list}>
      {title ? <Text style={styles.title}>{title}</Text> : null}
      {drops.map(d => (
        <DropRow key={d.k} stat={d} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: {marginTop: 10, alignSelf: 'stretch'},
  title: {
    color: theme.textDim,
    fontSize: 11,
    textTransform: 'uppercase',
    marginBottom: 6,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 6,
    flexWrap: 'wrap',
  },
  stat: {color: theme.textDim, fontSize: 11},
});

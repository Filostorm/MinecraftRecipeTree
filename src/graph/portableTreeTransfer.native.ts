import * as DocumentPicker from 'expo-document-picker';
import {File} from 'expo-file-system';
import {Share} from 'react-native';
import {MAX_PORTABLE_TREE_BYTES} from './portableTree.ts';

export async function sharePortableTree(
  filename: string,
  json: string,
): Promise<string> {
  const result = await Share.share({title: filename, message: json});
  return result.action === Share.dismissedAction ? 'Share cancelled.' : 'Tree shared.';
}

export async function pickPortableTreeFile(): Promise<string | null> {
  const result = await DocumentPicker.getDocumentAsync({
    type: 'application/json',
    copyToCacheDirectory: true,
    multiple: false,
  });
  if (result.canceled) return null;
  const asset = result.assets[0];
  if (asset.size !== undefined && asset.size > MAX_PORTABLE_TREE_BYTES) {
    throw new Error('The selected tree is larger than the 1 MiB import limit.');
  }
  return new File(asset.uri).text();
}

export async function savePortableTreeToInstance(): Promise<string> {
  throw new Error('Saving into a Minecraft instance is available on desktop web.');
}

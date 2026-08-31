import React, {useState} from 'react';
import {MAX_PORTABLE_TREE_BYTES} from './portableTree.ts';
import {theme} from '../theme.ts';

export function PortableTreeDropZone({
  disabled,
  onDropTree,
}: {
  disabled: boolean;
  onDropTree: (raw: string) => Promise<void>;
}) {
  const [active, setActive] = useState(false);
  const [error, setError] = useState('');

  const readFile = async (file: File) => {
    setError('');
    if (file.size > MAX_PORTABLE_TREE_BYTES) {
      setError('That tree is larger than the 1 MiB import limit.');
      return;
    }
    try {
      await onDropTree(await file.text());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Tree import failed.');
    }
  };

  return (
    <div
      role="region"
      aria-label="Drop a shared recipe tree file to import it"
      onDragEnter={event => {
        event.preventDefault();
        if (!disabled) setActive(true);
      }}
      onDragOver={event => {
        event.preventDefault();
        if (!disabled) event.dataTransfer.dropEffect = 'copy';
      }}
      onDragLeave={event => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setActive(false);
      }}
      onDrop={event => {
        event.preventDefault();
        setActive(false);
        if (disabled) return;
        const file = event.dataTransfer.files[0];
        if (file) void readFile(file);
      }}
      style={{
        minHeight: 76,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 12,
        borderRadius: 9,
        border: `2px dashed ${active ? theme.accent : theme.borderLight}`,
        background: active ? 'rgba(76,175,80,0.12)' : theme.bg,
        color: active ? theme.accent : theme.textDim,
        fontSize: 12,
        fontWeight: 700,
        textAlign: 'center',
      }}>
      {error || (active ? 'Release to import this recipe tree' : 'Drop a .mrtree.json recipe tree here')}
    </div>
  );
}

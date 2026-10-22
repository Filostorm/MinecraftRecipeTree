import React, {useRef} from 'react';
import BottomSheet from '@expo/ui/community/bottom-sheet';
import {theme} from '../theme';

export function GraphSheetSurface({visible, onClose, children}: {
  visible: boolean;
  onClose(): void;
  children: React.ReactNode;
}) {
  const visibleRef = useRef(visible);
  visibleRef.current = visible;
  return (
    <BottomSheet
      index={visible ? 0 : -1}
      snapPoints={['65%', '90%']}
      enableDynamicSizing={false}
      enablePanDownToClose
      backgroundStyle={{backgroundColor: theme.panel}}
      onClose={() => {
        // A controlled close also emits this callback; never toggle the parent back open.
        if (!visibleRef.current) return;
        visibleRef.current = false;
        onClose();
      }}>
      {children}
    </BottomSheet>
  );
}

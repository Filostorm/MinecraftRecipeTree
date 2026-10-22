import React from 'react';
import {Drawer} from 'vaul';
import {theme} from '../theme';

export function GraphSheetSurface({visible, onClose, children}: {
  visible: boolean;
  onClose(): void;
  children: React.ReactNode;
}) {
  return (
    <Drawer.Root open={visible} onOpenChange={open => {if (!open) onClose();}}>
      <Drawer.Portal>
        <Drawer.Overlay style={{position: 'fixed', inset: 0, background: '#0007', zIndex: 999}} />
        <Drawer.Content aria-describedby={undefined} style={{position: 'fixed', bottom: 0, left: 0, right: 0, height: '80dvh', display: 'flex', flexDirection: 'column', background: theme.panel, borderRadius: '16px 16px 0 0', zIndex: 1000}}>
          <Drawer.Title style={{position: 'absolute', width: 1, height: 1, overflow: 'hidden', clipPath: 'inset(50%)'}}>Graph settings</Drawer.Title>
          <Drawer.Handle style={{margin: '12px auto', flexShrink: 0}} />
          {children}
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}

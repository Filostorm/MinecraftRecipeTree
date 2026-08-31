import type {Metadata} from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Minecraft Recipe Tree',
  description:
    'Browse Minecraft items, recipes, mobs, drops, and interactive crafting flowcharts.',
  icons: {
    icon: '/favicon.svg',
  },
};

export default function RootLayout({children}: Readonly<{children: React.ReactNode}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var theme=localStorage.getItem('minecraft-recipe-tree-theme');var font=localStorage.getItem('minecraft-recipe-tree-font');if(theme==='minecraft'){document.documentElement.dataset.mrtTheme='dark';document.documentElement.dataset.mrtFont='minecraft';}else{if(theme==='dark'||theme==='light'){document.documentElement.dataset.mrtTheme=theme;}if(font==='minecraft'){document.documentElement.dataset.mrtFont='minecraft';}}}catch(error){console.error('Theme preference could not be restored before rendering.',error);}})();`,
          }}
        />
      </head>
      <body>{children}</body>
    </html>
  );
}

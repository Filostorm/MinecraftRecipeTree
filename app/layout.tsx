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
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

import "./globals.css";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Nexora AI",
  description: "IA local entrenable con panel web, API propia, cliente Android y despliegue en VPS.",
  manifest: "/manifest.json",
  icons: {
    icon: "/nexora.svg",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}

import "./globals.css";
import type { Metadata, Viewport } from "next";

const siteUrl = new URL(
  process.env.NEXT_PUBLIC_SITE_URL || "https://ghostnexoraai.duckdns.org",
);

export const metadata: Metadata = {
  metadataBase: siteUrl,
  title: {
    default: "Nexora AI — Inteligencia artificial privada en tu VPS",
    template: "%s · Nexora AI",
  },
  description:
    "Asistente de inteligencia artificial local para programación, Android, backend, datos, DevOps y ciberseguridad defensiva. Descarga Nexora AI para Android.",
  applicationName: "Nexora AI",
  authors: [{ name: "Ghost Developer" }],
  creator: "Ghost Developer",
  publisher: "Ghost Developer",
  category: "technology",
  keywords: [
    "Nexora AI",
    "inteligencia artificial privada",
    "IA local",
    "Ollama",
    "asistente de programación",
    "Android",
    "VPS",
    "ciberseguridad defensiva",
  ],
  alternates: { canonical: "/" },
  manifest: "/manifest.json",
  icons: { icon: "/nexora.svg", apple: "/nexora.svg" },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  },
  openGraph: {
    type: "website",
    locale: "es_MX",
    url: "/",
    siteName: "Nexora AI",
    title: "Nexora AI — Tu IA privada para construir software",
    description:
      "Modelos locales, app Android, agentes especializados y despliegue en tu propia VPS.",
    images: [{ url: "/opengraph-image", width: 1200, height: 630, alt: "Nexora AI" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Nexora AI — IA privada en tu VPS",
    description: "Descarga el cliente Android de Nexora AI.",
    images: ["/opengraph-image"],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#07100d",
  colorScheme: "dark",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}

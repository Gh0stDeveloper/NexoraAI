import "./globals.css";
import type { Metadata } from "next";
export const metadata: Metadata = { title: "Nexora AI", description: "IA local entrenable en VPS propia" };
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="es"><body>{children}</body></html>}

import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Documentación",
  description: "Guías de instalación, actualización, Android, seguridad y solución de problemas de Nexora AI.",
  alternates: { canonical: "/docs" },
};

const repo = "https://github.com/Gh0stDeveloper/NexoraAI/blob/main";
const documents = [
  ["Instalación desde cero", "VPS, Docker, dominios, HTTPS y primer modelo.", `${repo}/docs/README-INSTALL.md`],
  ["Actualizar y volver atrás", "Actualización segura, respaldo, verificación y rollback.", `${repo}/docs/README-UPDATE.md`],
  ["Compilar Android en VPS", "SDK persistente, keystore estable y APK release.", `${repo}/docs/ANDROID-BUILD-VPS.md`],
  ["Compatibilidad", "Sistemas operativos, arquitecturas y recursos.", `${repo}/docs/SUPPORT-MATRIX.md`],
  ["Laboratorio aislado", "Modelo de seguridad y activación explícita.", `${repo}/docs/SANDBOX.md`],
  ["Solución de problemas", "Diagnóstico de Docker, Ollama, Nginx, TLS y Android.", `${repo}/docs/TROUBLESHOOTING.md`],
];

export default function DocsPage() {
  return (
    <main className="docs-shell">
      <nav className="site-nav page-width">
        <Link className="brand" href="/"><span className="brand-mark">N</span><span>Nexora AI</span></Link>
        <Link className="nav-download" href="/download">Descargar Android</Link>
      </nav>
      <header className="docs-header page-width">
        <p className="kicker">Centro de ayuda</p>
        <h1>Documentación operativa.</h1>
        <p>Guías versionadas junto al código para instalar, mantener, compilar y recuperar Nexora AI.</p>
      </header>
      <section className="doc-grid page-width">
        {documents.map(([title, body, href]) => (
          <article className="doc-card" key={title}>
            <h2>{title}</h2><p>{body}</p><a href={href}>Abrir guía →</a>
          </article>
        ))}
      </section>
    </main>
  );
}

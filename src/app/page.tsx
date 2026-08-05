import Link from "next/link";

const capabilities = [
  ["Programación", "Full stack, Android, backend, APIs, DevOps y documentación técnica."],
  ["Ciberseguridad defensiva", "Auditoría autorizada, hardening, revisión de secretos, permisos y configuración."],
  ["Datos", "SQL, reportes, análisis de datasets, métricas y preparación para dashboards."],
];

const productionChecks = [
  ["Web", "ghostnexoraai.duckdns.org"],
  ["API", "apighostnexoraai.duckdns.org"],
  ["Modelo local", "Ollama / vLLM configurable"],
  ["Android", "Debug siempre, release con secretos"],
  ["CI", "Web, API, Docker, dataset y APK"],
];

export default function Page() {
  return (
    <main className="shell">
      <nav className="nav">
        <Link className="brand" href="/">
          <span className="brand-mark">NX</span>
          <span>Nexora AI</span>
        </Link>
        <div className="nav-links">
          <Link href="/chat">Chat</Link>
          <Link href="/docs">Docs</Link>
          <Link href="/api/mobile/status">API status</Link>
          <Link href="/openapi.json">OpenAPI</Link>
        </div>
      </nav>

      <section className="hero">
        <article className="hero-card">
          <p className="eyebrow">VPS Owner Edition</p>
          <h1>Tu IA local para construir software real.</h1>
          <p>
            Nexora AI queda preparada como plataforma propia: panel web, API,
            cliente Android, CI/CD, Docker para VPS, documentación navegable y
            base para conectar modelos locales entrenables con RAG y agentes.
          </p>
          <div className="actions">
            <Link className="btn" href="/chat">Abrir cliente web</Link>
            <Link className="btn secondary" href="/docs">Leer documentación</Link>
          </div>
        </article>

        <aside className="card">
          <p className="eyebrow">Estado de producción</p>
          <div className="status-list">
            {productionChecks.map(([title, value]) => (
              <div className="status-item" key={title}>
                <span>{title}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          <p className="muted">
            Los dominios DuckDNS ya apuntan a la VPS. Nginx separa el panel
            público de la API y Certbot habilita HTTPS para ambos hosts.
          </p>
        </aside>
      </section>

      <section className="grid">
        {capabilities.map(([title, body]) => (
          <article className="card" key={title}>
            <span className="dot" />
            <h2>{title}</h2>
            <p>{body}</p>
          </article>
        ))}
      </section>

      <footer className="footer">
        Nexora AI · Local-first · Defensive security · Android client · VPS ready
      </footer>
    </main>
  );
}

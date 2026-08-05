import Link from "next/link";
import Script from "next/script";

const capabilities = [
  {
    icon: "01",
    title: "Programación completa",
    body: "Arquitectura, frontend, backend, Android, APIs, bases de datos, pruebas y documentación.",
  },
  {
    icon: "02",
    title: "Agentes especializados",
    body: "Elige respuesta instantánea o colaboración entre planificación, especialidad, revisión y síntesis.",
  },
  {
    icon: "03",
    title: "Actividad visible",
    body: "Consulta las etapas de trabajo, agentes utilizados, proveedor y tiempo real sin exponer razonamiento privado.",
  },
  {
    icon: "04",
    title: "Archivos e imágenes",
    body: "Adjunta código, documentos, PDF, Word e imágenes para obtener respuestas con contexto útil.",
  },
  {
    icon: "05",
    title: "Laboratorio efímero",
    body: "Valida código compatible dentro de contenedores aislados, limitados, sin red y eliminados al terminar.",
  },
  {
    icon: "06",
    title: "Tu infraestructura",
    body: "La API, los modelos y los datos operativos permanecen bajo el control de tu propia VPS.",
  },
];

const requirements = [
  {
    label: "Base",
    title: "API sin modelo local",
    specs: ["2 vCPU", "4 GB RAM", "20 GB SSD"],
  },
  {
    label: "Recomendado",
    title: "Modelo local 7B / 8B",
    specs: ["8 vCPU", "16 GB RAM", "80 GB SSD"],
    featured: true,
  },
  {
    label: "Acelerado",
    title: "Inferencia con GPU",
    specs: ["8 vCPU", "16–32 GB RAM", "12+ GB VRAM"],
  },
];

export default function Page() {
  const softwareJsonLd = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "Nexora AI",
    applicationCategory: "DeveloperApplication",
    operatingSystem: "Android 8.0 or later",
    softwareVersion: "0.5.1",
    description:
      "Cliente Android para una inteligencia artificial privada alojada en una VPS propia.",
    offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
    author: { "@type": "Person", name: "Ghost Developer" },
  };

  return (
    <main>
      <Script
        id="nexora-software-jsonld"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(softwareJsonLd) }}
      />

      <nav className="site-nav page-width" aria-label="Navegación principal">
        <Link className="brand" href="/" aria-label="Nexora AI, inicio">
          <span className="brand-mark" aria-hidden="true">N</span>
          <span>Nexora AI</span>
        </Link>
        <div className="nav-links">
          <Link href="#capacidades">Capacidades</Link>
          <Link href="#requisitos">Requisitos</Link>
          <Link href="/terms">Términos</Link>
          <Link className="nav-download" href="/download">Descargar</Link>
        </div>
      </nav>

      <section className="hero page-width">
        <div className="hero-copy">
          <div className="availability"><span /> Disponible para Android</div>
          <h1>Tu inteligencia.<br /><em>Tu servidor.</em></h1>
          <p className="hero-lead">
            Nexora AI une modelos locales, agentes especializados y una app móvil
            cuidada para ayudarte a construir software sin entregar el control de
            tu infraestructura.
          </p>
          <div className="hero-actions">
            <Link className="primary-button" href="/download">
              Descargar APK <span aria-hidden="true">↓</span>
            </Link>
            <Link className="text-button" href="#capacidades">Conocer Nexora AI</Link>
          </div>
          <div className="hero-facts" aria-label="Compatibilidad principal">
            <span>Android 8+</span>
            <span>API propia</span>
            <span>Modelos locales</span>
            <span>Sin anuncios</span>
          </div>
        </div>

        <div className="phone-stage" aria-label="Vista previa de la aplicación Nexora AI">
          <div className="orb orb-one" />
          <div className="orb orb-two" />
          <div className="phone">
            <div className="phone-top"><span>9:41</span><i /><b>●</b></div>
            <div className="app-bar">
              <span className="menu-lines" aria-hidden="true" />
              <div><strong>Nexora AI</strong><small>Proyecto Android</small></div>
              <span className="pin-mini">◆</span>
            </div>
            <div className="phone-content">
              <div className="user-bubble">Mejora la arquitectura y valida que compile.</div>
              <div className="thinking-card">
                <header><span className="pulse" /><div><strong>Nexora AI está pensando</strong><small>Revisor técnico trabajando</small></div><time>12.8 s</time></header>
                <div className="progress"><span /></div>
                <ul>
                  <li><i>✓</i> Solicitud validada</li>
                  <li><i>✓</i> Planificación completada</li>
                  <li><i>✓</i> Especialista completado</li>
                </ul>
              </div>
            </div>
            <div className="phone-composer"><span>＋</span><p>Mensaje para Nexora AI</p><b>↑</b></div>
          </div>
        </div>
      </section>

      <section className="trust-strip" aria-label="Tecnologías compatibles">
        <div className="page-width trust-items">
          <span>ANDROID</span><span>KOTLIN</span><span>OLLAMA</span><span>DOCKER</span><span>LINUX</span><span>NEXT.JS</span>
        </div>
      </section>

      <section className="section page-width" id="capacidades">
        <div className="section-heading">
          <p className="kicker">Un espacio de trabajo completo</p>
          <h2>Más claridad mientras la IA trabaja.</h2>
          <p>No es solo una caja de chat. Nexora organiza conversaciones, proyectos, archivos, especialistas y pruebas desde una experiencia móvil adaptable.</p>
        </div>
        <div className="capability-grid">
          {capabilities.map((capability) => (
            <article className="capability-card" key={capability.title}>
              <span className="feature-number">{capability.icon}</span>
              <h3>{capability.title}</h3>
              <p>{capability.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="privacy-section">
        <div className="page-width privacy-grid">
          <div>
            <p className="kicker">Local-first</p>
            <h2>Privacidad diseñada desde la infraestructura.</h2>
          </div>
          <div className="privacy-points">
            <article><span>01</span><div><h3>Inferencia bajo tu control</h3><p>Conecta Ollama u otro proveedor local desde la VPS que administras.</p></div></article>
            <article><span>02</span><div><h3>Historial en el dispositivo</h3><p>Los chats, proyectos y fijados se guardan localmente en Android.</p></div></article>
            <article><span>03</span><div><h3>Seguridad real en el servidor</h3><p>HTTPS, límites, políticas defensivas y ejecución de código desactivada por defecto.</p></div></article>
          </div>
        </div>
      </section>

      <section className="section page-width" id="requisitos">
        <div className="section-heading compact">
          <p className="kicker">Escala según tu modelo</p>
          <h2>Una VPS para cada etapa.</h2>
        </div>
        <div className="requirements-grid">
          {requirements.map((requirement) => (
            <article className={`requirement-card ${requirement.featured ? "featured" : ""}`} key={requirement.label}>
              <span>{requirement.label}</span>
              <h3>{requirement.title}</h3>
              <ul>{requirement.specs.map((spec) => <li key={spec}>{spec}</li>)}</ul>
            </article>
          ))}
        </div>
        <p className="compatibility-note">
          Servidor validable en Ubuntu 22.04/24.04/26.04 y Debian 11/12/13 sobre AMD64 o ARM64. La compilación Android release en VPS requiere Linux AMD64; GitHub Actions permanece como alternativa reproducible.
        </p>
      </section>

      <section className="download-section page-width">
        <div>
          <p className="kicker">Nexora AI 0.5.1</p>
          <h2>Lleva tu IA contigo.</h2>
          <p>Descarga el cliente Android y conéctalo a la API de tu propia VPS.</p>
        </div>
        <Link className="primary-button light" href="/download">Descargar para Android <span>↓</span></Link>
      </section>

      <footer className="footer page-width">
        <Link className="brand" href="/"><span className="brand-mark">N</span><span>Nexora AI</span></Link>
        <p>IA privada para construir, revisar y aprender.</p>
        <div><Link href="/terms">Términos</Link><Link href="/privacy">Privacidad</Link><Link href="/docs">Documentación</Link></div>
        <small>© 2026 Ghost Developer</small>
      </footer>
    </main>
  );
}

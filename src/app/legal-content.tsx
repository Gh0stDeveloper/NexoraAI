import Link from "next/link";

export type LegalSection = { title: string; body: string };

export function LegalPage({
  eyebrow,
  title,
  description,
  sections,
}: {
  eyebrow: string;
  title: string;
  description: string;
  sections: LegalSection[];
}) {
  return (
    <main className="legal-shell">
      <nav className="site-nav page-width">
        <Link className="brand" href="/"><span className="brand-mark">N</span><span>Nexora AI</span></Link>
        <Link className="nav-download" href="/download">Descargar Android</Link>
      </nav>
      <header className="legal-header page-width">
        <p className="kicker">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </header>
      <section className="legal-content page-width">
        {sections.map((section) => (
          <article key={section.title}>
            <h2>{section.title}</h2>
            <p>{section.body}</p>
          </article>
        ))}
      </section>
    </main>
  );
}

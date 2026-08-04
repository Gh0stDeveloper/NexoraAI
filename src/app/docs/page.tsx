import Link from "next/link";
const docs=["inicio","vps","dominios","android","ci","seguridad"];
export default function Docs(){return <main className="shell"><h1>Documentación Nexora AI</h1>{docs.map(d=><article className="card" key={d}><h2>{d}</h2><p className="muted">Documento operativo disponible en <code>docs/{d}.md</code>.</p></article>)}<Link href="/api/docs/list">API docs list</Link></main>}

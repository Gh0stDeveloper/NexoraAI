"use client";

import { FormEvent, useMemo, useState } from "react";
import Link from "next/link";

type Mode = "auto" | "fullstack" | "android" | "backend" | "security" | "data" | "devops";
type ChatMessage = { role: "user" | "assistant"; content: string };

const modes: { id: Mode; label: string; hint: string }[] = [
  { id: "auto", label: "Automático", hint: "Router decide" },
  { id: "fullstack", label: "Full stack", hint: "Web/API" },
  { id: "android", label: "Android", hint: "Kotlin/Compose" },
  { id: "backend", label: "Backend", hint: "APIs/DB" },
  { id: "security", label: "Seguridad", hint: "Defensiva" },
  { id: "data", label: "Datos", hint: "SQL/reportes" },
  { id: "devops", label: "DevOps", hint: "VPS/CI" },
];

export default function ChatPage() {
  const [mode, setMode] = useState<Mode>("auto");
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content:
        "Soy Nexora AI. Puedo ayudarte con programación, Android, backend, datos, DevOps y ciberseguridad defensiva. El cliente web ya consume la API propia del servidor.",
    },
  ]);

  const activeMode = useMemo(() => modes.find((item) => item.id === mode), [mode]);

  async function sendMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = input.trim();
    if (!message || loading) return;

    setInput("");
    setLoading(true);
    setMessages((current) => [...current, { role: "user", content: message }]);

    try {
      const response = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message, mode }),
      });
      const data = await response.json();
      const answer = data?.answer ?? data?.error ?? "La API no devolvió una respuesta utilizable.";
      setMessages((current) => [...current, { role: "assistant", content: answer }]);
    } catch (error) {
      setMessages((current) => [
        ...current,
        { role: "assistant", content: `Error conectando con la API: ${error instanceof Error ? error.message : "desconocido"}` },
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <section className="chat-layout">
        <aside className="sidebar">
          <Link className="brand" href="/">
            <span className="brand-mark">NX</span>
            <span>Nexora AI</span>
          </Link>
          <p className="muted">Agente activo: {activeMode?.label}</p>
          {modes.map((item) => (
            <button
              key={item.id}
              className={`mode-chip ${item.id === mode ? "active" : ""}`}
              type="button"
              onClick={() => setMode(item.id)}
            >
              <strong>{item.label}</strong>
              <br />
              <small>{item.hint}</small>
            </button>
          ))}
          <Link href="/docs">Documentación</Link>
          <Link href="/api/mobile/status">Estado API</Link>
        </aside>

        <section className="chat-panel">
          <header className="chat-header">
            <div>
              <p className="eyebrow">Cliente web</p>
              <h2>Chat Nexora AI</h2>
            </div>
            <span className="pill">API propia · {mode}</span>
          </header>

          <div className="messages">
            {messages.map((message, index) => (
              <article className={`message ${message.role}`} key={`${message.role}-${index}`}>
                {message.content}
              </article>
            ))}
            {loading ? <article className="message assistant">Pensando con el agente {mode}...</article> : null}
          </div>

          <form className="composer" onSubmit={sendMessage}>
            <textarea
              rows={2}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Escribe una tarea: revisar código, planificar API, auditar seguridad defensiva, explicar logs..."
            />
            <button className="btn" type="submit" disabled={loading}>
              Enviar
            </button>
          </form>
        </section>
      </section>
    </main>
  );
}

import { ImageResponse } from "next/og";

export const alt = "Nexora AI — Inteligencia artificial privada";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "68px 76px",
        color: "#f4f8f6",
        background: "radial-gradient(circle at 80% 0%, #173e31 0%, #07100d 45%, #050806 100%)",
        fontFamily: "sans-serif",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 20, fontSize: 30, fontWeight: 800 }}>
        <div style={{ display: "flex", width: 64, height: 64, alignItems: "center", justifyContent: "center", borderRadius: 18, background: "#38e8b0", color: "#03130d" }}>N</div>
        Nexora AI
      </div>
      <div style={{ display: "flex", flexDirection: "column" }}>
        <div style={{ display: "flex", flexDirection: "column", fontSize: 82, lineHeight: 0.98, letterSpacing: "-4px", fontWeight: 850 }}>
          <span>Tu inteligencia.</span>
          <span style={{ color: "#38e8b0" }}>Tu servidor.</span>
        </div>
        <div style={{ marginTop: 30, fontSize: 27, color: "#a6b7af" }}>Modelos locales · Android · Agentes especializados · VPS propia</div>
      </div>
    </div>,
    size,
  );
}

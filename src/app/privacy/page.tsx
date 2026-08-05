import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "../legal-content";

export const metadata: Metadata = {
  title: "Aviso de privacidad",
  description: "Información sobre el tratamiento de datos en Nexora AI.",
  alternates: { canonical: "/privacy" },
};

const sections: LegalSection[] = [
  { title: "1. Responsable", body: "Ghost Developer opera Nexora AI. Para consultas sobre privacidad puedes escribir a ghostnexora@gmail.com. Vigente desde el 5 de agosto de 2026." },
  { title: "2. Información procesada", body: "La aplicación envía a la API los mensajes, archivos que elijas adjuntar, identificadores locales de proyecto y conversación, nivel de inteligencia, especialidad y datos técnicos mínimos de versión necesarios para prestar el servicio." },
  { title: "3. Historial local", body: "Los chats, proyectos, elementos fijados y metadatos de actividad se guardan en el dispositivo mediante almacenamiento privado de la aplicación. Borrar los datos de Android o desinstalarla puede eliminar ese historial." },
  { title: "4. Finalidades", body: "La información se procesa para generar respuestas, analizar adjuntos, ejecutar funciones solicitadas, proteger el servicio, diagnosticar fallos, medir tiempos operativos y mantener la calidad técnica." },
  { title: "5. VPS y modelos", body: "Por defecto, la inferencia se realiza con modelos configurados en la VPS del operador. La política de logs y respaldos depende de esa instalación. No se deben habilitar proveedores externos sin reflejarlo en este aviso." },
  { title: "6. Conservación", body: "Nexora AI no crea por defecto una base de datos remota del historial móvil. Los logs técnicos del servidor deben conservarse únicamente durante el tiempo necesario para seguridad y diagnóstico, según la configuración del operador." },
  { title: "7. Seguridad", body: "La variante release usa HTTPS y rechaza todo tráfico claro. Solo debug permite destinos locales de desarrollo. Ningún sistema es infalible: evita enviar contraseñas, claves privadas, tokens o datos que no deban procesarse." },
  { title: "8. Derechos", body: "Puedes eliminar chats desde la aplicación y borrar todos sus datos desde los ajustes de Android. Para solicitar información o eliminación de datos conservados en el servidor, contacta al responsable." },
  { title: "9. Cambios", body: "Si cambian los datos tratados, proveedores o finalidades, este aviso y la copia incluida en la aplicación deberán actualizarse antes del cambio." },
];

export default function PrivacyPage() {
  return <LegalPage eyebrow="Privacidad" title="Tus datos, explicados con claridad." description="Qué procesa Nexora AI, dónde se guarda y qué controles tienes." sections={sections} />;
}

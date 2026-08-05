import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "../legal-content";

export const metadata: Metadata = {
  title: "Términos y condiciones",
  description: "Condiciones de uso de Nexora AI y su aplicación Android.",
  alternates: { canonical: "/terms" },
};

const sections: LegalSection[] = [
  { title: "1. Aceptación", body: "Al acceder a Nexora AI, instalar la aplicación o usar la API aceptas estos términos. Si no estás de acuerdo, no utilices el servicio. Vigentes desde el 5 de agosto de 2026." },
  { title: "2. Descripción del servicio", body: "Nexora AI ofrece asistencia automatizada para programación, Android, backend, datos, DevOps y ciberseguridad defensiva mediante modelos alojados en infraestructura administrada por el operador." },
  { title: "3. Respuestas generadas", body: "Las respuestas pueden ser incompletas o incorrectas. Debes revisar código, comandos, configuraciones, diagnósticos y decisiones antes de aplicarlos, especialmente en producción, seguridad, asuntos legales, financieros o médicos." },
  { title: "4. Uso permitido", body: "Solo puedes usar Nexora AI sobre sistemas propios o para los que tengas autorización. Se prohíben malware, phishing, robo de credenciales, evasión de controles, exfiltración, abuso de terceros y cualquier actividad contraria a la ley." },
  { title: "5. Laboratorio de código", body: "Cuando está habilitado, el laboratorio ejecuta bloques compatibles dentro de contenedores efímeros con límites de CPU, memoria, procesos y red. Un resultado exitoso no garantiza que el proyecto completo sea seguro o funcione en todos los entornos." },
  { title: "6. Disponibilidad", body: "El servicio depende de la VPS, la red, los modelos y la configuración. Puede interrumpirse por mantenimiento, seguridad, capacidad o causas externas, sin garantía de disponibilidad continua." },
  { title: "7. Responsabilidad", body: "El usuario es responsable de copias de seguridad, permisos, secretos, pruebas y despliegues. Nexora AI se proporciona sin garantías expresas y, en la medida permitida por la ley, el operador no responde por daños derivados de resultados no verificados." },
  { title: "8. Cambios", body: "Estos términos pueden actualizarse cuando cambie el servicio. La versión vigente se publicará aquí y dentro de la aplicación." },
  { title: "9. Contacto", body: "Para consultas relacionadas con estos términos escribe a ghostnexora@gmail.com." },
];

export default function TermsPage() {
  return <LegalPage eyebrow="Legal" title="Términos y condiciones" description="Reglas claras para usar Nexora AI de manera responsable y segura." sections={sections} />;
}

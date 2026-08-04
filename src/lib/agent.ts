export type AgentMode="auto"|"fullstack"|"android"|"backend"|"security"|"data"|"devops";
export async function runAgent(message:string,mode:AgentMode="auto"){
 const blocked=/(phishing|robar credenciales|malware|ransomware|exfiltrar|bypass)/i.test(message);
 if(blocked && mode==="security") return {agent:"security",answer:"No puedo ayudar con abuso ofensivo. Sí puedo auditar, endurecer o corregir sistemas propios/autorizados."};
 return {agent:mode,answer:`Nexora AI recibió tu solicitud en modo ${mode}. En producción este endpoint se conecta al proveedor local Ollama/vLLM, RAG y herramientas autorizadas.\n\nSolicitud: ${message}`};
}

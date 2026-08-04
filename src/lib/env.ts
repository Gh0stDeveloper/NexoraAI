export const env = {
  appName: process.env.APP_NAME ?? "Nexora AI",
  appVersion: process.env.APP_VERSION ?? "dev",
  appEnv: process.env.APP_ENV ?? "local",
  aiProvider: process.env.AI_PROVIDER ?? "ollama",
  model: process.env.OLLAMA_MODEL ?? "qwen2.5-coder:7b",
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:3000",
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  mobileApi: process.env.MOBILE_PRODUCTION_API_URL ?? "https://api.nexoraia.com/",
  dangerousTools: process.env.ENABLE_DANGEROUS_TOOLS === "true",
  codeExecution: process.env.ALLOW_CODE_EXECUTION === "true",
  githubWrite: process.env.ALLOW_GITHUB_WRITE === "true"
};

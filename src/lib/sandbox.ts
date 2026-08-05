export type CodeValidationResult = {
  status: "disabled" | "skipped" | "passed" | "failed" | "unavailable";
  language?: "python" | "javascript" | "bash";
  exitCode?: number;
  durationMs?: number;
  output?: string;
  reason?: string;
};

type SupportedLanguage = NonNullable<CodeValidationResult["language"]>;

const languageAliases: Record<string, SupportedLanguage> = {
  py: "python",
  python: "python",
  js: "javascript",
  javascript: "javascript",
  node: "javascript",
  bash: "bash",
  sh: "bash",
  shell: "bash",
};

function firstRunnableBlock(answer: string): {
  language: SupportedLanguage;
  code: string;
} | null {
  const blocks = answer.matchAll(/```([a-zA-Z0-9_+-]+)\s*\n([\s\S]*?)```/g);
  for (const match of blocks) {
    const language = languageAliases[match[1].toLowerCase()];
    const code = match[2].trim();
    if (language && code && code.length <= 120_000) return { language, code };
  }
  return null;
}

function cleanOutput(value: unknown): string | undefined {
  if (typeof value !== "string" || !value.trim()) return undefined;
  return value
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "")
    .slice(0, 12_000);
}

export async function validateGeneratedCode(
  answer: string,
): Promise<CodeValidationResult> {
  if ((process.env.ALLOW_CODE_EXECUTION || "false").toLowerCase() !== "true") {
    return {
      status: "disabled",
      reason: "El laboratorio está desactivado en la configuración del servidor.",
    };
  }

  const block = firstRunnableBlock(answer);
  if (!block) {
    return {
      status: "skipped",
      reason: "La respuesta no incluyó un bloque Python, JavaScript o Bash verificable.",
    };
  }

  const runnerUrl = process.env.SANDBOX_RUNNER_URL || "http://sandbox:8787";
  const token = process.env.SANDBOX_RUNNER_TOKEN;
  if (!token || token.length < 24) {
    return {
      status: "unavailable",
      language: block.language,
      reason: "El token interno del laboratorio no está configurado.",
    };
  }

  try {
    const response = await fetch(`${runnerUrl.replace(/\/$/, "")}/v1/run`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      signal: AbortSignal.timeout(45_000),
      body: JSON.stringify(block),
    });
    const payload = (await response.json()) as {
      ok?: boolean;
      exitCode?: number;
      durationMs?: number;
      output?: string;
      error?: string;
    };

    if (!response.ok) {
      return {
        status: "unavailable",
        language: block.language,
        reason: cleanOutput(payload.error) || `El laboratorio respondió HTTP ${response.status}.`,
      };
    }

    return {
      status: payload.ok ? "passed" : "failed",
      language: block.language,
      exitCode: payload.exitCode,
      durationMs: payload.durationMs,
      output: cleanOutput(payload.output),
    };
  } catch (error) {
    return {
      status: "unavailable",
      language: block.language,
      reason:
        error instanceof Error
          ? `El laboratorio no estuvo disponible: ${error.message}`
          : "El laboratorio no estuvo disponible.",
    };
  }
}

import { createServer } from "node:http";
import { chmod, mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { execFile } from "node:child_process";
import { randomUUID, timingSafeEqual } from "node:crypto";

const port = boundedInteger(process.env.PORT, 8787, 1, 65535);
const timeoutMs = boundedInteger(process.env.SANDBOX_TIMEOUT_MS, 30_000, 1_000, 60_000);
const maxOutputBytes = boundedInteger(
  process.env.SANDBOX_MAX_OUTPUT_BYTES,
  12_000,
  1_000,
  50_000,
);
const token = process.env.SANDBOX_RUNNER_TOKEN || "";
const jobRoot = process.env.SANDBOX_JOBS_PATH || "/var/lib/nexora-ai/sandbox-jobs";
const maxConcurrentJobs = boundedInteger(
  process.env.SANDBOX_MAX_CONCURRENT_JOBS,
  2,
  1,
  8,
);
let activeJobs = 0;

const runtimes = {
  python: {
    image: process.env.SANDBOX_PYTHON_IMAGE || "python:3.12-alpine",
    filename: "main.py",
    command: "python -B /workspace/main.py",
  },
  javascript: {
    image: process.env.SANDBOX_NODE_IMAGE || "node:22-alpine",
    filename: "main.js",
    command: "node --check /workspace/main.js && node /workspace/main.js",
  },
  bash: {
    image: process.env.SANDBOX_BASH_IMAGE || "bash:5.2",
    filename: "main.sh",
    command: "bash -n /workspace/main.sh && bash /workspace/main.sh",
  },
};

function boundedInteger(raw, fallback, minimum, maximum) {
  const parsed = Number.parseInt(raw || "", 10);
  return Number.isFinite(parsed)
    ? Math.min(maximum, Math.max(minimum, parsed))
    : fallback;
}

function reply(response, status, payload) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(JSON.stringify(payload));
}

function authorized(header) {
  if (token.length < 24 || typeof header !== "string" || !header.startsWith("Bearer ")) {
    return false;
  }
  const candidate = header.slice(7);
  const expected = Buffer.from(token);
  const actual = Buffer.from(candidate);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

async function readJson(request) {
  let body = "";
  for await (const chunk of request) {
    body += chunk;
    if (Buffer.byteLength(body) > 180_000) throw new Error("request_too_large");
  }
  return JSON.parse(body || "{}");
}

function runDocker(args, jobName) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    execFile(
      "docker",
      args,
      { timeout: timeoutMs, maxBuffer: maxOutputBytes * 4 },
      (error, stdout, stderr) => {
        const timedOut = Boolean(error?.killed);
        const finish = () =>
          resolve({
            ok: !error,
            exitCode: typeof error?.code === "number" ? error.code : error ? 1 : 0,
            durationMs: Date.now() - startedAt,
            output: `${stdout || ""}${stderr || ""}`.slice(0, maxOutputBytes),
            timedOut,
          });

        if (error) {
          execFile("docker", ["rm", "-f", jobName], () => finish());
        } else {
          finish();
        }
      },
    );
  });
}

async function execute(language, code) {
  const runtime = runtimes[language];
  if (!runtime) throw new Error("unsupported_language");
  if (typeof code !== "string" || !code.trim() || code.length > 120_000) {
    throw new Error("invalid_code");
  }

  const jobDirectory = await mkdtemp(join(jobRoot, "nexora-job-"));
  const sourcePath = join(jobDirectory, runtime.filename);
  const jobName = `nexora-job-${randomUUID()}`;
  await chmod(jobDirectory, 0o755);
  await writeFile(sourcePath, code, { encoding: "utf8", mode: 0o444 });

  try {
    return await runDocker(
      [
        "run",
        "--rm",
        "--name",
        jobName,
        "--network",
        "none",
        "--read-only",
        "--cap-drop",
        "ALL",
        "--security-opt",
        "no-new-privileges",
        "--pids-limit",
        "64",
        "--memory",
        "512m",
        "--memory-swap",
        "512m",
        "--cpus",
        "1",
        "--user",
        "65534:65534",
        "--ulimit",
        "nofile=64:64",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777",
        "--volume",
        `${jobDirectory}:/workspace:ro`,
        "--workdir",
        "/workspace",
        runtime.image,
        "/bin/sh",
        "-lc",
        runtime.command,
      ],
      jobName,
    );
  } finally {
    await rm(jobDirectory, { recursive: true, force: true });
  }
}

await mkdir(jobRoot, { recursive: true, mode: 0o700 });

createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    return reply(response, 200, {
      ok: true,
      service: "nexora-sandbox-runner",
      configured: token.length >= 24,
      activeJobs,
      maxConcurrentJobs,
    });
  }

  if (request.method !== "POST" || request.url !== "/v1/run") {
    return reply(response, 404, { ok: false, error: "not_found" });
  }
  if (!authorized(request.headers.authorization)) {
    return reply(response, 401, { ok: false, error: "unauthorized" });
  }
  if (activeJobs >= maxConcurrentJobs) {
    return reply(response, 429, { ok: false, error: "runner_busy" });
  }

  activeJobs += 1;
  try {
    const body = await readJson(request);
    const result = await execute(body.language, body.code);
    return reply(response, 200, result);
  } catch (error) {
    const reason = error instanceof Error ? error.message : "invalid_request";
    return reply(response, 400, { ok: false, error: reason });
  } finally {
    activeJobs -= 1;
  }
}).listen(port, "0.0.0.0", () => {
  console.log(`[Nexora sandbox] listening on ${port}`);
});

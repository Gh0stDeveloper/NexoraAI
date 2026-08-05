import { createHash } from "node:crypto";
import { execFile } from "node:child_process";
import {
  chmod,
  copyFile,
  cp,
  mkdir,
  readFile,
  readdir,
  rm,
  writeFile,
} from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";
import pg from "pg";

const execFileAsync = promisify(execFile);
const { Pool } = pg;
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 2,
  connectionTimeoutMillis: 10_000,
});

const jobsRoot = path.resolve(
  process.env.USER_BUILD_JOBS_PATH || "/var/lib/nexora-ai/android-build-jobs",
);
const templateRoot = path.resolve(
  process.env.USER_BUILD_TEMPLATE_PATH || "/opt/nexora-user-template",
);
const gradle = process.env.NEXORA_GRADLE_BIN || "/opt/gradle/bin/gradle";
const apksigner =
  process.env.NEXORA_APKSIGNER ||
  "/opt/android-sdk/build-tools/35.0.0/apksigner";
const zipalign =
  process.env.NEXORA_ZIPALIGN ||
  "/opt/android-sdk/build-tools/35.0.0/zipalign";
const signingDirectory = "/run/secrets/nexora";
const signingEnvFile = path.join(signingDirectory, "android-user-signing.env");
const keystoreFile = path.join(signingDirectory, "android-user-builds.keystore");
const pollIntervalMs = 2_500;
const cleanupIntervalMs = 5_000;
let stopping = false;
let cleanupTask = null;

function assertJobPath(candidate) {
  const target = path.resolve(candidate);
  if (!target.startsWith(`${jobsRoot}${path.sep}`)) {
    throw new Error("Unsafe Android build job path");
  }
  return target;
}

function safeFileName(value) {
  const normalized = value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return normalized || "Nexora-App";
}

function xmlEscape(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

async function readSigningEnvironment() {
  const raw = await readFile(signingEnvFile, "utf8");
  const parsed = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const separator = trimmed.indexOf("=");
    if (separator <= 0) continue;
    parsed[trimmed.slice(0, separator)] = trimmed.slice(separator + 1);
  }
  for (const key of [
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
  ]) {
    if (!parsed[key]) throw new Error(`Missing user build signing value: ${key}`);
  }
  const gradleEnvironment = {
    ...process.env,
    ANDROID_HOME: process.env.ANDROID_HOME || "/opt/android-sdk",
    ANDROID_SDK_ROOT: process.env.ANDROID_SDK_ROOT || "/opt/android-sdk",
    GRADLE_USER_HOME: process.env.GRADLE_USER_HOME || "/var/cache/nexora-gradle",
    NEXORA_EXTERNAL_APK_SIGNING: "true",
  };
  for (const key of [
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
  ]) {
    delete gradleEnvironment[key];
  }
  return {
    gradleEnvironment,
    signingEnvironment: {
      ...process.env,
      ...parsed,
    },
    keyAlias: parsed.ANDROID_KEY_ALIAS,
  };
}

async function claimBuild() {
  const client = await pool.connect();
  try {
    await client.query("begin");
    const selected = await client.query(
      `select * from android_build_jobs
        where status = 'queued'
        order by created_at asc
        for update skip locked
        limit 1`,
    );
    const row = selected.rows[0];
    if (!row) {
      await client.query("commit");
      return null;
    }
    await client.query(
      `update android_build_jobs
          set status = 'building',
              progress_label = 'Preparando proyecto Android',
              attempts = attempts + 1,
              started_at = coalesce(started_at, now()),
              updated_at = now()
        where id = $1`,
      [row.id],
    );
    await client.query("commit");
    return row;
  } catch (error) {
    await client.query("rollback").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

async function updateProgress(id, label) {
  await pool.query(
    `update android_build_jobs
        set progress_label = $2, updated_at = now()
      where id = $1 and status = 'building'`,
    [id, label],
  );
}

async function findReleaseApk(directory) {
  const files = await readdir(directory, { withFileTypes: true });
  const match = files.find((file) => file.isFile() && file.name.endsWith(".apk"));
  if (!match) throw new Error("Gradle did not generate a release APK");
  return path.join(directory, match.name);
}

async function compileBuild(job) {
  const jobRoot = assertJobPath(path.join(jobsRoot, job.id));
  const workRoot = assertJobPath(path.join(jobRoot, "work"));
  const outputRoot = assertJobPath(path.join(jobRoot, "output"));
  await rm(jobRoot, { recursive: true, force: true });
  await mkdir(outputRoot, { recursive: true });
  await cp(templateRoot, workRoot, { recursive: true });

  const assets = path.join(workRoot, "app/src/main/assets");
  const values = path.join(workRoot, "app/src/main/res/values");
  await mkdir(assets, { recursive: true });
  await mkdir(values, { recursive: true });
  await writeFile(
    path.join(assets, "nexora_content.txt"),
    `${job.source_prompt}\n\n${job.source_content}`,
    { encoding: "utf8", mode: 0o600 },
  );
  await writeFile(
    path.join(values, "generated.xml"),
    [
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
      "<resources>",
      `    <string name=\"app_name\">${xmlEscape(job.app_name)}</string>`,
      `    <color name=\"generated_accent\">${job.accent_color}</color>`,
      "</resources>",
      "",
    ].join("\n"),
    "utf8",
  );

  await updateProgress(job.id, "Compilando recursos y código");
  const signing = await readSigningEnvironment();
  await execFileAsync(
    gradle,
    [
      "assembleRelease",
      "--no-daemon",
      "--stacktrace",
      `-PNEXORA_APPLICATION_ID=${job.package_name}`,
      "-PNEXORA_VERSION_CODE=1",
    ],
    {
      cwd: workRoot,
      env: signing.gradleEnvironment,
      timeout: 15 * 60 * 1000,
      maxBuffer: 4 * 1024 * 1024,
    },
  );

  await updateProgress(job.id, "Verificando firma V1 + V2 + V3");
  const builtApk = await findReleaseApk(
    path.join(workRoot, "app/build/outputs/apk/release"),
  );
  const alignedApk = assertJobPath(path.join(workRoot, "aligned.apk"));
  const signedApk = assertJobPath(path.join(workRoot, "signed.apk"));
  await execFileAsync(zipalign, ["-p", "-f", "4", builtApk, alignedApk], {
    timeout: 60_000,
    maxBuffer: 512 * 1024,
  });
  await execFileAsync(
    apksigner,
    [
      "sign",
      "--ks",
      keystoreFile,
      "--ks-key-alias",
      signing.keyAlias,
      "--ks-pass",
      "env:ANDROID_KEYSTORE_PASSWORD",
      "--key-pass",
      "env:ANDROID_KEY_PASSWORD",
      "--v1-signing-enabled",
      "true",
      "--v2-signing-enabled",
      "true",
      "--v3-signing-enabled",
      "true",
      "--v4-signing-enabled",
      "false",
      "--out",
      signedApk,
      alignedApk,
    ],
    {
      env: signing.signingEnvironment,
      timeout: 60_000,
      maxBuffer: 512 * 1024,
    },
  );
  const verification = await execFileAsync(
    apksigner,
    ["verify", "--min-sdk-version", "23", "--verbose", signedApk],
    { timeout: 60_000, maxBuffer: 512 * 1024 },
  );
  const verified = verification.stdout;
  for (const scheme of ["v1", "v2", "v3"]) {
    if (!new RegExp(`Verified using ${scheme} scheme.*: true`, "i").test(verified)) {
      throw new Error(`Generated APK is missing ${scheme.toUpperCase()} signing`);
    }
  }

  const fileName = `${safeFileName(job.app_name)}-${job.id.slice(0, 8)}.apk`;
  const target = assertJobPath(path.join(outputRoot, fileName));
  await copyFile(signedApk, target);
  await chmod(target, 0o644);
  const digest = createHash("sha256").update(await readFile(target)).digest("hex");
  await rm(workRoot, { recursive: true, force: true });

  await pool.query(
    `update android_build_jobs
        set status = 'completed',
            progress_label = 'APK listo para descargar',
            output_path = $2,
            file_name = $3,
            sha256 = $4,
            signature_schemes = array['V1', 'V2', 'V3'],
            completed_at = now(),
            expires_at = now() + interval '1 hour',
            error = null,
            updated_at = now()
      where id = $1`,
    [job.id, target, fileName, digest],
  );
}

async function failBuild(job, error) {
  const detail = error instanceof Error ? error.message : String(error);
  console.error("[NexoraAI build worker] Build failed", {
    id: job.id,
    error: detail.slice(0, 500),
  });
  const jobRoot = assertJobPath(path.join(jobsRoot, job.id));
  await rm(jobRoot, { recursive: true, force: true }).catch(() => undefined);
  await pool.query(
    `update android_build_jobs
        set status = 'failed',
            progress_label = 'La compilación no pudo completarse',
            error = $2,
            completed_at = now(),
            expires_at = now() + interval '1 hour',
            updated_at = now()
      where id = $1`,
    [job.id, "No se pudo compilar el APK solicitado."],
  );
}

async function cleanupExpiredBuilds() {
  const expired = await pool.query(
    `select id from android_build_jobs
      where (expires_at is not null and expires_at <= now())
         or (status in ('queued', 'building') and created_at < now() - interval '2 hours')`,
  );
  for (const row of expired.rows) {
    const jobRoot = assertJobPath(path.join(jobsRoot, row.id));
    await rm(jobRoot, { recursive: true, force: true }).catch(() => undefined);
  }
  if (expired.rowCount) {
    await pool.query(
      `update android_build_jobs
          set status = 'expired',
              progress_label = 'Enlace expirado',
              output_path = null,
              source_prompt = '',
              source_content = '',
              updated_at = now()
        where id = any($1::uuid[])`,
      [expired.rows.map((row) => row.id)],
    );
  }
}

function scheduleCleanup() {
  if (cleanupTask) return cleanupTask;
  cleanupTask = cleanupExpiredBuilds()
    .catch((error) => {
      console.error("[NexoraAI build worker] Cleanup failed", {
        error: error instanceof Error ? error.message : String(error),
      });
    })
    .finally(() => {
      cleanupTask = null;
    });
  return cleanupTask;
}

async function run() {
  await mkdir(jobsRoot, { recursive: true });
  await scheduleCleanup();
  const cleanupTimer = setInterval(() => {
    void scheduleCleanup();
  }, cleanupIntervalMs);
  while (!stopping) {
    try {
      const job = await claimBuild();
      if (job) {
        await compileBuild(job).catch((error) => failBuild(job, error));
        continue;
      }
    } catch (error) {
      console.error("[NexoraAI build worker] Worker loop failed", {
        error: error instanceof Error ? error.message : String(error),
      });
    }
    await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
  }
  clearInterval(cleanupTimer);
  await cleanupTask;
  await pool.end();
}

for (const signal of ["SIGTERM", "SIGINT"]) {
  process.on(signal, () => {
    stopping = true;
  });
}

run().catch((error) => {
  console.error("[NexoraAI build worker] Fatal error", error);
  process.exitCode = 1;
});

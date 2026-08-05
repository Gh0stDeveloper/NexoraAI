#!/usr/bin/env node
import fs from "node:fs";
import { execFileSync } from "node:child_process";

const tracked = execFileSync(
  "git",
  ["ls-files", "--cached", "--others", "--exclude-standard", "-z"],
  { encoding: "utf8" },
)
  .split("\0")
  .filter(Boolean)
  .filter((file) => !file.endsWith(".svg"));

const secretPatterns = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /(?:^|[^A-Za-z0-9])sk-[A-Za-z0-9_-]{20,}/,
  /gh[pousr]_[A-Za-z0-9]{30,}/,
  /AIza[0-9A-Za-z_-]{30,}/,
];

const findings = [];
for (const file of tracked) {
  const stat = fs.statSync(file, { throwIfNoEntry: false });
  if (!stat?.isFile() || stat.size > 2_000_000) continue;
  const text = fs.readFileSync(file, "utf8");
  for (const pattern of secretPatterns) {
    if (pattern.test(text)) findings.push(`${file}: patrón sensible ${pattern}`);
  }
}

const releaseNetworkConfig = fs.readFileSync(
  "apps/android/GhostNexoraAndroid/app/src/main/res/xml/network_security_config.xml",
  "utf8",
);
const debugNetworkConfig = fs.readFileSync(
  "apps/android/GhostNexoraAndroid/app/src/debug/res/xml/network_security_config.xml",
  "utf8",
);
if (
  releaseNetworkConfig.includes('cleartextTrafficPermitted="true"') ||
  !releaseNetworkConfig.includes('cleartextTrafficPermitted="false"')
) {
  findings.push("Android release must reject all cleartext traffic");
}
if (!debugNetworkConfig.includes("10.0.2.2") || !debugNetworkConfig.includes("localhost")) {
  findings.push("Android network security config must limit cleartext to local development");
}
const debugDomains = [...debugNetworkConfig.matchAll(/<domain[^>]*>([^<]+)<\/domain>/g)]
  .map((match) => match[1]?.trim())
  .filter(Boolean);
if (debugDomains.some((domain) => domain !== "10.0.2.2" && domain !== "localhost")) {
  findings.push("Android cleartext appears enabled for a non-local domain");
}

const envTemplate = fs.readFileSync(".env.vps.example", "utf8");
if (!envTemplate.includes("ALLOW_CODE_EXECUTION=false")) {
  findings.push("Code execution must be disabled by default");
}

if (findings.length) {
  console.error("Security check failed:");
  findings.forEach((finding) => console.error(`- ${finding}`));
  process.exit(1);
}

console.log("Security check passed: no repository secrets and safe defaults verified.");

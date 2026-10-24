#!/usr/bin/env node
/**
 * Reports which Supabase Auth providers this project actually has enabled.
 *
 * WHY: the "Continue with Google" button is hidden unless the live project
 * reports `google: true` (see src/lib/auth-providers.ts). That is correct
 * behaviour — the project does not ship controls that only error — but it makes
 * "did my dashboard change take effect?" invisible. This prints the truth.
 *
 * Usage:  node scripts/check-auth-providers.mjs
 * Exit:   0 when Google is enabled, 1 when it is not, 2 on a read failure.
 *
 * Reads NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY from
 * .env.local. The publishable key is public by design; nothing secret is used.
 */

import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

function loadEnv(file) {
  const path = join(root, file);
  if (!existsSync(path)) return {};
  const out = {};
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(line);
    if (!match) continue;
    out[match[1]] = match[2].replace(/^["']|["']$/g, "").trim();
  }
  return out;
}

const env = { ...loadEnv(".env"), ...loadEnv(".env.local") };
const url = env.NEXT_PUBLIC_SUPABASE_URL;
const key = env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
const siteUrl = (env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000").replace(/\/$/, "");

if (!url || !key) {
  console.error("Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY.");
  console.error("Checked .env and .env.local in: " + root);
  process.exit(2);
}

let settings;
try {
  const response = await fetch(`${url}/auth/v1/settings`, {
    headers: { apikey: key },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) {
    console.error(`GET /auth/v1/settings failed: HTTP ${response.status}`);
    process.exit(2);
  }
  settings = await response.json();
} catch (error) {
  console.error("Could not reach Supabase:", error.message);
  process.exit(2);
}

const external = settings.external ?? {};
const enabled = Object.entries(external)
  .filter(([, on]) => on === true)
  .map(([name]) => name);

console.log(`Project:  ${url}`);
console.log(`Enabled:  ${enabled.join(", ") || "(none)"}`);
console.log(`Auto-confirm signup: ${settings.mailer_autoconfirm === true ? "yes" : "no"}`);
console.log("");

const projectRef = new URL(url).hostname.split(".")[0];

if (external.google === true) {
  console.log("Google sign-in: ENABLED");
  console.log(`  App callback:   ${siteUrl}/auth/callback`);
  console.log(`  Google redirect: https://${projectRef}.supabase.co/auth/v1/callback`);
  console.log("The button now renders on /login and /signup with no code change.");
  process.exit(0);
}

console.log("Google sign-in: DISABLED — the button is intentionally hidden.");
console.log("");
console.log("To enable it, in this order:");
console.log("  1. Google Cloud Console -> APIs & Services -> OAuth consent screen (External)");
console.log("  2. Credentials -> Create credentials -> OAuth client ID -> Web application");
console.log(`     Authorised redirect URI:  https://${projectRef}.supabase.co/auth/v1/callback`);
console.log("  3. Supabase dashboard -> Authentication -> Providers -> Google");
console.log("     Paste the Client ID and Client Secret, then Enable and Save.");
console.log(`  4. Supabase -> Authentication -> URL Configuration -> Redirect URLs must include`);
console.log(`     ${siteUrl}/**`);
console.log("  5. Re-run this script, then reload /login.");
process.exit(1);

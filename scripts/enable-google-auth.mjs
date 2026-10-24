#!/usr/bin/env node
/**
 * Enables Google as a sign-in provider on the live Supabase project.
 *
 * WHY THIS EXISTS: `src/lib/auth-providers.ts` hides the Google button unless
 * the project reports `google: true`. Clicking the toggle in the dashboard works
 * too, but it is four screens deep and easy to leave half-done (enabled with no
 * secret, or enabled with the wrong redirect URI). This does it in one call and
 * then *verifies* the result by re-reading the public settings endpoint.
 *
 * Usage:
 *   SUPABASE_ACCESS_TOKEN=sbp_... \
 *   GOOGLE_CLIENT_ID=...apps.googleusercontent.com \
 *   GOOGLE_CLIENT_SECRET=GOCSPX-... \
 *   node scripts/enable-google-auth.mjs
 *
 * Optional:
 *   --dry-run     print the request body and exit without sending it
 *   --site-url    override NEXT_PUBLIC_SITE_URL for the redirect allow-list
 *
 * Exit: 0 enabled and verified, 1 verification failed, 2 bad input or API error.
 *
 * SECURITY: the access token and client secret are read from the environment and
 * never written to disk or echoed. Only their presence is reported.
 */

import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const siteUrlFlag = args.indexOf("--site-url");
const siteUrlOverride = siteUrlFlag !== -1 ? args[siteUrlFlag + 1] : undefined;

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
const supabaseUrl = env.NEXT_PUBLIC_SUPABASE_URL;
const publishableKey = env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

const token = process.env.SUPABASE_ACCESS_TOKEN;
const clientId = process.env.GOOGLE_CLIENT_ID;
const clientSecret = process.env.GOOGLE_CLIENT_SECRET;

if (!supabaseUrl) {
  console.error("Missing NEXT_PUBLIC_SUPABASE_URL in .env or .env.local.");
  process.exit(2);
}

const projectRef = new URL(supabaseUrl).hostname.split(".")[0];

// `site_url` is where Supabase sends users after the OAuth round trip, so it must
// be the *public* domain. .env.local legitimately points at localhost for
// development, and copying that into the live project would bounce every Google
// sign-in back to a server only this machine can reach — so localhost is refused
// here and the production alias is used instead. Localhost stays in the
// allow-list below, which is what actually enables local development.
const PRODUCTION_URL = "https://vedant-grocery.vercel.app";
const rawSiteUrl = (siteUrlOverride || env.NEXT_PUBLIC_SITE_URL || PRODUCTION_URL).replace(/\/$/, "");
const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(rawSiteUrl);
const siteUrl = isLocal ? PRODUCTION_URL : rawSiteUrl;

const missing = [
  ["SUPABASE_ACCESS_TOKEN", token],
  ["GOOGLE_CLIENT_ID", clientId],
  ["GOOGLE_CLIENT_SECRET", clientSecret],
].filter(([, value]) => !value);

if (missing.length > 0) {
  console.error("Missing required environment variables:");
  for (const [name] of missing) console.error(`  - ${name}`);
  console.error("");
  console.error("Create a token at https://supabase.com/dashboard/account/tokens");
  process.exit(2);
}

if (!/\.apps\.googleusercontent\.com$/.test(clientId)) {
  console.error(`GOOGLE_CLIENT_ID does not look like an OAuth web client id: ${clientId}`);
  console.error("Expected it to end with .apps.googleusercontent.com");
  process.exit(2);
}

const api = `https://api.supabase.com/v1/projects/${projectRef}/config/auth`;
const authHeaders = {
  Authorization: `Bearer ${token}`,
  "Content-Type": "application/json",
};

/** Callbacks our app needs to be allowed to land on after the OAuth round trip. */
const requiredRedirects = [
  `${siteUrl}/auth/callback`,
  `${siteUrl}/**`,
  "http://localhost:3000/auth/callback",
  "http://localhost:3000/**",
];

async function readConfig() {
  const response = await fetch(api, {
    headers: authHeaders,
    signal: AbortSignal.timeout(20000),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${api} -> HTTP ${response.status}: ${body.slice(0, 300)}`);
  }
  return response.json();
}

console.log(`Project:  ${projectRef}`);
console.log(`Site URL: ${siteUrl}`);
if (isLocal) {
  console.log(`          (NEXT_PUBLIC_SITE_URL is ${rawSiteUrl} — using the public domain`);
  console.log("           for site_url instead, and allowing localhost only for dev)");
}
console.log(`Client:   ${clientId}`);
console.log(`Secret:   ${clientSecret.slice(0, 6)}… (${clientSecret.length} chars)`);
console.log("");

let current;
try {
  current = await readConfig();
} catch (error) {
  console.error("Could not read the current auth config.");
  console.error(error.message);
  console.error("");
  console.error("A 401 here means the access token is wrong or expired.");
  console.error("A 404 means the token's account cannot see this project.");
  process.exit(2);
}

// Merge, never replace: the allow-list may already carry preview-deployment URLs
// that we would otherwise silently break.
const existing = new Set(
  String(current.uri_allow_list ?? "")
    .split(/[,\n]/)
    .map((entry) => entry.trim())
    .filter(Boolean),
);
const before = existing.size;
for (const entry of requiredRedirects) existing.add(entry);
const allowList = [...existing];

console.log(`Redirect allow-list: ${before} existing + ${allowList.length - before} new`);
for (const entry of requiredRedirects) {
  console.log(`  ${String(current.uri_allow_list ?? "").includes(entry) ? "have" : "add "} ${entry}`);
}
console.log("");

const body = {
  external_google_enabled: true,
  external_google_client_id: clientId,
  external_google_secret: clientSecret,
  uri_allow_list: allowList.join(","),
  site_url: siteUrl,
};

if (dryRun) {
  console.log("--dry-run: not sending. Body would be:");
  console.log(JSON.stringify({ ...body, external_google_secret: "<redacted>" }, null, 2));
  process.exit(0);
}

const response = await fetch(api, {
  method: "PATCH",
  headers: authHeaders,
  body: JSON.stringify(body),
  signal: AbortSignal.timeout(30000),
});

if (!response.ok) {
  const text = await response.text();
  console.error(`PATCH ${api} -> HTTP ${response.status}`);
  console.error(text.slice(0, 800));
  process.exit(2);
}

console.log("PATCH accepted. Verifying against the public settings endpoint…");

if (!publishableKey) {
  console.error("No NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY, so the result cannot be verified.");
  console.error("Check Authentication -> Providers -> Google in the dashboard.");
  process.exit(1);
}

// The Management API returns 200 for an accepted write; only /auth/v1/settings
// proves the running auth server is actually serving Google. Cache can lag a
// few seconds, so retry briefly rather than declaring failure on the first read.
let verified = false;
for (let attempt = 1; attempt <= 6; attempt += 1) {
  const probe = await fetch(`${supabaseUrl}/auth/v1/settings`, {
    headers: { apikey: publishableKey },
    signal: AbortSignal.timeout(15000),
  });
  if (probe.ok) {
    const settings = await probe.json();
    if (settings.external?.google === true) {
      verified = true;
      break;
    }
  }
  if (attempt < 6) await new Promise((resolve) => setTimeout(resolve, 2500));
}

if (!verified) {
  console.error("Google still reports as disabled after 6 checks.");
  console.error("The write was accepted, so re-run `npm run auth:check` in a minute.");
  process.exit(1);
}

console.log("");
console.log("Google sign-in is ENABLED and verified.");
console.log(`  Google redirect URI:  https://${projectRef}.supabase.co/auth/v1/callback`);
console.log(`  App callback:         ${siteUrl}/auth/callback`);
console.log("Reload /login — the button appears with no code change or redeploy.");

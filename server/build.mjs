// Builds server/public from the repository before every deploy:
//   - the web app (web/index.html) with the server's AI address and PWA manifest switched on;
//   - the learning content the Android app downloads (content/*.json + content/manifest.json).
// Usage: node server/build.mjs
// Optional env: APP_VERSION_CODE + APP_URL announce the newest Android build to installed apps.
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const pub = join(root, "server", "public");
const assets = join(root, "app", "src", "main", "assets", "content");

// 1. Web app: the same page as the claude.ai version, plus the server's AI endpoint and the PWA manifest.
const web = readFileSync(join(root, "web", "index.html"), "utf8");
const anchor = '<meta name="viewport"';
const at = web.indexOf(anchor);
if (at < 0) throw new Error("web/index.html: viewport meta tag not found");
const lineEnd = web.indexOf("\n", at) + 1;
const serverHead = '<script>window.DANSKLY_API="/api/ai"</script>\n<link rel="manifest" href="/manifest.webmanifest">\n';
writeFileSync(join(pub, "index.html"), web.slice(0, lineEnd) + serverHead + web.slice(lineEnd));

// 2. Content: the files bundled with the Android app. The version is a hash of the files,
//    so installed apps download them again only when something really changed.
const out = join(pub, "content");
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
const hash = createHash("sha256");
for (const name of readdirSync(assets).filter((f) => f.endsWith(".json")).sort()) {
  const text = readFileSync(join(assets, name), "utf8");
  JSON.parse(text); // fail the deploy on broken JSON
  hash.update(name).update(text);
  writeFileSync(join(out, name), text);
}
const gradle = readFileSync(join(root, "app", "build.gradle.kts"), "utf8");
const versionName = (gradle.match(/versionName = "([^"]+)"/) || [])[1];
const manifest = { version: hash.digest("hex").slice(0, 12), schema: 1 };
const code = Number(process.env.APP_VERSION_CODE || 0);
if (code > 0 && process.env.APP_URL?.startsWith("https://") && versionName) {
  manifest.app = { versionCode: code, versionName, url: process.env.APP_URL };
}
writeFileSync(join(out, "manifest.json"), JSON.stringify(manifest, null, 1));
console.log("Built server/public:", JSON.stringify(manifest));

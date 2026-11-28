// Writes a ready-made dictionary entry for every bundled word into
// app/src/main/assets/content/details.json, so the word page opens instantly and offline.
// Run by .github/workflows/details.yml with the OpenCode Go key; resumes where it stopped.
//
// The prompt and the JSON shape match AiTasks.wordDetailsPrompt / ProgressCodec.decodeDetails
// in the app: keep them in sync.
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { createHash } from "node:crypto";

const CONTENT = "app/src/main/assets/content";
const OUT = `${CONTENT}/details.json`;
const KEY = (process.env.OPENCODE_KEYS || "").split(/[\s,]+/).filter(Boolean)[0];
const BASE = process.env.AI_BASE_URL || "https://opencode.ai/inference/go/v1";
const MODELS = (process.env.AI_MODELS || "deepseek-v4.1-flash,deepseek-v4-pro").split(",");
const LIMIT = Number(process.env.LIMIT || 0); // 0 = all words
const PARALLEL = Number(process.env.PARALLEL || 6);
if (!KEY) throw new Error("OPENCODE_KEYS is not set");

const TEACHER =
  "Ти — доброзичливий викладач данської для україномовного студента рівня A2–B1, який готується до модульного тесту " +
  "Danskuddannelse 3, модуль 5. Відповідай українською, данські приклади давай данською. " +
  "Не використовуй markdown (без зірочок, решіток і таблиць), лише простий текст.";
const JSON_ONLY = "\n\nВідповідай лише чистим JSON, без пояснень і без ```.";

const display = (w) => (w.article ? `${w.article} ${w.da}` : w.da);
const isVerb = (w) => w.da.startsWith("at ");

function prompt(w) {
  const examples = isVerb(w)
    ? "examples — 9 коротких речень, по одному на кожну форму з forms, label = назва форми."
    : "examples — 6 коротких речень у різних ситуаціях, label = 1–3 слова про ситуацію.";
  return `${TEACHER}\nТи укладаєш словникову статтю для слова «${display(w)}» (переклад: ${w.uk}). Поверни ЛИШЕ JSON такої форми:\n` +
    '{"pos":"частина мови українською","forms":[["назва форми українською","данська форма"]],"usage":"2–3 речення українською: коли й у яких ситуаціях вживається слово, з чим його не плутати","examples":[{"label":"...","da":"...","uk":"..."}],"phrases":[{"da":"...","uk":"..."}]}\n' +
    "Правила: forms — для дієслова: Інфінітив, Теперішній, Минулий, Перфект, Плюсквамперфект, Майбутній, Умовний, Наказовий, Пасив; " +
    "для іменника: Однина, Однина, означена, Множина, Множина, означена; для прикметника: en-форма, et-форма, множина й означена форма, " +
    `вищий ступінь, найвищий ступінь; для інших частин мови порожній масив. ${examples} phrases — 3–5 частих сполучень або сталих виразів. ` +
    "Рівень A2–B1, природна сучасна данська, кожен приклад з точним перекладом." + JSON_ONLY;
}

/** Keeps only well-formed parts; returns null when the entry is unusable. */
function clean(raw) {
  const text = raw.replace(/<think>[\s\S]*?<\/think>/g, "");
  const m = text.match(/\{[\s\S]*\}/);
  if (!m) return null;
  let o;
  try { o = JSON.parse(m[0]); } catch { return null; }
  const s = (v) => (typeof v === "string" ? v.trim() : "");
  const entry = {
    pos: s(o.pos),
    usage: s(o.usage),
    forms: (Array.isArray(o.forms) ? o.forms : []).filter((f) => Array.isArray(f) && f.length === 2 && s(f[0]) && s(f[1])).map((f) => [s(f[0]), s(f[1])]).slice(0, 12),
    examples: (Array.isArray(o.examples) ? o.examples : []).filter((e) => e && s(e.da) && s(e.uk)).map((e) => ({ label: s(e.label), da: s(e.da), uk: s(e.uk) })).slice(0, 10),
    phrases: (Array.isArray(o.phrases) ? o.phrases : []).filter((e) => e && s(e.da) && s(e.uk)).map((e) => ({ da: s(e.da), uk: s(e.uk) })).slice(0, 6),
  };
  return entry.usage && entry.examples.length >= 3 ? entry : null;
}

async function ask(w) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const model = MODELS[Math.min(attempt >> 1, MODELS.length - 1)];
    try {
      const res = await fetch(`${BASE}/chat/completions`, {
        method: "POST",
        headers: {
          authorization: `Bearer ${KEY}`,
          "content-type": "application/json",
          "user-agent": "danskly-details/1.0",
          "x-opencode-session": "danskly-details-" + createHash("sha256").update(w.da).digest("hex").slice(0, 16),
        },
        body: JSON.stringify({ model, messages: [{ role: "user", content: prompt(w) }], max_tokens: 1500, temperature: 0.4 }),
        signal: AbortSignal.timeout(60000),
      });
      if (res.status === 429) { await new Promise((r) => setTimeout(r, 20000)); continue; }
      if (!res.ok) { console.log(`  ${w.da}: HTTP ${res.status} ${(await res.text()).slice(0, 120)}`); continue; }
      const data = await res.json();
      const entry = clean(data?.choices?.[0]?.message?.content || "");
      if (entry) return entry;
      console.log(`  ${w.da}: unusable answer, retrying`);
    } catch (e) {
      console.log(`  ${w.da}: ${e.message}`);
    }
  }
  return null;
}

const words = JSON.parse(readFileSync(`${CONTENT}/words.json`, "utf8")).words
  .map((w) => ({ da: w.da, uk: w.uk, article: w.article }));
const out = existsSync(OUT) ? JSON.parse(readFileSync(OUT, "utf8")) : {};
let todo = words.filter((w) => !out[w.da]);
if (LIMIT) todo = todo.slice(0, LIMIT);
console.log(`${Object.keys(out).length} ready, ${todo.length} to write`);

const save = () => {
  const sorted = Object.fromEntries(Object.keys(out).sort().map((k) => [k, out[k]]));
  writeFileSync(OUT, JSON.stringify(sorted, null, 0) + "\n");
};
let done = 0, failed = 0;
async function worker() {
  while (todo.length) {
    const w = todo.shift();
    const entry = await ask(w);
    if (entry) out[w.da] = entry; else failed++;
    if (++done % 50 === 0) { save(); console.log(`${done} done, ${failed} failed`); }
  }
}
await Promise.all(Array.from({ length: PARALLEL }, worker));
save();
console.log(`Finished: ${Object.keys(out).length}/${words.length} words have entries, ${failed} failed this run.`);

// Danskly — сервер-посередник для ШІ (Cloudflare Worker).
// Ключі OpenCode Go зберігаються лише в секретах Cloudflare і ніколи не потрапляють у браузер.
//
// Секрети / змінні (Cloudflare → Worker → Settings → Variables and Secrets):
//   OPENCODE_KEYS  (секрет, обов'язково)  — один або кілька ключів, через кому або з нового рядка
//   ACCESS_CODE    (секрет, необов'язково) — код доступу; якщо заданий, без нього ШІ не працює
//   AI_MODELS      — моделі в порядку пріоритету, через кому
//   AI_BASE_URL    — базова адреса OpenAI-сумісного API
//   AI_TIMEOUT_MS  — скільки чекати відповіді від однієї спроби, мс
//   RATE_LIMIT     — скільки запитів до ШІ дозволено з однієї IP-адреси за 10 хвилин

const DEFAULTS = {
  AI_MODELS: "deepseek-v4.1-flash,deepseek-v4-pro,glm-5.3-flash",
  AI_BASE_URL: "https://opencode.ai/inference/go/v1",
  AI_TIMEOUT_MS: "20000",
  RATE_LIMIT: "60",
};
const MAX_MESSAGES = 40;
const MAX_CHARS = 24000;
const MAX_TOKENS = 1500;
const STREAM_IDLE_MS = 30000;
const USER_AGENT = "danskly/1.0";
const SYSTEM_PROMPT =
  "Ти — помічник навчального застосунку Danskly для україномовних людей, які вивчають данську мову (рівні A1–B2, підготовка до Prøve i Dansk). " +
  "Допомагай лише з тим, що стосується вивчення данської: слова, граматика, вимова, переклад, письмо, розмовна практика, життя й іспити в Данії. " +
  "Якщо просять зробити щось стороннє (програмування, довгі тексти на інші теми тощо), ввічливо відмов і запропонуй потренувати данську. " +
  "Суворо дотримуйся формату відповіді, який просить користувач (наприклад, лише JSON).";

// Стан живе в межах одного екземпляра воркера (між запитами, але не назавжди).
const cooldown = new Map(); // ключ → до якого часу його не використовувати
const hits = new Map();     // IP → [мітки часу запитів]
let rr = 0;                 // круговий лічильник, щоб навантаження розподілялося між ключами

const cfg = (env, k) => (env[k] ?? DEFAULTS[k] ?? "").toString().trim();
const list = (s) => s.split(/[\s,]+/).map((x) => x.trim()).filter(Boolean);
const json = (body, status = 200, extra = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra },
  });

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === "/api/ai") return handleAI(request, env, url);
    if (url.pathname === "/api/health") return handleHealth(request, env);
    if (url.pathname === "/api/report") return handleReport(request, env);
    if (env.ASSETS) return env.ASSETS.fetch(request);
    return new Response("Not found", { status: 404 });
  },
};

// ---------- перевірка стану (без розкриття ключів) ----------
async function handleHealth(request, env) {
  const keys = list(cfg(env, "OPENCODE_KEYS"));
  const out = {
    ok: keys.length > 0,
    keys: keys.length,
    models: list(cfg(env, "AI_MODELS")),
    baseUrl: cfg(env, "AI_BASE_URL"),
    accessCode: Boolean(cfg(env, "ACCESS_CODE")),
  };
  if (!keys.length) return json({ ...out, error: "Не задано секрет OPENCODE_KEYS" });
  // Перевіряємо лише, чи відповідає API, і не робимо запитів до моделі.
  const code = request.headers.get("x-access-code") || "";
  if (out.accessCode && !safeEqual(code, cfg(env, "ACCESS_CODE"))) return json(out);
  try {
    const r = await fetchWithTimeout(`${out.baseUrl}/models`, {
      headers: { authorization: `Bearer ${keys[0]}`, "user-agent": USER_AGENT },
    }, 8000);
    out.upstream = r.status;
  } catch (e) {
    out.upstream = "недоступний";
  }
  return json(out);
}

// ---------- скарга на відповідь ШІ (вимога Google Play для ШІ-функцій) ----------
// Скарги пишуться в журнал воркера: Cloudflare → Workers → danskly → Observability → Logs,
// фільтр за "danskly-report". Особистих даних там немає: лише текст відповіді, причина й сесія.
async function handleReport(request, env) {
  if (request.method !== "POST") return json({ error: "Лише POST" }, 405);
  const access = cfg(env, "ACCESS_CODE");
  if (access && !safeEqual(request.headers.get("x-access-code") || "", access))
    return json({ error: "Потрібен код доступу", code: "access_code" }, 401);
  const ip = request.headers.get("cf-connecting-ip") || "local";
  if (!allow("report:" + ip, 20)) return json({ error: "Забагато скарг", code: "rate_limited" }, 429);
  let body;
  try { body = await request.json(); } catch { return json({ error: "Некоректний JSON", code: "bad_request" }, 400); }
  const content = typeof body?.content === "string" ? body.content.slice(0, 4000) : "";
  const reason = ["offensive", "wrong"].includes(body?.reason) ? body.reason : "other";
  if (!content) return json({ error: "Порожня скарга", code: "bad_request" }, 400);
  console.warn(JSON.stringify({
    type: "danskly-report",
    reason,
    session: typeof body.session === "string" ? body.session.slice(0, 64) : "",
    content,
    at: new Date().toISOString(),
  }));
  return json({ ok: true });
}

// ---------- основний запит до ШІ ----------
async function handleAI(request, env, url) {
  if (request.method !== "POST") return json({ error: "Лише POST" }, 405);

  // 1. Запити лише зі своєї сторінки
  const origin = request.headers.get("origin");
  if (origin && origin !== url.origin) return json({ error: "Чужий сайт", code: "forbidden" }, 403);

  // 2. Код доступу (якщо налаштований)
  const access = cfg(env, "ACCESS_CODE");
  if (access && !safeEqual(request.headers.get("x-access-code") || "", access))
    return json({ error: "Потрібен код доступу", code: "access_code" }, 401);

  // 3. Обмеження частоти з однієї IP-адреси
  const ip = request.headers.get("cf-connecting-ip") || "local";
  const limit = parseInt(cfg(env, "RATE_LIMIT"), 10) || 60;
  if (!allow(ip, limit)) return json({ error: "Забагато запитів, зачекай кілька хвилин", code: "rate_limited" }, 429, { "retry-after": "120" });

  // 4. Перевірка тіла запиту
  let body;
  try { body = await request.json(); } catch { return json({ error: "Некоректний JSON", code: "bad_request" }, 400); }
  const msgs = Array.isArray(body?.messages) ? body.messages : null;
  if (!msgs || !msgs.length || msgs.length > MAX_MESSAGES) return json({ error: "Некоректні повідомлення", code: "bad_request" }, 400);
  let chars = 0;
  for (const m of msgs) {
    if (!m || !["user", "assistant"].includes(m.role) || typeof m.content !== "string") return json({ error: "Некоректне повідомлення", code: "bad_request" }, 400);
    chars += m.content.length;
  }
  if (chars > MAX_CHARS) return json({ error: "Запит задовгий", code: "too_long" }, 413);
  const stream = body.stream === true;
  const session = typeof body.session === "string" ? body.session.slice(0, 64) : "";

  const keys = list(cfg(env, "OPENCODE_KEYS"));
  if (!keys.length) return json({ error: "Сервер не налаштований: немає ключів", code: "no_keys" }, 503);
  const models = list(cfg(env, "AI_MODELS"));
  const base = cfg(env, "AI_BASE_URL").replace(/\/+$/, "");
  const timeout = parseInt(cfg(env, "AI_TIMEOUT_MS"), 10) || 20000;

  const payload = (model) => JSON.stringify({
    model,
    messages: [{ role: "system", content: SYSTEM_PROMPT }, ...msgs],
    max_tokens: MAX_TOKENS,
    temperature: body.json ? 0.4 : 0.7,
    stream,
  });

  // 5. Фолбек: кожна модель по черзі, для кожної — усі ключі
  const tried = [];
  for (const model of models) {
    for (const key of orderKeys(keys)) {
      const tag = `${model}#${keys.indexOf(key) + 1}`;
      let res;
      try {
        res = await fetchWithTimeout(`${base}/chat/completions`, {
          method: "POST",
          headers: {
            authorization: `Bearer ${key}`,
            "content-type": "application/json",
            "user-agent": USER_AGENT,
            ...(session ? { "x-opencode-session": session } : {}),
          },
          body: payload(model),
        }, timeout);
      } catch (e) {
        tried.push(`${tag}:timeout`);
        cool(key, 15000);
        continue; // таймаут або мережа → наступний ключ
      }
      if (res.ok) {
        const headers = { "cache-control": "no-store", "x-danskly-model": model, "x-danskly-tries": String(tried.length + 1) };
        if (stream) {
          return new Response(guardStream(res.body), { headers: { ...headers, "content-type": "text/event-stream; charset=utf-8" } });
        }
        const data = await readJsonWithTimeout(res, timeout * 2).catch(() => null);
        const text = data?.choices?.[0]?.message?.content;
        if (typeof text === "string" && text.trim()) return json({ text, model }, 200, headers);
        tried.push(`${tag}:empty`);
        continue;
      }
      tried.push(`${tag}:${res.status}`);
      res.body?.cancel?.();
      if (res.status === 401 || res.status === 403) { cool(key, 10 * 60000); continue; } // поганий ключ або немає підписки
      if (res.status === 429) { cool(key, 60000); continue; }                             // ліміт ключа
      if (res.status >= 500) { cool(key, 15000); continue; }                               // збій сервісу
      if (res.status === 404 || res.status === 400 || res.status === 422) break;           // модель недоступна → наступна модель
    }
  }
  console.log("AI fallback exhausted:", tried.join(" "));
  return json({ error: "Усі ключі й моделі зараз недоступні. Спробуй за хвилину.", code: "unavailable", tried: tried.length }, 503);
}

// ---------- допоміжне ----------
function orderKeys(keys) {
  const now = Date.now();
  const start = rr++ % keys.length;
  const rotated = keys.slice(start).concat(keys.slice(0, start));
  const fresh = rotated.filter((k) => (cooldown.get(k) || 0) <= now);
  const cooled = rotated.filter((k) => (cooldown.get(k) || 0) > now);
  return fresh.concat(cooled); // охолоджені ключі — в кінці, як остання надія
}
function cool(key, ms) { cooldown.set(key, Date.now() + ms); }

function allow(ip, limit) {
  const now = Date.now(), win = 10 * 60000;
  const arr = (hits.get(ip) || []).filter((t) => now - t < win);
  if (arr.length >= limit) { hits.set(ip, arr); return false; }
  arr.push(now); hits.set(ip, arr);
  if (hits.size > 5000) for (const [k, v] of hits) if (!v.length || now - v[v.length - 1] > win) hits.delete(k);
  return true;
}

function safeEqual(a, b) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

async function fetchWithTimeout(url, init, ms) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort("timeout"), ms);
  try {
    return await fetch(url, { ...init, signal: ac.signal });
  } finally {
    clearTimeout(t);
  }
}

async function readJsonWithTimeout(res, ms) {
  let t;
  const timer = new Promise((_, rej) => { t = setTimeout(() => rej(new Error("timeout")), ms); });
  try { return await Promise.race([res.json(), timer]); } finally { clearTimeout(t); }
}

// Передає потік як є, але обриває його, якщо модель надовго замовкла.
function guardStream(src) {
  const reader = src.getReader();
  return new ReadableStream({
    async pull(controller) {
      let t;
      const idle = new Promise((res) => { t = setTimeout(() => res({ idle: true }), STREAM_IDLE_MS); });
      const r = await Promise.race([reader.read(), idle]);
      clearTimeout(t);
      if (r.idle) {
        controller.enqueue(new TextEncoder().encode('data: {"error":"timeout"}\n\n'));
        reader.cancel().catch(() => {});
        controller.close();
        return;
      }
      if (r.done) { controller.close(); return; }
      controller.enqueue(r.value);
    },
    cancel() { reader.cancel().catch(() => {}); },
  });
}

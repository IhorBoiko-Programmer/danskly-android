# Data safety form — answers (Play Console → App content → Data safety)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all user data encrypted in transit? | **Yes** (HTTPS only) |
| Do you provide a way for users to request that their data is deleted? | **Yes** — data is on device; "Скинути прогрес" in Settings / uninstall. Server keeps no user data except reports (30 days). |

**Data types collected**

| Type | Collected | Shared | Optional? | Purpose | Processed ephemerally? |
|---|---|---|---|---|---|
| App activity → Other user-generated content (text sent to the AI tutor / writing check, reported AI answers) | Yes | Yes — with the AI model provider, to generate the answer | Optional (only when using AI features) | App functionality | Yes for AI requests (not stored); reports stored ≤30 days |
| App info and performance → Crash logs / diagnostics | No | — | — | — | — |
| Device or other IDs | No (the session ID is random, generated per install, not a device ID) | — | — | — | — |
| Location, Personal info, Financial, Health, Messages, Photos, Audio, Files, Calendar, Contacts | No | — | — | — | — |

**Other App content forms**
- Ads: **No ads**.
- Target audience: **18+** (or 13+); not designed for children → avoid "Designed for Families".
- Content rating (IARC questionnaire): Education app; user-generated content: **No** sharing between users; AI-generated content: **Yes** → answer the generative-AI questions; expected rating: Everyone / PEGI 3.
- Generative AI: the app has an in-app **"⚑ Поскаржитися на відповідь"** button under every AI answer (tutor and writing check). Reports go to `/api/report` on the server.
- Government apps: No. Financial features: No. Health: No. News: No.

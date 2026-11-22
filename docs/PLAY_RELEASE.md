# Як опублікувати Danskly в Google Play

## 0. Що вже готово в репозиторії
- `app/` — застосунок (targetSdk 36, minSdk 26, R8, підпис із секретів).
- GitHub Actions збирає **`danskly-aab`** (App Bundle для Play) і **`danskly-apk`**.
- `store/` — тексти сторінки (uk, en), відповіді Data safety, іконка 512×512, банер 1024×500, план промо-відео.
- `docs/privacy-policy.md` — політика конфіденційності.
- Скріншоти телефона: артефакт `screenshots` (папка `light/`).

## 1. Акаунт розробника (один раз)
1. Відкрий https://play.google.com/console/signup і увійди своїм Google-акаунтом.
2. Обери **«Особистий» (Personal)** акаунт.
3. Сплати реєстраційний внесок **$25** (один раз).
4. Пройди перевірку особи: документ, адреса, номер телефона. Це займає від кількох днів.
5. **Важливо для нових особистих акаунтів:** перед виходом у «Робочу версію» (Production) потрібне **закрите тестування з щонайменше 12 тестувальниками протягом 14 днів поспіль**. Збери 12 друзів чи одногрупників із курсів данської, і плануй це заздалегідь.

## 2. Політика конфіденційності онлайн (GitHub Pages)
1. У файлі `docs/privacy-policy.md` заміни e-mail на свій.
2. У репозиторії відкрий **Settings → Pages → Source: Deploy from a branch → `main` / `docs`** і натисни **Save**.
3. Через 1–2 хвилини сторінка буде доступна за адресою `https://<логін>.github.io/danskly/privacy-policy`.

## 3. Ключ підпису
Upload key (`danskly-upload.jks`) і паролі — у файлі `SECRETS.txt`, який ти отримав окремо (**не в репозиторії**).
1. Додай 4 секрети в GitHub: **Settings → Secrets and variables → Actions**.
2. **Actions → Android CI → Run workflow** (або будь-який пуш). Завантаж артефакт `danskly-aab`.
3. У Play Console залиш увімкненим **Play App Signing**: Google зберігає основний ключ, а ти підписуєш лише upload-ключем.

## 4. Створи застосунок у Play Console
**Create app**:
- Назва: `Danskly: данська українською`
- Мова за замовчуванням: **Українська (uk)**
- Тип: App
- Безкоштовний

## 5. Розділ «App content» (вміст застосунку)
Відповіді беремо з `store/data-safety.md`:
- **Privacy policy**: URL із кроку 2.
- **Ads**: No.
- **App access**: всі функції доступні без входу. Якщо на сервері ввімкнено ACCESS_CODE, додай код в інструкції для рецензента.
- **Content rating**: заповни анкету IARC (категорія Education).
- **Target audience**: 18+ (або 13+).
- **Data safety**: як у таблиці.
- **Generative AI**: так. Скарги — кнопка «⚑ Поскаржитися на відповідь» під кожною відповіддю ШІ.

## 6. Сторінка в магазині (Main store listing)
- Тексти: `store/listing-uk.md`. Додай переклад англійською: **Translations → en-US**, `store/listing-en.md`.
- **App icon**: `store/graphics/icon-512.png`.
- **Feature graphic**: `store/graphics/feature-graphic-1024x500.png`.
- **Phone screenshots** (2–8 штук): зі `screenshots/light/`. Рекомендовані:
  - `01_home`
  - `02_today`
  - `07_word_verb`
  - `12_ex_build`
  - `16_reading`
  - `19_pronunciation`
  - `22_tutor`
  - `17_grammar`
- Категорія: **Education**, e-mail для зв'язку.

## 7. Тестування → реліз
1. **Testing → Closed testing → Create track** → завантаж `.aab`.
2. Додай список e-mail тестувальників (≥12) і надішли їм посилання для участі.
3. Через 14 днів: **Production → Create release** → той самий або новіший `.aab` → **Review release → Start rollout**.
4. Перевірка Google триває від кількох годин до 7 днів.

## 8. Оновлення
Кожен пуш у `main` збирає новий `.aab` з `versionCode` = номер запуску CI, тож номер завжди зростає. Онови `versionName` в `app/build.gradle.kts`, завантаж новий `.aab` у потрібний трек і додай опис змін (release notes).

## Чек-лист перед відправкою
- [ ] Адреса сервера ШІ задана: змінна репозиторію `DANSKLY_AI_URL` (Settings → Variables).
- [ ] На сервері є ключі; `/api/health` показує `keys > 0`.
- [ ] Політика конфіденційності відкривається за посиланням.
- [ ] E-mail замінено в політиці й на сторінці магазину.
- [ ] Upload key і паролі збережені поза репозиторієм.
- [ ] Встановив APK на свій телефон і пройшов усі розділи.

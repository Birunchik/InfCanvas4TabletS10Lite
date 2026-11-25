# AGENTS.md — правила для AI-агентов в этом репозитории

Ты работаешь в Android-проекте **InfCanvas** (бесконечный холст: штрихи, фото, папки/доски).
Этот файл — краткая память проекта для агентов, которые попали сюда впервые.

Все файлы для агентов живут в **`.ai/`**, чтобы не засорять корень:

```
.ai/
├── AGENTS.md                 ← ты здесь: правила и ловушки
├── docs/
│   ├── PROJECT_MAP.md        модули, слои, граф зависимостей, «куда что класть»
│   └── TESTING.md            чем и как тестировать
└── skills/
    └── android-module/
        └── SKILL.md          процедуры: новая кисть / инструмент / объект / формат
```

**Порядок чтения перед правками:**

1. `.ai/AGENTS.md` (этот файл) — правила и ловушки.
2. `.ai/docs/PROJECT_MAP.md` — модули, граф, «куда что класть».
3. `.ai/docs/TESTING.md` — стек тестов и команды.
4. Для задач «добавить кисть / инструмент / новый тип объекта / изменить формат» —
   `.ai/skills/android-module/SKILL.md`.

> Корневой `AGENTS.md` **намеренно отсутствует** (чтобы не мусорить), поэтому DSH
> автоматически этот файл не подгружает. Если хочешь, чтобы правила применялись сами —
> верни в корень либо симлинк `AGENTS.md -> .ai/AGENTS.md`, либо однострочный указатель
> «читать .ai/AGENTS.md» (решение — за человеком).

## Правила

- **Gradle.** В песочнице `gradlew` падает на локе `~/.gradle/wrapper/dists/...zip.lck`.
  Для проверок сборки передай изолированные каталоги **внутри workspace**:
  ```powershell
  $env:GRADLE_USER_HOME = Join-Path (Get-Location) 'build/.gradle-home'
  $env:ANDROID_USER_HOME = Join-Path (Get-Location) 'build/.android-home'
  # sdk.dir в local.properties хранит Java-эскейпленные двоеточия (\:) — их нужно разэкранировать
  $env:ANDROID_SDK_ROOT = (Select-String -Path local.properties -Pattern 'sdk\.dir=(.*)' |
      ForEach-Object { $_.Matches[0].Groups[1].Value }) -replace '\\:', ':'
  ```
  (первый прогон скачает Gradle заново в `build/.gradle-home` — это долго, а сам
  android-плагин, Compose и тесты ещё не проверялись в таком режиме: выводы помечай как
  непроверенные, если прогона не было).
- **Коммиты — по согласованию.** Сообщения: `type(scope): description` (см. `git log`).
  Не делай `git commit` и `git push`, пока пользователь не попросил.
- **Не трогай `docs/.obsidian/`** — это артефакт Obsidian.
- **Правки production-кода требуют теста в том же модуле.** Где лежат тесты — `.ai/docs/TESTING.md`.
- **`grep`, а не догадки.** Граф зависимостей проверяй скриптом `scripts/check-module-boundaries.ps1`
  (и он же гоняется в CI), а не памятью.
- **Не переименовывай пакеты** без причины: пакеты намеренно сохранены при переносе
  файлов между модулями — так history и импорты не ломаются.

## Жёсткие инварианты (нарушение = баг)

1. `core:domain` не зависит от Android UI/фреймворков кроме `Uri`/`File` в портах.
   Нельзя: Compose, Room, `presentation`, `data`.
2. `core:data` не импортирует `presentation`. `presentation` не импортирует `core:data`,
   только порты из `core:domain`. Единственное исключение — `:app`/`AppContainer`.
3. **Формат файла доски** (`core/data/.../BoardFileStorage.kt`). Сейчас
   `FORMAT_VERSION = 1`, и **обратная совместимость удалена** (см. KDoc файла): чтение
   старых версий больше не поддерживается. Поэтому:
   - любое изменение раскладки/порядка полей **или `BrushType`** (он пишется как `ordinal`)
     требует либо `FORMAT_VERSION++` + фикстур старых версий в `BoardFileStorageTest`,
     либо сознательного решения о сбросе совместимости (тогда это упоминается в PR и
     в KDoc файла);
   - порядок полей: `magic, version, viewport, strokes, images`; внутри штриха
     `id, brush.ordinal, color, baseSize, opacity, bounds, points`;
   - **Не добавляй тип в середину `BrushType`** — он станет другим `ordinal`'ом в уже
     сохранённых файлах.
4. Пакеты `com.infinitecanvas.board.*` — публичный контракт между модулями. Публичные
   сущности в `core:data` (DAO, `ImageAssetStorage`, `BoardFileStorage`) — деталь реализации,
   которую видит только `AppContainer`.
5. Производительность холста: в draw-фазе `InfiniteCanvasView`/`renderer/*` **нельзя
   аллоцировать** (`Path`, `Rect`, `Offset`, списки) — переиспользуй `CanvasRenderState`.

## Известные ловушки

- **Gradle wrapper lock** — см. правило про Gradle выше.
- **`app/build.gradle.kts`: `composeCompiler { stabilityConfigurationFiles }`** — файл
  `app/stability_config.conf`; при переносе Compose-кода в новый модуль эта конфигурация
  (и сам файл) должны переехать вместе с ним.
- **Тесты `:core:data`** — Robolectric `@Config(sdk = [31])`; `isIncludeAndroidResources = true`
  уже включён. `@GraphicsMode(NATIVE)` нужен только тестам с растром
  (`ThumbnailGeneratorTest`, `ImageAssetStorageTest`) — не копируй его механически.
- **`:core:domain` тесты** — чистый JVM (JUnit + Truth), без Robolectric и MockK.
- **`BoardViewModelTest`** управляет дебаунсом виртуальным временем: `StandardTestDispatcher`
  + `advanceTimeBy`, а не `Unconfined`.
- **`:app` androidTest используют `ImageAssetStorage` напрямую** (осознанно — тест гоняет
  реальные файлы), поэтому у `:app` есть зависимость от `:core:data`.
- **CI** — `.github/workflows/ci.yml`; границы модулей проверяются
  `scripts/check-module-boundaries.ps1` в job `boundaries`. Перед тем как ссылаться на CI,
  убедись, что прогон реально зелёный.
- **`gradlew` должен быть `100755` в индексе git.** На Windows `core.filemode=false`, и
  коммит может сбросить бит исполнения → в Linux CI `./gradlew: Permission denied (126)`.
  Проверка: `git ls-files -s gradlew` (ждём `100755`); починка:
  `git update-index --chmod=+x gradlew` и коммит. В `.gitattributes` закреплено `eol=lf`
  для `gradlew`, чтобы CRLF не превращал shebang в `/bin/sh^M`.
  Обходной путь в workflow — `bash ./gradlew`, но он не заменяет починку режима.

## Текущее состояние модуляризации

Сделано: `:core:domain` (+ порты), `:core:data`, presentation переведён на порты,
есть доменные/данные/ViewModel-тесты. Дальше — по плану `.ai/../docs/Modularization.md`
и `docs/MODULARIZATION_PLAN.md` (осторожно: в `Modularization.md` есть обрыв после
глобальной замены путей — сверяйся с фактическим деревом).
Открытые долги: `api` вместо `implementation` в `core:data`, размер APK/время сборки —
обновлять по факту.

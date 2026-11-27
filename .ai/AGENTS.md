# AGENTS.md — правила для AI-агентов в этом репозитории

Ты работаешь в Android-проекте **InfCanvas** (бесконечный холст: штрихи, фото, папки/доски).
Этот файл — краткая память проекта для агентов, которые попали сюда впервые.

Все файлы для агентов живут в **`.ai/`**, чтобы не засорять корень:

```
.ai/
├── AGENTS.md                 ← ты здесь: правила и ловушки
├── docs/
│   ├── PROJECT_MAP.md        модули, слои, граф зависимостей, «куда что класть»
│   ├── TESTING.md            чем и как тестировать
│   └── MCP_KOTLIN_LSP.md     связка DSH→MCP→kotlin-lsp: что починено и как запускать
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
5. Если работаешь через MCP-инструменты `mcp__kotlin-lsp__*` (hover/symbols/references) —
   `.ai/docs/MCP_KOTLIN_LSP.md`.

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
- **Gradle-вывод — через `scripts/gradle-issues.ps1`.** Он печатает только проблемы и сохраняет
  полный лог в `build/logs/`; код возврата совпадает с Gradle. Сырой вывод `gradlew` читать
  целиком не нужно — это сотни строк логов и предупреждений JVM.
- **Локальные скиллы проекта** подключены через `@deepseek-ai/dsh-skill-filesystem`
  (`.ai/skills`; на другое профильное имя смотрит `customSkillDirs` в
  `$DSH_HOME/profiles/<profile>/cordis.patch.yml`). Новый скилл — каталог
  `.ai/skills/<name>/SKILL.md` с frontmatter `name` + `description`.
- **Не переименовывай пакеты** без причины: пакеты намеренно сохранены при переносе
  файлов между модулями — так history и импорты не ломаются.

## Жёсткие инварианты (нарушение = баг)

1. `core:domain` не зависит от Android UI/фреймворков кроме `Uri`/`File` в портах.
   Нельзя: Compose, Room, `presentation`, `data`, `feature`.
2. `core:data` не импортирует `presentation`/`feature`/`core:canvas`;
   `core:canvas` не импортирует `core:data`/экраны/фичи; `feature:*` не импортируют
   `core:data` и друг друга. Единственное место, где создаются реализации, — `:app`
   (`AppContainer`). Граф проверяется `scripts/check-module-boundaries.ps1`.
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
4. Пакеты `com.infinitecanvas.board.*` — публичный контракт между модулями. **Namespace
   модуля уникален** (совпадает с его `R`-классом): импортировать чужой `R` нельзя
   (кроме `com.infinitecanvas.designsystem.R`). В `core:data` деталь реализации —
   `BoardDao`, `AppDatabase`, `BoardRepositoryImpl` (все `internal`); наружу отдаётся
   доменный `BoardRepository` через `createBoardRepository(...)`, его и вызывает
   `AppContainer`. `ImageAssetStorage`, `BoardFileStorage`, `ThumbnailGenerator`
   остаются публичными — их видит только `AppContainer`.
5. Публичный API `:core:canvas` — только `InfiniteCanvasView`, `BoardImageCache`,
   `ImageDecoder`, `computeSelectionWorldBounds`; остальное `internal`. Входы фич —
   `HomeRoute`/`BoardRoute`; ViewModel/контроллеры/HUD — детали модуля.
6. Производительность холста: в draw-фазе `InfiniteCanvasView`/`renderer/*` **нельзя
   аллоцировать** (`Path`, `Rect`, `Offset`, списки) — переиспользуй `CanvasRenderState`.

## Известные ловушки

- **Gradle wrapper lock** — см. правило про Gradle выше.
- **Convention-плагины** — `build-logic/` (included build). Библиотеки подключают
  `id("infcanvas.android.library")` или `id("infcanvas.android.compose")` и задают только
  `namespace`; compileSdk/minSdk/Java/testOptions уже там. Не дублируй их в модулях.
- **Compose stability config** — `gradle/stability_config.conf`; модули Compose ссылаются
  на него через `rootProject.file(...)`. При добавлении стабильного доменного класса
  (передаётся в @Composable) дописывай его сюда.
- **Тесты `:core:data`** — Robolectric `@Config(sdk = [31])`; `isIncludeAndroidResources = true`
  уже включён. `@GraphicsMode(NATIVE)` нужен только тестам с растром
  (`ThumbnailGeneratorTest`, `ImageAssetStorageTest`) — не копируй его механически.
- **`:core:domain` тесты** — чистый JVM (JUnit + Truth), без Robolectric и MockK.
- **`BoardViewModelTest`** (в `:feature:board`) управляет дебаунсом виртуальным временем:
  `StandardTestDispatcher` + `advanceTimeBy`, а не `Unconfined`.
- **`:core:canvas` androidTest** используют `FakeImageStore` из `:core:testing`, а не
  реальный `ImageAssetStorage` (иначе тест тянет `:core:data`).
- **Фикстуры** — только `:core:testing`; не дублируй `BrushFixtures`/`StrokeRealFixtures`
  между `test` и `androidTest`.
- **CI** — `.github/workflows/ci.yml`; границы модулей проверяются
  `scripts/check-module-boundaries.ps1` в job `boundaries`, зависимости — `buildHealth`
  в job `dependency-analysis`. Перед тем как ссылаться на CI, убедись, что прогон реально зелёный.
- **`gradlew` должен быть `100755` в индексе git.** На Windows `core.filemode=false`, и
  коммит может сбросить бит исполнения → в Linux CI `./gradlew: Permission denied (126)`.
  Проверка: `git ls-files -s gradlew` (ждём `100755`); починка:
  `git update-index --chmod=+x gradlew` и коммит. В `.gitattributes` закреплено `eol=lf`
  для `gradlew`, чтобы CRLF не превращал shebang в `/bin/sh^M`.
  Обходной путь в workflow — `bash ./gradlew`, но он не заменяет починку режима.

## Текущее состояние модуляризации

Фазы 0–5 плана выполнены: `:core:domain` (+ порты), `:core:data`, presentation переведён
на порты, затем вынесены `:core:canvas`, `:core:designsystem`, `:feature:board`,
`:feature:home`, фикстуры — в `:core:testing`, конфигурация — в `build-logic/`.
`:app` — только `MainActivity` + `AppContainer` + `AppNavHost`.
Долг `api` вместо `implementation` в `core:data` закрыт: DAO и `BoardRepositoryImpl`
теперь `internal`, наружу — `createBoardRepository(...)`; Room стал `implementation`.
Размер APK/время сборки — обновлять по факту.

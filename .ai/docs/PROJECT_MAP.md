# PROJECT_MAP — карта проекта InfCanvas

Документы для агентов живут в `.ai/` (`AGENTS.md`, `docs/PROJECT_MAP.md`, `docs/TESTING.md`,
`skills/`), чтобы не засорять корень репозитория.

Обновляй этот файл, когда меняется структура модулей или граф зависимостей.
Цифры (файлы/строки) — снимок на дату последнего обновления; проверяй `git ls-files`.

## 1. Модули

| Модуль | Тип | Что внутри | Публичный вход |
|---|---|---|---|
| `:app` | Android application | `MainActivity`, `AppContainer.kt`, `AppNavHost.kt`, манифест, иконки, backup/file_paths, smoke-тесты | `MainActivity` → `AppNavHost` |
| `:core:domain` | Android library (без Compose/Room) | `model/`, `spatial/` (`Quadtree`, `LassoSelector`), `repository/` (`BoardRepository`), `port/` (`ImageStore`, `BoardThumbnailer`) | Kotlin-классы и интерфейсы |
| `:core:data` | Android library (Room + files) | `local/db/` (Room), `local/storage/` (`BoardFileStorage`, `ImageAssetStorage`, `ThumbnailGenerator`), `repository/BoardRepositoryImpl` | реализации портов; создаются в `AppContainer` |
| `:core:canvas` | Android library (Compose) | `InfiniteCanvasView`, `gestures/`, `renderer/`, `StrokeGeometryCache`, `BoardImageCache` | `InfiniteCanvasView`, `BoardImageCache`, `ImageDecoder`, `computeSelectionWorldBounds` |
| `:core:designsystem` | Android library (Compose) | `presentation/theme/ui/` (`Theme`, `Color`, `Type`), `colors.xml`, `themes.xml`, `app_name` | `InfCanvasTheme`, ресурсы темы |
| `:core:testing` | Android library | `FakeBoardRepository`, `FakeImageStore`, `FakeBoardThumbnailer`, `MainDispatcherRule`, `FakeClock`, `*Fixtures` | тестовые фикстуры (только `testImplementation`/`androidTestImplementation`) |
| `:feature:home` | Android library (Compose) | `HomeScreen`, `HomeViewModel`, `HomeRoute`, карточки, диалоги, sidebar, `strings.xml` | `HomeRoute` |
| `:feature:board` | Android library (Compose) | `BoardScreen`, `BoardViewModel` + 4 контроллера, HUD, палитра, `strings.xml` | `BoardRoute` |

`build-logic/` — included build с convention-плагинами `infcanvas.android.library`
и `infcanvas.android.compose` (compileSdk 37.1, minSdk 31, Java 11, Compose, testOptions).
Подключены во всех библиотечных модулях (`:core:*`, `:feature:*`); `:app` остаётся
на application-плагине и задаёт свои buildType-настройки.

## 2. Граф зависимостей (фактический)

```
:app ──> :feature:home ──> :core:designsystem
 │             └────────> :core:domain
 ├──> :feature:board ──> :core:canvas ──> :core:domain
 │             └────────> :core:domain
 ├──> :core:data ───────> :core:domain
 └──> :core:designsystem

test-only: modules ──> :core:testing ──> :core:domain
```

- `:app` нужен `:core:data` только для `AppContainer`; больше никто в data не ходит.
- `:feature:*` не зависят от `:core:data` и друг от друга.
- `:core:canvas` не зависит от data/экранов/фич.
- В `:core:data` Room исторически подключён через `api` (долг: DAGP советует `implementation`).

**Правило проверки:** `pwsh scripts/check-module-boundaries.ps1` (exit 1 при нарушении:
запрещённые project-зависимости, импорты слоёв, чужие `R`, дубли namespace).
Он же гоняется в CI job `boundaries`.

## 3. Куда класть код (решение по слоям)

| Задача | Куда идти | Тест в … |
|---|---|---|
| Модель / геометрия / правило | `core:domain/.../model` или `/spatial` | `core:domain/src/test` (JVM, без Robolectric) |
| Контракт доступа к данным | `core:domain/.../repository` (интерфейс) или `.../port` | фейки в `:core:testing` |
| Хранение (БД, файлы, превью) | `core:data` | `core:data/src/test` (Robolectric) |
| Отрисовка и жесты | `core:canvas/.../presentation/canvas/**` | `core:canvas/src/test` + `src/androidTest` |
| Состояние экрана | `feature/<screen>/...` (`*Controller`, `*ViewModel`) | `feature/<screen>/src/test` |
| UI экрана / HUD | `feature/<screen>` | `feature/<screen>/src/androidTest` |
| Тема, цвета, типографика | `core:designsystem` | — |
| Фикстуры и фейки | `core:testing/src/main` | — |
| Composition root | `app/.../AppContainer.kt` | — |

## 4. Ключевые классы по подсистемам

- **Данные:** `BoardRepositoryImpl` (порядок «файл → Room»), `BoardFileStorage`
  (бинарный формат), `ImageAssetStorage` (импорт, EXIF, downsample), `ThumbnailGenerator`.
- **Домен:** `Stroke`/`WorldBounds` (инвариант `min <= max` держим сами), `Brush.calculateWidth`
  (единый источник ширины — не дублировать), `Quadtree` (не thread-safe, только Main).
- **Холст:** `InfiniteCanvasView` (один Canvas, порядок слоёв), `CanvasGestureHandler`
  (приоритеты жестов важны; `internal`), `CanvasRenderState` (буферы без аллокаций),
  `StrokeGeometryCache` (`internal`).
- **Экраны:** `BoardRoute` → `BoardViewModel` + 4 контроллера; `HomeRoute` → `HomeViewModel`.

## 5. Формат доски (контракт)

`core/data/.../storage/BoardFileStorage.kt`: `MAGIC = "ICB1"`, `FORMAT_VERSION = 1`,
только текущая версия (старые не читаются). Поля: `viewport → strokes → images`, внутри
штриха `brush.type.ordinal` — то есть порядок `BrushType` в enum является частью файла.
Счётчики из файла зажимаются `MAX_STROKE_COUNT/MAX_POINT_COUNT/MAX_IMAGE_COUNT` (защита от OOM).

## 6. Полезные команды

```bat
:: граф зависимостей модуля
.\gradlew.bat :app:dependencies --configuration debugCompileClasspath
:: проверка границ между модулями
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-module-boundaries.ps1
:: анализ зависимостей (DAGP)
.\gradlew.bat buildHealth
:: список файлов и размер модуля
git ls-files "core/**/*.kt" "feature/**/*.kt" "app/src/**/*.kt"
```

## 7. Документы в `docs/`

- `EXTENDING.md` — как добавлять фичи (кисти, инструменты, объекты), пути соответствуют
  модульной структуре (canvas → `core/canvas`, board → `feature/board`).
- `MODULARIZATION_PLAN.md` — план и статус модуляризации.
- `.ai/docs/TESTING.md` — стек тестов и команды запуска.

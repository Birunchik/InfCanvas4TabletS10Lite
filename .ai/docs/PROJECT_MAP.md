# PROJECT_MAP — карта проекта InfCanvas

Документы для агентов живут в `.ai/` (`AGENTS.md`, `docs/PROJECT_MAP.md`, `docs/TESTING.md`,
`skills/`), чтобы не засорять корень репозитория.

Обновляй этот файл, когда меняется структура модулей или граф зависимостей.
Цифры (файлы/строки) — снимок на дату последнего обновления; проверяй `git ls-files`.

## 1. Модули

| Модуль | Тип | Что внутри | Публичный вход |
|---|---|---|---|
| `:app` | Android application | `MainActivity`, `di/AppContainer`, `presentation/**` (canvas, home, board, navigation, theme, ресурсы), `androidTest` | `MainActivity` → `AppNavHost` |
| `:core:domain` | Android library (без Compose/Room) | `model/` (модели), `spatial/` (`Quadtree`, `LassoSelector`), `repository/` (`BoardRepository`), `port/` (`ImageStore`, `BoardThumbnailer`) | публичные Kotlin-классы и интерфейсы |
| `:core:data` | Android library (Room + files) | `local/db/` (Room), `local/storage/` (`BoardFileStorage`, `ImageAssetStorage`, `ThumbnailGenerator`), `repository/BoardRepositoryImpl` | реализации портов; создаётся в `AppContainer` |

Модулей `:core:canvas`, `:core:designsystem`, `:feature:*` **ещё нет** — это фазы 4–5
плана (`docs/Modularization.md`).

## 2. Граф зависимостей (фактический)

```
:app ──> :core:data ──api──> :core:domain
  └───> :core:domain
```

- В `:core:data` Room подключён через `api` (долг: должно быть `implementation`).
- `:app` нужен `:core:data` только для `AppContainer`; androidTest используют
  `ImageAssetStorage` напрямую намеренно.

**Правило проверки:** `scripts/check-module-boundaries.ps1` (exit 1 при нарушении).
Если добавляешь новый модуль — добавь его в скрипт и в §1–2.

## 3. Куда класть код (решение по слоям)

| Задача | Куда идти | Тест в … |
|---|---|---|
| Модель / геометрия / правило | `core:domain/.../model` или `/spatial` | `core:domain/src/test` (JVM, без Robolectric) |
| Контракт доступа к данным | `core:domain/.../repository` (интерфейс) или `.../port` | фейки в `app/src/test/.../testing` |
| Хранение (БД, файлы, превью) | `core:data` | `core:data/src/test` (Robolectric) |
| Состояние экрана (StateFlow, мутации) | `app/.../presentation/screen/<screen>/` (`*Controller`, `*ViewModel`) | `app/src/test` |
| Отрисовка и жесты | `app/.../presentation/canvas/**` | `app/src/test` + `app/src/androidTest` |
| Composition root / флаги | `app/.../di/AppContainer.kt` | — |

## 4. Ключевые классы по подсистемам

- **Данные:** `BoardRepositoryImpl` (порядок «файл → Room»), `BoardFileStorage`
  (бинарный формат), `ImageAssetStorage` (импорт, EXIF, downsample), `ThumbnailGenerator`.
- **Домен:** `Stroke`/`WorldBounds` (инвариант `min <= max` держим сами), `Brush.calculateWidth`
  (единый источник ширины — не дублировать), `Quadtree` (нот thread-safe, только Main).
- **Холст:** `InfiniteCanvasView` (один Canvas, порядок слоёв), `CanvasGestureHandler`
  (приоритеты жестов важны), `CanvasRenderState` (буферы без аллокаций), `StrokeGeometryCache`.
- **Экраны:** `BoardViewModel` + 4 контроллера (`Stroke`, `Image`, `Tool`, `Viewport`);
  `HomeViewModel` (combine фолдеров, фильтра и поиска).

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
pwsh scripts/check-module-boundaries.ps1
:: список файлов и размер модуля
git ls-files "core/**/*.kt" "app/src/**/*.kt"
```

## 7. Документы в `docs/`

- `EXTENDING.md` — как добавлять фичи (кисти, инструменты, объекты), осторожно: после
  массовой замены путей там могут быть неточные пути — сверяйся с деревом.
- `Modularization.md` / `MODULARIZATION_PLAN.md` — план модуляризации (в первом есть
  обрыв; фактическое состояние — §1–2 этого файла).
- `.ai/docs/TESTING.md` — стек тестов и команды запуска.

---
name: android-module
description: Use when adding or changing code in the InfCanvas Android project: a new brush, canvas tool, board object type, file-format change, new module, or wiring a port — maps every feature to the exact modules and files to touch, plus the invariants that break silently (file format, ordinal, gestures priority).
---

# InfCanvas: работа с модулями и фичами

Сначала прочитай `.ai/AGENTS.md` и `.ai/docs/PROJECT_MAP.md` в корне проекта. Этот скилл — процедура
«куда что трогать», чтобы не изобретать маршрут и не ломать невидимые инварианты.

## 0. Сначала определи слой

| Что меняешь | Модуль | Проверка |
|---|---|---|
| Правило/модель/геометрия | `:core:domain` | тест в `core/domain/src/test`; `android.*`/`androidx.*` (кроме `Uri`/`File` в `port`) запрещены |
| Хранение/БД/файл/превью | `:core:data` | тест в `core/data/src/test` (Robolectric) |
| Состояние экрана/жесты/UI | `:app` | unit-тест в `app/src/test`, для жестов — ещё и androidTest |
| Сборка реализаций | `:app/di/AppContainer.kt` | — |

Если фича должна получить данные во ViewModel, а он знает только `BoardRepository`,
**не тащи туда `core:data`** — добавь метод/порт в `:core:domain`.

## 1. Новая кисть (`BrushType`)

Порядок критичен: `BrushType.ordinal` **пишется в файл доски** (см. `BoardFileStorage`).

1. `core/domain/.../model/Brush.kt` — новый тип **только в конец enum**; ветка в `Brush.calculateWidth`.
2. `core/data/.../storage/BoardFileStorage.kt` — если формат меняется: `FORMAT_VERSION++`,
   ветка чтения старых `ordinal → type` (если совместимость нужна) или явный сброс
   совместимости в KDoc; обнови фикстуры `BoardFileStorageTest`.
3. `core/domain/.../model/Stroke.kt` — множитель `visualWidth` в `Stroke.create` (padding `bounds`).
4. `core/domain/.../spatial/Quadtree.kt` — `isStrokeHitByCircle` (полуширина для ластика).
5. `app/.../canvas/StrokeGeometryCache.kt` — `buildGeometry` и все места с шириной
   (ширина берётся только через `Brush.calculateWidth`, не дублируй формулу).
6. `app/.../canvas/renderer/CanvasRenderer.kt` — как рисовать (Stroke/Fill/свои слои).
7. `app/.../screen/board/viewControllers/BoardToolController.kt` — `selectBrushType` (opacity по умолчанию).
8. `app/.../screen/board/huds/BoardTopBar.kt` — чип в `BrushSelector`.
9. `app/src/main/res/values/strings.xml` — подпись `brush_*`.
10. `core/data/.../storage/ThumbnailGenerator.kt` — как кисть выглядит на превью (если отличается).

**Тест:** `BrushCalculateWidthTest` / `BuildGeometryTest` (+ новый тип), тест round-trip в
`BoardFileStorageTest` на новый `ordinal`.

## 2. Новый инструмент (`CanvasTool`)

1. `core/domain/model/CanvasTool.kt` — значение enum.
2. `app/.../canvas/CanvasGestureHandler.kt` — место в приоритетах (`detectCanvasGestures`)
   **и** отдельный `handleXxx` по образцу `handleLassoGesture`.
3. `app/.../canvas/gestures/CanvasGestures.kt` — низкоуровневые хелперы (накопление точек, hit-test).
4. `app/.../screen/board/viewControllers/BoardToolController.kt` — `selectXxx()` + `onClearSelectionRequested`.
5. HUD: `huds/BoardTopBar.kt` или `huds/BoardBottomHud.kt`.
6. Отрисовка: новый файл в `canvas/renderer/` + вызов в `InfiniteCanvasView` **в правильном слое**.
7. Буферы состояния кадра — в `renderer/CanvasRenderState.kt` (иначе аллокации в draw).

**Не забыть:** инструмент не должен перехватывать жесты, когда активен другой; жест читает
состояние через лямбды-провайдеры (`activeTool()`, `viewportState()`), а не из композиции.

## 3. Новый тип объекта (фигуры, текст, стикеры) — самое дорогое

1. **Модель** — `core/domain/model/` + поле в `BoardDocument`/`BoardModels`.
2. **Формат** — `core/data/.../BoardFileStorage.kt`: новые поля в конец, `FORMAT_VERSION++`,
   `writeInt(count)` перед списком (как у `strokes`/`images`), чтение старых версий с дефолтом.
3. **Репозиторий** — `core/domain/repository/BoardRepository.kt` + `core/data/repository/BoardRepositoryImpl.kt`.
4. **Состояние** — новый `XxxController` в `app/.../viewControllers/` по образцу
   `BoardImageController` (StateFlow + мутации + `onContentChanged()`); зарегистрировать
   в `BoardViewModel` и проксировать.
5. **Отрисовка** — `canvas/renderer/`; порядок слоёв в `InfiniteCanvasView`.
6. **Хит-тест/жесты** — `gestures/ImageGestureHandler.kt` как образец (углы, тело, приоритет).
7. **Выделение** — взаимоисключение через `onSelectionActivated`.
8. **Undo** — сейчас только штрихи.
9. **Превью** — `core/data/.../ThumbnailGenerator.kt`.
10. **Размер файла** — полный payload переписывается каждые 1.5 с; тяжёлые объекты — отдельный файл/журнал.

## 4. Изменение формата (`BoardFileStorage`)

- Версия только вверх; чтение `1..FORMAT_VERSION`, запись — только текущую.
- Никаких `ordinal` без таблицы `ordinal → type` по версиям.
- Замена файла: `tmp → fsync → ATOMIC_MOVE` (сейчас `delete()` + `renameTo()` без проверки).
- Ошибка чтения **не должна** превращаться в пустой холст молча (автосейв затрёт доску).
- Обязательный тест: «сохранить на старой версии → прочитать новой» для каждой версии.

## 5. Сборка/границы

- Добавляя модуль: `settings.gradle.kts` → `build.gradle.kts` (плагины `apply false`) →
  namespace/compileSdk/minSdk → конфигурация тестов (`isIncludeAndroidResources`, при Compose —
  `composeCompiler` из `app/build.gradle.kts`) → добавить его в `scripts/check-module-boundaries.ps1`.
- Зависимость на другой модуль — `implementation`; `api` только если тип модуля есть
  в публичной сигнатуре (иначе нарушение).
- После изменений: `pwsh scripts/check-module-boundaries.ps1`; если Gradle доступен —
  `.\gradlew.bat testDebugUnitTest :app:lintDebug`.

## 6. Чек-лист перед завершением

- [ ] Новый код лежит в правильном модуле, границы не нарушены.
- [ ] Тест в модуле изменённого кода и он падал до фикса.
- [ ] Формат/ordinal не сломаны (или осознанно сброшена совместимость в KDoc + PR).
- [ ] Новые строки — в `strings.xml`, константы — в одном месте.
- [ ] Поведение жестов не конфликтует (лассо/ластик/фото).
- [ ] `.ai/docs/PROJECT_MAP.md`/`.ai/docs/TESTING.md` обновлены, если поменялась структура.

# Расширение InfCanvas: что менять

Практическая карта точек расширения. Отвечает на вопрос «хочу добавить X — какие классы трогать и что не забыть», с указанием инвариантов, которые легко сломать.

**Корень:** `app/src/main/java/com/infinitecanvas/board/`
**Стек:** Kotlin 2.4.21 · Compose · Room 2.8.5 · бинарный формат доски v4

---

## 0. Три правила, без которых всё развалится

1. **Слои.** `domain` — модели и правила (без Android), `data` — хранение, `presentation` — Compose и состояние. Новая фича идёт сверху вниз: модель → хранение → состояние → UI. UI не хранит истину: всё, что переживает рекомпозицию, живёт во ViewModel/контроллере.
2. **Формат файла.** В `board_<id>.bin` пишутся числа, а не имена. Любое изменение набора полей или enum'а — это `FORMAT_VERSION++` и ветка чтения для старых версий. Порядковые номера (`ordinal`) в файл писать нельзя (это уже один раз сломалось: см. §1).
3. **Один источник для геометрии.** Список штрихов в `BoardStrokeController` и `Quadtree` меняются **вместе и в одном потоке (Main)**. Забыл quadtree — штрих не рисуется или не стирается; забыл список — исчезнет после undo.

---

## 1. Новая кисть

| Шаг | Файл | Что делать |
|---|---|---|
| 1 | `../core/domain` | Добавить тип **в конец** enum (без перестановок) |
| 2 | `../core/data` | `FORMAT_VERSION++`, ветка чтения `ordinal → type` для старых версий, запись/чтение нового типа |
| 3 | `../core/domain` | `Stroke.create`: визуальная ширина → padding `bounds` |
| 4 | `../core/domain` | `isStrokeHitByCircle`: полуширина для ластика |
| 5 | `presentation/canvas/StrokeGeometryCache.kt` | `buildGeometry` (ветка), `calculateWidthForBrush` (множители) |
| 6 | `presentation/canvas/renderer/CanvasRenderer.kt` | `drawCachedGeometry`: как рисовать (Stroke / Fill / свои слои) |
| 7 | `presentation/screen/board/viewConrollers/BoardToolController.kt` | `selectBrushType`: opacity по умолчанию |
| 8 | `presentation/screen/board/huds/BoardTopBar.kt` | Чип в `BrushSelector` |
| 9 | `app/src/main/res/values/strings.xml` | Подпись `brush_*` |
| 10 | `../core/data` | Как кисть выглядит на превью (если отличается) |

**Не забыть:**
- Дорожка `Stroke.create → bounds → кэш геометрии`: кэш валидируется по `bounds`, поэтому новый тип влияет на отступы иначе, чем старый, — старые доски перечитаются с новым паддингом (это нормально, но визуально сдвинет границы выделения).
- Константы ширины сейчас продублированы в `Stroke.kt`, `Quadtree.kt`, `StrokeGeometryCache.kt` — при добавлении вынеси их в одно место.
- `PENCIL` уже добавлен, но выключен из UI (`BoardTopBar.kt` — чип закомментирован), при этом обрабатывается в 5 местах. Реши: включать или удалять.

**Проверка:** нарисовать → сохранить → перезапустить приложение → открыть доску (проверяет формат) → ластик → лассо → undo → превью на Home.

---

## 2. Новый инструмент (`CanvasTool`)

1. `../core/domain` — добавить значение.
2. `presentation/canvas/CanvasGestureHandler.kt` — `detectCanvasGestures`: **место в приоритетах** (выше рисования, но ниже выделения/фото, если новый инструмент не должен их перебивать) + отдельный `handleXxx` по образцу `handleLassoGesture`.
3. `presentation/canvas/gestures/CanvasGestures.kt` — низкоуровневые хелперы (накопление точек, hit-test).
4. `presentation/screen/board/viewConrollers/BoardToolController.kt` — `selectXxx()` (и снятие выделения через `onClearSelectionRequested`).
5. HUD: `huds/BoardTopBar.kt` или `huds/BoardBottomHud.kt` — чип/кнопка.
6. Отрисовка: новый файл в `presentation/canvas/renderer/` + вызов в `InfiniteCanvasView` **в правильном слое** (что под штрихами, что поверх).
7. Буферы состояния кадра — в `renderer/CanvasRenderState.kt` (иначе аллокации в draw).

**Не забыть:** инструмент не должен перехватывать жесты, когда активен другой; жест нельзя начинать с чтения `State` из композиции — только через лямбды-провайдеры (`activeTool()`, `viewportState()`), как в существующих handler'ах.

---

## 3. Новый тип объекта на доске (фигуры, текст, стикеры)

Самое дорогое расширение: объект должен переживать сохранение, попадать в превью, выделяться, стираться и отменяться. Полный маршрут:

1. **Модель** — `../core/domain`: новый data-класс + поле в `BoardDocument`/`BoardModels`.
2. **Формат** — `../core/data`: новые поля в конец (порядок критичен), `FORMAT_VERSION++`, чтение старых версий с дефолтом. Для списка объектов — `writeInt(count)` перед циклом, как у `strokes`/`images`.
3. **Репозиторий** — `../core/data` + интерфейс `../core/domain`: `loadBoard`/`saveBoardContent`.
4. **Состояние** — новый `XxxController` в `presentation/screen/board/viewConrollers/` по образцу `BoardImageController` (StateFlow + мутации + `onContentChanged()`), зарегистрировать в `BoardViewModel` и проксировать методы.
5. **Отрисовка** — `presentation/canvas/renderer/`: отдельный слой; порядок — в `InfiniteCanvasView` (под штрихами / над штрихами / поверх мира).
6. **Хит-тест и жесты** — `presentation/canvas/gestures/ImageGestureHandler.kt` как образец (углы, тело, приоритет в `detectCanvasGestures`).
7. **Выделение** — взаимоисключение через `onSelectionActivated` (сейчас: штрихи ↔ картинки).
8. **Undo** — сейчас стек только для штрихов (см. §5).
9. **Превью** — `../core/data`.
10. **Массштаб** — размер файла: полнохолстовый payload переписывается каждые 1.5 с; для тяжёлых объектов продумай отдельный файл или журнал.

**Не забыть:** абсолютные пути не хранить в модели (только имя файла); при удалении объекта удаление файла-ассета должно идти **после** сохранения ссылки, иначе останется битая ссылка.

---

## 4. Изменение формата хранения

- Версия — только вверх; чтение поддерживает `1..FORMAT_VERSION`, запись — только текущую.
- Стабильные коды: `BrushType` не должен попадать в файл как `ordinal`. Если код уже записан так, при смене — таблица `ordinal → type` по версиям.
- Замена файла: `tmp → fsync → ATOMIC_MOVE` (сейчас `delete()` + `renameTo()` без проверки — окно потери).
- Валидация: `strokeCount`/`pointCount`/длина файла проверяются, иначе `ArrayList(capacity)` от мусора даёт OOM.
- Ошибка чтения не должна превращаться в пустой холст: пустой `payload` → следующий автосейв затрёт доску.
- Тест: сохранить на старой версии → прочитать новой (round-trip на каждую версию).

**Файлы:** `BoardFileStorage.kt` (формат), `BoardRepositoryImpl.kt` (порядок «файл → БД»), `BoardViewModel.kt` (кто и когда пишет).

---

## 5. Undo/redo

Сейчас: `BoardStrokeController.undoStack` — до 40 снапшотов `PersistentList<Stroke>`; `undo()` пересобирает quadtree целиком и сбрасывает выделение. Картинки в историю **не входят** — удаление фото необратимо.

Как расширять:
- Ввести `sealed interface CanvasEdit { AddStroke, EraseStrokes(ids), MoveStrokes(ids, dx, dy), AddImage, TransformImage, DeleteImage, … }` и применять/откатывать по одному месту.
- Один жест = одна запись: ластик сейчас делает снапшот на каждое событие пера (история выедается за один проход), перетаскивание — уже по завершении жеста.
- При снапшоте всего документа следи за памятью: 40 полных копий при 10k штрихов — это гигабайты; лучше диффы.

**Файлы:** `BoardStrokeController.kt`, `BoardImageController.kt`, `BoardViewModel.kt` (единая точка `undo()`), кнопки — `huds/BoardTopBar.kt` / `huds/BoardBottomHud.kt`.

---

## 6. Сохранение и жизненный цикл

Точки сейчас:
- `BoardViewModel.scheduleAutoSave()` — debounce 1.5 с, перезапускается на каждое изменение (включая пан).
- `flushSaveAndThumbnail()` — принудительно, из `BoardScreen.handleExit` (кнопка/Back).
- `onCleared()` — **не работает**: `viewModelScope` отменяется до вызова `onCleared`, корутина не стартует.
- `ON_STOP` — нет; сворачивание приложения теряет до 1.5 с.

Как правильно расширять:
- Финальное сохранение — в собственном `application-scope` (`SupervisorJob + Dispatchers.IO`), не в `viewModelScope`.
- `LifecycleEventObserver` на `ON_STOP` в `BoardScreen` + флаг «уже сохраняем», чтобы не было двух писателей.
- Запись — под `Mutex` на `boardId`; уникальное имя tmp-файла; проверять результат `rename`.

**Файлы:** `BoardViewModel.kt`, `BoardScreen.kt`, `BoardFileStorage.kt`, `BoardRepositoryImpl.kt`.

---

## 7. Home: папки, сортировка, превью

- Сортировка — `../core/data` (`ORDER BY`), затем поле в `HomeUiState` + меню в `HomeScreen`.
- Новый фильтр — `HomeUiState.FolderFilter` (sealed) + ветка в `HomeViewModel.boardsFlow`.
- Поиск — `HomeViewModel` (сейчас подстрока без нормализации регистра).
- Превью — `BoardPreviewCard.kt`: ключ Coil обязан включать `updatedAt`, иначе картинка не обновится.
- Диалоги — `components/HomeDialogs.kt`; состояние диалогов держится в `HomeScreen`, а не в ViewModel (это осознанно).

---

## 8. Тема, цвета, локализация

- Тема — `presentation/theme/ui/Theme.kt` (`InfCanvasTheme`), палитра — `Color.kt`, тексты — `Type.kt`.
- Все новые строки — сразу в `res/values/strings.xml`; сейчас часть подписей захардкожена в HUD, не повторяй.
- `app_name` в `strings.xml` сейчас — «Porno» (видно в лаунчере); при первом же релизе переименовать.

---

## 9. Производительность: правила, которые нельзя нарушать

1. **Никаких аллокаций в draw-фазе:** `Path`, `Rect`, `Offset`, списки, строки, `Stroke`, `Paint`. Переиспользуемые объекты — в `CanvasRenderState`, стили — в кэшах рендерера.
2. **`saveLayer` дорог** — не вызывай его на каждый объект (сейчас так у выделителя: отдельный проход, но слой на штрих).
3. **Кэш геометрии** инвалидируется по `bounds`: меняешь точки — создавай новый `Stroke` (`translated`/`copy`), не мутируй список.
4. **Quadtree** — мутируется там же, где меняется список штрихов, в Main; `query` — только на видимую область (+ запас на драг).
5. **Тяжёлое** (декод, файлы, миниатюры) — вне Main: `Dispatchers.IO`/`Default`.
6. **Новый слой** — с отсечением по `visibleWorldBounds`, иначе zoom-out убьёт кадр.
7. **`collectAsState` в корне холста** = рекомпозиция на каждое событие пана; для новых параметров, меняющихся в жесте, передавай `State`/лямбду и читай в draw.

---

## 10. Чек-лист перед мержем фичи

- [ ] Переживает перезапуск приложения (формат, миграция).
- [ ] Работает undo/redo (или осознанно не входит в историю).
- [ ] Не конфликтует с лассо, ластиком и жестами фото.
- [ ] Обновляется превью на Home.
- [ ] Поворот экрана / сворачивание не теряют данные.
- [ ] 2000+ штрихов: нет просадок при рисовании, зуме 0.15×, панорамировании.
- [ ] Новые строки — в ресурсах; новые константы — в одном месте.
- [ ] Тест на сериализацию (round-trip) и на hit-test, если добавлена геометрия.

---

## 11. Карта «фича → файлы»

| Что добавляешь | domain | data | состояние | UI | отрисовка |
|---|---|---|---|---|---|
| Кисть | `Brush.kt`, `Stroke.kt` | `BoardFileStorage` | `BoardToolController` | `BoardTopBar`, strings | `StrokeGeometryCache`, `CanvasRenderer` |
| Инструмент | `CanvasTool.kt` | — | `BoardToolController` | HUD | `CanvasGestureHandler`, `gestures/*`, `renderer/*` |
| Фигуры/текст | новая модель | `BoardFileStorage` (v5) | новый `*Controller` + `BoardViewModel` | меню вставки | новый renderer + порядок в `InfiniteCanvasView` |
| Слои / z-order | `zIndex` в моделях | формат | контроллеры | панель слоёв | `InfiniteCanvasView`, `StrokeLayerRenderer` |
| Экспорт PNG/PDF | — | — | `BoardViewModel` | меню | `ThumbnailGenerator` (как основа offscreen-рендера) |
| Синхронизация | `BoardRepository` | новый remote-источник | ViewModel | индикаторы | — |
| Настройки | — | DataStore | новый ViewModel | экран | — |
| Поиск по доскам | — | `BoardDao` (FTS) | `HomeViewModel` | `HomeScreen` | — |
| Локализация | — | — | — | `strings.xml` (+ `values-en`) | — |

---

## 12. Анти-паттерны проекта (не повторять)

| Антипаттерн | Где сейчас | Чем плохо |
|---|---|---|
| `enum.ordinal` в файле | `BrushType` → `BoardFileStorage` | добавление типа в середину портит старые доски |
| Пустой payload при ошибке чтения | `readBoardPayload` | автосейв затирает доску |
| `delete()` + `renameTo()` без проверки | файловые хранилища | окно потери файла |
| Сохранение в `onCleared` | `BoardViewModel` | scope уже отменён, сохранения нет |
| Ссылка на файл удаляется раньше сохранения | `BoardImageController.deleteSelectedImage` | битая ссылка при краше |
| Дублирование констант ширины | `Stroke`, `Quadtree`, `StrokeGeometryCache` | рассинхрон границ/ластика |
| Абсолютный путь в модели/БД | `BoardSummary.thumbnailPath` | ломается при переносе/бэкапе |
| Строки в коде | `BoardBottomHud`, `BoardTopBar` | нет локализации |
| Рекомпозиция корня на каждое событие | `CanvasLayer` → `InfiniteCanvasView` | лишние 1.5–4 мс на кадр |

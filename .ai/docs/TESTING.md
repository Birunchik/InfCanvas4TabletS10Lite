# TESTING.md — как тестируется InfCanvas

## Стек

- JUnit 4.13, `kotlinx-coroutines-test` (`runTest`, `advanceTimeBy`/`advanceUntilIdle`),
  MockK (только для портов/фреймворковых классов), Truth (в `core:*`),
  Robolectric 4.18 (`@Config(sdk = [31])` в `core:data`, `[34]` в `core:canvas`),
  Compose UI Test (androidTest у `:core:canvas`, `:feature:home`, `:app`).
- Покрытие: Kover подключён к `:app`, `:core:data`, `:core:canvas`, `:feature:board`,
  `:feature:home` (в `:core:domain` — нет).
- Robolectric требует `testOptions.unitTests.isIncludeAndroidResources = true`
  (включено convention-плагином для всех библиотек).

## Где лежат тесты

| Модуль | Путь | Тип | Зависимости |
|---|---|---|---|
| `:core:domain` | `core/domain/src/test/java/.../domain/**` | JVM, без Robolectric | JUnit + Truth |
| `:core:data` | `core/data/src/test/java/.../data/**` | JVM + **Robolectric** | `bundles.unitTest` |
| `:core:canvas` | `core/canvas/src/test/java/.../canvas/**` | JVM + **Robolectric** | `bundles.unitTest` + `:core:testing` |
| `:core:canvas` | `core/canvas/src/androidTest/java/.../canvas/**` | устройство/эмулятор | Compose UI Test + `:core:testing` |
| `:feature:board` | `feature/board/src/test/java/.../board/**` | JVM | `bundles.unitTest` + `:core:testing` |
| `:feature:home` | `feature/home/src/test/java/.../home/**` | JVM | `bundles.unitTest` + `:core:testing` |
| `:feature:home` | `feature/home/src/androidTest/java/.../home/**` | устройство/эмулятор | Compose UI Test + `:core:testing` |
| `:app` | `app/src/test`, `app/src/androidTest` | smoke | `bundles.unitTest` / Compose UI Test |

Фикстуры: `core/testing/src/main/java/.../testing/` (`FakeBoardRepository`, `FakeImageStore`,
`FakeBoardThumbnailer`, `MainDispatcherRule`, `FakeClock`, `*Fixtures`);
`core/data/src/test/.../data/testing/TestData.kt` — доменные объекты для data-тестов.

## Команды

```bat
:: все unit-тесты (все модули, устройство не нужно)
.\gradlew.bat testDebugUnitTest

:: конкретный модуль
.\gradlew.bat :core:domain:testDebugUnitTest
.\gradlew.bat :core:data:testDebugUnitTest
.\gradlew.bat :core:canvas:testDebugUnitTest
.\gradlew.bat :feature:board:testDebugUnitTest
.\gradlew.bat :feature:home:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest

:: конкретный класс/метод
.\gradlew.bat :core:data:testDebugUnitTest --tests "*BoardFileStorageTest"
.\gradlew.bat :feature:board:testDebugUnitTest --tests "*BoardViewModelTest"

:: instrumented (нужен девайс/эмулятор; adb devices должен видеть устройство)
.\gradlew.bat connectedDebugAndroidTest
:: только один модуль
.\gradlew.bat :core:canvas:connectedDebugAndroidTest

:: отчёты
:: app:      app/build/reports/tests/testDebugUnitTest/index.html
:: core:*:   core/<module>/build/reports/tests/testDebugUnitTest/index.html
:: feature:*:feature/<module>/build/reports/tests/testDebugUnitTest/index.html
:: androidTest: <module>/build/reports/androidTests/connected/debug/index.html

:: покрытие (там, где подключён Kover)
.\gradlew.bat :app:koverHtmlReportDebug
.\gradlew.bat :core:canvas:koverHtmlReportDebug
```

Перед прогоном убедись, что Gradle может писать в `~/.gradle/wrapper/dists`
(иначе `FileNotFoundException ... .lck` — ограничение песочницы, а не проекта).

## Что чем тестируется (ориентир)

- **Формат файла** → `core/data` `BoardFileStorageTest`: round-trip, битый magic,
  обрезанный файл, мусорные счётчики (нет OOM). При изменении формата/`BrushType`
  обновляй тест в том же коммите.
- **Репозиторий** → `BoardRepositoryImplTest`: in-memory Room + реальный `BoardFileStorage`,
  `ImageStore` через MockK. Проверяет порядок «файл → Room», `thumbnailPath = null` (COALESCE),
  каскад `deleteFolder`.
- **DAO/маппинг** → `BoardDaoTest`, `EntityMappingTest` (in-memory Room).
- **Импорт картинок** → `ImageAssetStorageTest` (Robolectric, реальные файлы).
- **Превью** → `ThumbnailGeneratorTest` (`@GraphicsMode(NATIVE)`, проверка webp-заголовка и размеров).
- **Домен (чистая логика)** → `StrokeTest`, `WorldBoundsTest`, `ViewportStateTest`,
  `QuadtreeTest`, `LassoSelectorTest`, `BrushCalculateWidthTest`. **Новые правила геометрии —
  сюда, без моков.**
- **Состояние экрана** → `feature:board` `BoardViewModelTest` (дебаунс через виртуальное время,
  двойной flush, NonCancellable), `feature:home` `HomeViewModelTest`.
- **Жесты/холст** → `core:canvas` unit (`src/test/.../canvas/**`) + instrumented
  (`CanvasGestureIntegrationTest`, `InfiniteCanvasViewTest`).
- **Home UI** → `feature:home` instrumented `HomeScreenTest` (пустые состояния).

## Правила для новых тестов

1. Фейки — для портов; Room и файловый формат — только реальные (in-memory / tmp-каталог).
2. Не мокай `ViewportState`/`Stroke` — это чистые value-объекты, моки прячут поломку формул.
3. Тест должен падать, если сломать поведение (сначала убедись, что он красный).
4. Нет `Thread.sleep`; время — через `TestDispatcher`/`FakeClock`.
5. Тесты, требующие `Uri`/`Bitmap`/`Context`, — только в `:core:data` и `:core:canvas` (Robolectric).
6. Общие фикстуры — в `:core:testing`, не дублируй их в `test` и `androidTest`.

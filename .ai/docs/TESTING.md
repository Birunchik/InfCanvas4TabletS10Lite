# TESTING.md — как тестируется InfCanvas

## Стек

- JUnit 4.13, `kotlinx-coroutines-test` (`runTest`, `advanceTimeBy`/`advanceUntilIdle`),
  MockK (только для портов/фреймворковых классов), Truth (в `core:*`),
  Robolectric 4.18 (`@Config(sdk = [31])`) для тестов, которым нужны `Context`/`Bitmap`,
  Compose UI Test (только `:app` androidTest).
- Покрытие: Kover подключён к `:app` и `:core:data` (в `:core:domain` — нет).
- Robolectric требует `testOptions.unitTests.isIncludeAndroidResources = true`
  (включено в `:app` и `:core:data`).

## Где лежат тесты

| Модуль | Путь | Тип | Зависимости |
|---|---|---|---|
| `:core:domain` | `core/domain/src/test/java/.../domain/**` | JVM, без Robolectric | только JUnit |
| `:core:data` | `core/data/src/test/java/.../data/**` | JVM + **Robolectric** | `bundles.unitTest` |
| `:app` | `app/src/test/java/...` | JVM, часть на Robolectric | `bundles.unitTest` |
| `:app` | `app/src/androidTest/java/...` | устройство/эмулятор | `bundles.androidTest` + Compose UI Test |

Фикстуры: `core/data/src/test/.../data/testing/TestData.kt` (доменные объекты для data-тестов),
`app/src/test/.../testing/` (`FakeBoardRepository`, `FakeImageStore`, `FakeBoardThumbnailer`,
`MainDispatcherRule`, `FakeClock`, `*Fixtures`).

## Команды

```bat
:: все unit-тесты (все модули, устройство не нужно)
.\gradlew.bat testDebugUnitTest

:: конкретный модуль
.\gradlew.bat :core:domain:testDebugUnitTest
.\gradlew.bat :core:data:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest

:: конкретный класс/метод
.\gradlew.bat :core:data:testDebugUnitTest --tests "*BoardFileStorageTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*BoardViewModelTest"

:: instrumented (нужен девайс/эмулятор)
.\gradlew.bat :app:connectedDebugAndroidTest

:: отчёты
:: app:      app/build/reports/tests/testDebugUnitTest/index.html
:: core:*:   core/<module>/build/reports/tests/testDebugUnitTest/index.html
:: androidTest: app/build/reports/androidTests/connected/debug/index.html

:: покрытие (там, где подключён Kover)
.\gradlew.bat :app:koverHtmlReportDebug
.\gradlew.bat :core:data:koverHtmlReportDebug
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
- **Состояние экрана** → `BoardViewModelTest` (дебаунс через виртуальное время,
  двойной flush, NonCancellable), `HomeViewModelTest`.
- **Жесты/холст** → unit (`app/src/test/.../canvas/**`) + instrumented
  (`CanvasGestureIntegrationTest`, `InfiniteCanvasViewTest`).

## Правила для новых тестов

1. Фейки — для портов; Room и файловый формат — только реальные (in-memory / tmp-каталог).
2. Не мокай `ViewportState`/`Stroke` — это чистые value-объекты, моки прячут поломку формул.
3. Тест должен падать, если сломать поведение (сначала убедись, что он красный).
4. Нет `Thread.sleep`; время — через `TestDispatcher`/`FakeClock`.
5. Тесты, требующие `Uri`/`Bitmap`/`Context`, — только в `:core:data` и `:app` (Robolectric).

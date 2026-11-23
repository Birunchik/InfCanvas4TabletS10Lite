**Цель:** разбить единственный Gradle-модуль на модули с проверяемыми границами, не меняя поведение приложения и не теряя покрытие тестами.

**Принцип:** сначала подготовка (тесты, DI-шов, схема Room), потом извлечение модулей по одному, от самого «чистого» к самому связанному. Каждая фаза — отдельный зелёный PR, приложение собирается и работает после каждой.

**Текущие факты (замеры):**

| Часть | Файлов | Строк | Android/Compose/Room |
|---|---:|---:|---|
| `domain` | 11 | 622 | нет (0 импортов android/androidx) |
| `data` | 8 | 1 016 | Room, Bitmap, Exif, Context |
| `presentation/canvas` | 22 | 2 440 | Compose, Bitmap |
| `presentation/screen/board` | 13 | 1 920 | Compose, Uri, File |
| `presentation/screen/home` | 16 | 1 464 | Compose, Coil |
| `presentation/theme` | 3 | 98 | Compose |

Связи, которые придётся разрезать (все — `presentation → data`, обратных нет):

- `ImageAssetStorage`: `BoardImageCache`, `AppNavHost`, `BoardScreen`, `BoardViewModel`, `BoardViewModelFactory`, `CanvasLayer`, `BoardImageController` (7 файлов);
- `ThumbnailGenerator`: `AppNavHost`, `BoardViewModel`, `BoardViewModelFactory`;
- `BoardRepositoryImpl`/Room в presentation не протекают — это уже хорошо.

---

## Целевая структура

```
InfCanvas/
├── app/                        # только точка входа: MainActivity, AppContainer, AppNavHost, App
├── core/
│   ├── domain/                 # Android-library БЕЗ Compose/Room: модели, spatial, контракты + порты
│   ├── data/                   # Room, файловые хранилища, ThumbnailGenerator, реализация репозитория
│   ├── canvas/                 # InfiniteCanvasView, gestures/, renderer/, кэши
│   └── designsystem/           # тема, Color, Type, общие строки/иконки
├── feature/
│   ├── home/                   # HomeScreen/VM, карточки, диалоги, sidebar
│   └── board/                  # BoardScreen/VM, контроллеры, HUD, палитра
└── build-logic/                # convention plugins (android-library, compose, feature)
```

### Граф зависимостей (стрелка = `implementation`)

```
app ──> feature:home ──> core:designsystem
 │          └─────────> core:domain
 ├──> feature:board ──> core:canvas ──> core:domain
 │          ├─────────> core:designsystem
 │          └─────────> core:domain
 ├──> core:data ──────> core:domain
 └──> core:designsystem
```

Жёсткие инварианты (проверяются в CI, см. фазу 5):

1. `core:domain` не зависит ни от чего, кроме `kotlinx-coroutines-core`.
2. `core:data` не зависит от `presentation`/`feature`/`core:canvas`; `core:canvas` не зависит от `core:data`.
3. `feature:board` и `feature:home` **не** зависят от `core:data` — только от портов из `core:domain`.
4. `app` — единственное место, где создаются реализации (composition root).

> **Про `core:domain`:** рекомендую Android-library (а не `kotlin("jvm")`), потому что порты картинок используют `Uri`/`File`. Запрет на Compose/Room сохраняется и проверяется автоматически. Если хочется 100% JVM-модуль — сделайте порты без `Uri` (`ImageSource`), это +1 день и лишний слой.

---

## Фаза 0. Подготовка (0,5–1 день)

Без неё двигать код опасно: unit-тесты сейчас **не компилируются**.

1. **Зафиксировать рабочее дерево.** Сейчас незакоммичены `Brush.kt`, `ThumbnailGenerator.kt`, `StrokeGeometryCache.kt`: рефакторинг `calculateWidthForBrush → Brush.calculateWidth` уже сделал `CalculateWidthForBrushTest.kt` невалидным. Варианты:
   - обновить тест на `Brush.calculateWidth` (предпочтительно) и закоммитить;
   - либо вернуть тонкий wrapper `fun calculateWidthForBrush(...) = brush.calculateWidth(...)` на один коммит.
   Критерий: `.\gradlew.bat :app:testDebugUnitTest` зелёный.
2. **Проверить, что в тестах нет «мёртвых» отчётов.** В `app/build/test-results` лежат `BoardFileStorageTest` (7) и `BoardViewModelTest` (2), которых нет в дереве. Решить судьбу: восстановить (рекомендую, это самые рискованные зоны) или осознанно удалить.
3. **DI-шов.** Вынести создание `AppDatabase`, `BoardFileStorage`, `ImageAssetStorage`, `ThumbnailGenerator`, `BoardRepositoryImpl`, Coil `ImageLoader` из `MainActivity` в `di/AppContainer.kt` (обычный класс с ленивыми полями). Никакого Hilt — достаточно ручного контейнера. Это ключевой enabler: модули будут отдавать фабрики, а `app` — связывать.
4. **Room schema export.** `@Database(exportSchema = false)` → `true`, добавить ksp-аргумент `room.schemaLocation` и закоммитить `core/data/schemas/.../1.json`. Без этого миграции при выносе модуля станут необратимыми.
5. **Baseline.** Записать: clean build, incremental build, время `testDebugUnitTest`, размер APK. Подключить `kover`/`jacoco` (сейчас нет) — чтобы доказать, что при переносе кода покрытие не упало.
6. **CI.** GitHub Actions (или локальный скрипт): `gradlew :app:testDebugUnitTest :app:lintDebug`. Instrumented — ночным прогоном на эмуляторе.

**Выход:** зелёные тесты, контейнер зависимостей, схема Room в git, метрики «до».
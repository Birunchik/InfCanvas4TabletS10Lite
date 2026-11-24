# InfCanvas: план выхода из монолитного `:app`

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

## Фаза 3. Порты и DI: убрать `presentation → data` (2 дня, риск средний)

Это архитектурное ядро плана; делать **до** выноса фич, пока всё в одном модуле и легко проверять.

1. `BoardViewModel`, `BoardViewModelFactory`, `BoardScreen`, `CanvasLayer`, `BoardImageController` принимают `ImageStore`/`BoardThumbnailer` вместо `ImageAssetStorage`/`ThumbnailGenerator`. `BoardImageCache` — `ImageStore` (или только `fileFor`-порт `ImageFiles`).
2. `AppNavHost` больше не принимает storage: получает `AppContainer` (или готовые фабрики VM).
3. `BoardViewModel` заодно становится тестируемым: добавить `CoroutineDispatcher`/`Thumbnailer` в конструктор, написать `BoardViewModelTest` на дебаунс 1.5 с, двойной `flush`, `openBoard`-guard, `NonCancellable`.
4. Убрать `@VisibleForTesting`-костыли, где они были нужны из-за конкретных классов.

**Критерий:** `grep -r "data.local.storage" app/src/main/java/com/infinitecanvas/board/presentation` пуст; тест `BoardViewModel` зелёный; поведение не изменилось (ручной смоук: рисование → сохранение → перезапуск → превью).
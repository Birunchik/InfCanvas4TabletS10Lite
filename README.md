# InfCanvas

Бесконечный холст для рисования пером: штрихи (5 кистей, давление/скорость), вставка
фотографий, закреплённые поверх экрана снимки, папки и доски, бинарный формат хранения,
превью на главном экране.

## Структура

| Модуль | Ответственность |
|---|---|
| `:app` | Compose UI (холст, Home, HUD), `BoardViewModel`/`HomeViewModel`, контроллеры состояния, навигация, `di/AppContainer`, ресурсы, instrumented-тесты |
| `:core:domain` | Модели (`Stroke`, `Brush`, `ViewportState`, ...), геометрия (`Quadtree`, `LassoSelector`), контракт `BoardRepository` и порты `ImageStore`/`BoardThumbnailer`. Без Compose/Room. |
| `:core:data` | Room (метаданные), бинарные файлы досок и превью, импорт изображений (EXIF, downsample), реализация `BoardRepositoryImpl` |

Граф: `:app → :core:data → :core:domain`, `:app → :core:domain`.
Границы проверяются `scripts/check-module-boundaries.ps1`.
Подробнее см. `.ai/docs/PROJECT_MAP.md`, `.ai/AGENTS.md` (правила для агентов),
`.ai/docs/TESTING.md`. Быстрый указатель для агентов — корневой `AGENTS.md`.

## Сборка и тесты

```bat
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest            # все unit-тесты (без устройства)
.\gradlew.bat :app:connectedDebugAndroidTest # нужен эмулятор/устройство
```

Требуется JDK 17+, Android SDK (compileSdk 37), Gradle wrapper 9.6.

## Документация

- `docs/EXTENDING.md` — как добавлять кисти/инструменты/объекты.
- `docs/Modularization.md`, `docs/MODULARIZATION_PLAN.md` — план модуляризации.
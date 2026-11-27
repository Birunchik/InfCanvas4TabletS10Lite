# MCP Kotlin LSP — как подключён и что было починено

Краткая память о связке **DSH → MCP → kotlin-language-server**. Здесь четыре
нетривиальных поломки, каждая из которых давала невнятную ошибку. Если MCP-инструменты
`mcp__kotlin-lsp__*` пропали или отвечают `Internal error` — читай этот файл, а не
начинай диагностику заново.

## Схема

```
DSH (dsh-mcp-manager, строка id: mcp-manager)
  └─ node <vendored>/@axivo/mcp-lsp/dist/index.js     (stdio MCP-сервер, 39 tools)
       └─ cmd.exe /c kotlin-language-server.bat        (JVM, LSP по stdio)
            └─ проект infcanvas → C:\Users\...\InfCanvas
```

| Что | Где |
|---|---|
| Конфиг MCP-сервера | `$DSH_HOME\profiles\desktop\cordis.patch.yml`, строка `id: mcp-manager` |
| Пропатченный mcp-lsp (вендорная копия) | `C:\Users\sobol\.dsh\tools\mcp-lsp-patched\@axivo\mcp-lsp\` |
| Конфиг языковых серверов | `C:\kotlin-language-server\lsp.json` (`LSP_FILE_PATH`) |
| Бинарь LSP | `C:\kotlin-language-server\server\bin\kotlin-language-server.bat` (v1.3.13, Kotlin 2.1.0, нужен Java 17) |

## Четыре починенные поломки

### 1. Неверный `id` строки патча → инструменты не появлялись вообще

В бандле `dsh-web-app` строка называется `id: mcp-manager`. Патч профиля использовал
`id: mcp`, поэтому создавался **второй** менеджер вместо настройки существующего, и
`mcp__kotlin-lsp__*` не регистрировались.

**Правило:** `id` в `cordis.patch.yml` обязан совпадать с `id` строки из бандла
(`dsh-base/cordis.patch.yml`, `dsh-web-app/cordis.patch.yml` внутри `app.asar`).
Сверять — до того, как отлаживать что-то ещё.

### 2. `find.default is not a function` при `start_server`

`@axivo/mcp-lsp@1.0.5` (последняя, не обновлялась с 2025-10) делает
`import find from 'find-process'`, но вызывает `find.default(...)`. `find-process`
в версии 2.1.1 (2026-03) перешёл на ESM-`exports` без `.default`, а диапазон `^2.0.0`
молча подтянул ломающую версию.

Починено в `dist/server/client.js`: выбор формы экспорта на месте вызова.

### 3. `spawn EINVAL` при старте LSP

Node ≥ 20.12 (защита CVE-2024-27980) запрещает спавнить `.bat`/`.cmd` без shell.
Починено в `lsp.json`: `command` = `cmd.exe`, а путь к `.bat` ушёл в `args`
(`["/c", "...kotlin-language-server.bat"]`).

### 4. `Internal error` на всех файловых инструментах (hover, symbols, diagnostics…)

mcp-lsp строит URI склейкой `` `file://${file_path}` `` в 24 местах `dist/server/mcp.js`.
На Windows это даёт `file://C:/...` — два слэша, `C:` разбирается как **authority**
хоста, и LSP отвечает `Internal error` (код -32603) на любой запрос с таким
`textDocument.uri`.

`get_project_files` / `get_project_symbols` при этом работают — они не используют
per-file URI. Это и есть диагностический признак: **индекс есть, а файловые инструменты
падают → дело в URI, а не в проекте и не в индексации.**

Починено: helper `toFileUri()` в `dist/server/mcp.js` (правильный `file:///C:/...`,
терпит и `\`, и `/`). **Пути в аргументах — прямые слэши**:
`C:/Users/sobol/.../CanvasTool.kt`.

## Почему mcp-lsp лежит в `~/.dsh/tools`, а не в npx-кеше

`npx -y @axivo/mcp-lsp` держит копию в `%LOCALAPPDATA%\npm-cache\_npx\...` и
переустанавливает зависимости при обновлении кеша — **все патчи стираются** (проверено:
откат `find-process` на 1.4.11 был смыт). Поэтому конфиг указывает на вендорную копию
с вложенными `node_modules`.

⚠️ Если понадобится обновить mcp-lsp — правки 2 и 4 надо **перенести вручную** в новую
копию (или проверить, починил ли автор: `find.default` и `file://${`).

## Прогрев сервера: `scripts/warm-kotlin-lsp.js`

```
node scripts/warm-kotlin-lsp.js             # прогреть и держать (Ctrl+C — стоп)
node scripts/warm-kotlin-lsp.js --check     # прогреть, проверить и погасить
node scripts/warm-kotlin-lsp.js --project infcanvas --timeout 20
```

Скрипт сам поднимает MCP-сервер, ждёт `ready`, проверяет файловый инструмент
(`get_symbols`) и корректно гасит LSP. Умеет чистить осиротевшие JVM (wmic в
современной Windows удалён — используется `Get-CimInstance`).

### ⚠️ Честное ограничение: прогрев НЕ ускоряет сессию DSH

Проверено экспериментально:

- LSP-процесс живёт, пока жив **его** MCP-родитель (`mcp-lsp`). Отдалить нельзя:
  `detached: true` + `unref()` не помогают, KLS выходит на EOF stdin.
- DSH поднимает **свой** экземпляр `mcp-lsp`, а тот стартует **свой** LSP.

Значит прогретый скриптом LSP **не переиспользуется** DSH. Первый `start_server`
в новой сессии всё равно проиндексирует проект заново.

### Что реально помогает против 9 минут ожидания

| Способ | Эффект |
|---|---|
| Держать `--hold` в отдельном терминале **до и во время** сессии DSH | LSP греется параллельно, но DSH всё равно поднимет свой — **выигрыша нет**, только +1 JVM |
| Вызывать `start_server` **один раз** в начале сессии и не перезапускать | убирает повторные индексации — главный реальный выигрыш |
| Пользоваться `get_project_symbols` / текстовым grep, пока индекс идёт | ответ за миллисекунды без ожидания |
| Посмотреть `get_server_status`: `ready` наступает не сразу | не дёргать инструменты, пока `starting` |

**Вывод:** скрипт полезен как диагностика («поднимается ли вся цепочка») и как
держатель сессии для отладки, но роль «предзапусти и DSH будет быстрее» он не
выполняет — архитектура этого не позволяет. Если нужен настоящий прогрев, единственный
путь — **не перезапускать хост DSH** между задачами и стартовать LSP в начале сессии.

## Рабочий цикл

```
1. get_server_status                       → stopped
2. start_server {language_id: kotlin, project: infcanvas}
3. ждать status: ready  (~5–8 мин на 140 файлов; CPU LSP растёт)
4. файловые инструменты — с прямыми слэшами
```

⚠️ **`start_server` отдаёт `SdkError: Request timed out`, но сервер при этом
стартует.** Это таймаут MCP-клиента DSH, а не сбой: индексация дольше. Вызывай
`start_server` один раз, затем опрашивай `get_server_status` (уходит в `starting` →
`ready`). Повторный `start_server` / `restart_server` создаёт лишние JVM — они
конкурируют за память и могут «уронить» текущий сервер.

## Ограничения самого kotlin-language-server 1.3.13

- **`get_diagnostics` не работает** — отдаёт `Internal error`. KLS не реализует
  LSP 3.17 pull-диагностику (`textDocument/diagnostic`); он только push'ит
  `publishDiagnostics`. Это ограничение версии, а не наша поломка.
  Проверять код — через `get_symbols`, `get_hover`, `get_symbol_definitions`,
  `get_symbol_references`, `get_format`, а ошибки компиляции — через Gradle.
- `hover` часто возвращает `null`/пусто, пока файл не открыт явно (`didOpen`);
  `documentSymbol` при этом отвечает.
- Каталог `build/`, `.gradle/`, `.idea/` исключены в `lsp.json` (`patterns.exclude`).

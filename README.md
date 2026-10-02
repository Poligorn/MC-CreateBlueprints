# Blueprint Forge

Аддон для Create. Чертёж — программа линии, а не сам меч: док держит документ, штамп deployer'а сверяет его, Sequenced Assembly выпускает предмет тира из сырья. Оригинал — капитал, копия — товар. Контент модпака живёт в datapack, без пересборки мода.

Стек: Minecraft 1.21.1, NeoForge, Java 21, Create 6.0.10+. Сборка разработки закреплена на `6.0.10-281` (диапазон в `neoforge.mods.toml` — `[6.0.10,)`). Линейку 6.0.11 не подключать: публичного релиза нет.

Источник правды геймплея — концепт v2. Правила в `.cursor/rules/` повторяют его и фиксируют чтение, по которому написан код.

## Статус

Новый MVP концепта v2. Лаборатория чертежей стоит на валу Create и исследует три оси: ME, Flux, Potency. Док держит один чертёж и берёт простой на смену. Эталонная линия одна: железный слиток становится железным мечом T2, если штамп совпал. TE больше нет. Стол зачарований скованный предмет T2+ не чарует.

Сознательно позже: расшифровка древних, Create: Enchantment Industry, рецепт T1, длинные линии T3/T4, фитинги, пьедесталы, decay и аукцион.

## Сборка и проверка

Нужна Java 21.

| Команда | Что делает |
|---|---|
| `./gradlew build` | Сборка jar и JUnit |
| `./gradlew runGameTestServer` | Гейм-тесты в мире с Create |
| `./gradlew runServer` | Dedicated-сервер в `run/` (`run/eula.txt` с `eula=true`) |
| `./gradlew runClient` | Клиент |

Гейм-тесты лежат в `src/gametest` и в jar не попадают. Тестовый datapack `blueprintforge_test` нарочно битый: в dev-логе четыре ошибки загрузки. В jar игрока их нет, строка загрузки заканчивается `0 errors`.

Серверный конфиг: `config/blueprintforge-server.toml`.

## Правила

| Файл | О чём |
|---|---|
| [00-vision-and-decisions.mdc](.cursor/rules/00-vision-and-decisions.mdc) | Столпы и закрытые решения |
| [01-game-design.mdc](.cursor/rules/01-game-design.mdc) | Классы, тиры, три оси, линия |
| [02-architecture.mdc](.cursor/rules/02-architecture.mdc) | Лаборатория, док, миксины линии |
| [03-datapack-schema.mdc](.cursor/rules/03-datapack-schema.mdc) | JSON |
| [04-roadmap.mdc](.cursor/rules/04-roadmap.mdc) | Граница MVP |
| [05-ui-and-presentation.mdc](.cursor/rules/05-ui-and-presentation.mdc) | Иконка, тултип, языки |
| [06-balance-and-safety.mdc](.cursor/rules/06-balance-and-safety.mdc) | Формулы и векторы |

## Зафиксировано

- Выход линии — ванильный предмет цели с компонентом `forged`, не новый id меча.
- Лаборатория кинетическая: тики datapack, пока вал крутится; стресс только на время операции.
- `scaled` следует правилам `restricted`.
- Эталонная линия одна и повторяет §7.3 (три цикла). Рецепта T1 нет. Оба места концепт описывает дважды — см. дорожную карту.

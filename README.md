# cl0udly для ПК

cl0udly (раньше OpenCode mobile) на компьютере. Тот же клиент OpenCode, что на телефоне, в рамке
Claude desktop: заголовок с переключателем Chat / Code, сайдбар с чатами и проектами, колонка
ответа и поле ввода по замерам (см. [docs/measurements.md](docs/measurements.md)).

С телефона перенесено как есть: облачко-маскот со всеми реакциями, пиксельные облака фоном,
заставка cl0udly, индикатор работы, разбор API OpenCode и событий. Где на телефоне датчики,
здесь мышь: глаза следят за курсором, а если быстро повозить курсором по облачку, у него закружится голова.
На ноутбуке облачко замечает зарядку.

## Установить

Linux x64:

```bash
curl -fsSL https://raw.githubusercontent.com/mistqkw/cl0udly-desktop/main/get.sh | bash
```

Ставит последний релиз в `~/.local/share/cl0udly-app`, команду `cl0udly` и ярлык в меню.
Телефонная версия: [cl0udly-mobile](https://github.com/mistqkw/cl0udly-mobile).

## Как это работает

При первом запуске cl0udly скачивает официальный OpenCode 1.x для Linux в
`~/.local/share/cl0udly/opencode` (контрольная сумма проверяется; дальше он обновляется сам внутри 1.x) и запускает `opencode serve` на
127.0.0.1 с паролем. Обычные чаты живут в `~/.local/share/cl0udly/chats`, для Code выбирается папка
проекта. У этого сервера свои настройки, ключи и история (`~/.local/share/cl0udly/opencode/{config,data}`),
OpenCode 2, если он стоит на ПК, их не делит. Можно подключиться и к чужому `opencode serve` (Настройки → Server).

Когда кончается бесплатный лимит OpenCode Zen (он считается по IP), чат сам переходит на GPT-OSS 20B
через Pollinations без аккаунта, как на телефоне. Текст тогда уходит на сторонний сервис.

## Виды

cl0udly, Warm, Graphite (цвета Claude desktop с этого ПК), Hairline, Notebook, OpenCode · Desktop,
OpenCode · Terminal. Рамка у всех одна, вид меняет цвета, шрифты и сообщения.

## Клавиши

Ctrl+N новый чат, Ctrl+O открыть проект, Ctrl+K поиск, Ctrl+Shift+Space быстрый ввод,
Ctrl+B сайдбар, Ctrl+, настройки, Esc остановить ответ.

## Сборка

```bash
./dev.sh        # запуск на отдельных данных в /tmp/cl0udly-dev
./install.sh    # поставить: команда cl0udly и ярлык
```

Нужен JDK 21 (`JAVA_HOME`, по умолчанию `~/toolchain/jdk`).

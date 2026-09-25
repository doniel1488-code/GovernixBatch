# GovernixBatch

Утилита для выполнения пачки консольных команд одной вставкой. Больше не надо вводить по одной — входишь в режим, пишешь/вставляешь команды в чат, они исполняются от имени консоли.

## Возможности

- **Batch-режим в чате** — всё, что пишешь, идёт в консоль
- **Многострочный ввод** — вставляешь сразу блок команд
- **Файловые скрипты** — `/gbatch run <файл>` из папки `scripts/`
- **Комментарии** — строки с `#` игнорируются
- **Счётчик выполненных** — видно сколько команд прошло
- **Без слеша** — пишешь `lp group mod ...`, а не `/lp group mod ...`
- **Автоочистка** — выход с сервера = выход из режима

## Требования

- Paper 1.21.11 (или 1.21+)
- Java 21+

## Установка

1. Скачай `GovernixBatch-1.0.0.jar` из Releases.
2. Положи в `plugins/`.
3. Перезапусти сервер.
4. Используй `/gbatch`.

## Использование

### Способ 1 — режим чата (для быстрых вставок)

```
/gbatch start
```

Дальше пишешь в чат **без слэша**:

```
lp group mod permission set cmi.command.mute true
lp group mod permission set cmi.command.kick true
lp group mod permission set cmi.command.warn true
lp group mod permission set cmi.command.fly true
lp group mod permission set coreprotect.inspect true
```

Каждая строка выполняется от консоли. Игрок в чате видит `▸ команда`.

Для выхода:

```
/gbatch stop
```
или просто напиши в чат `end` / `стоп`.

### Способ 2 — файл со скриптом

Создай файл `plugins/GovernixBatch/scripts/mymods.txt`:

```
# Модерация CMI
lp group mod permission set cmi.command.mute true
lp group mod permission set cmi.command.kick true
lp group mod permission set cmi.command.warn true
lp group mod permission set cmi.command.fly true

# CoreProtect
lp group mod permission set coreprotect.inspect true
lp group mod permission set coreprotect.lookup true
```

Выполни:

```
/gbatch run mymods
```

Файл `.txt` подставляется автоматически.

### Способ 3 — многострочная вставка

Находясь в batch-режиме, можешь вставить сразу **несколько строк** (если клиент позволяет) — они разобьются по `\n` и выполнятся по очереди.

## Команды

| Команда | Что делает |
| --- | --- |
| `/gbatch` | Справка |
| `/gbatch start` | Войти в режим |
| `/gbatch stop` | Выйти из режима |
| `/gbatch status` | Показать статус и счётчик |
| `/gbatch run <файл>` | Выполнить скрипт из `scripts/` |

Алиасы: `/gcmds`, `/gb`.

## Права

| Право | Описание | По умолчанию |
| --- | --- | --- |
| `governixbatch.use` | Доступ к `/gbatch` | op |

## Формат скрипт-файла

- Одна строка = одна команда
- Без начального `/` (но и с ним тоже работает)
- Пустые строки игнорируются
- Строки, начинающиеся с `#`, это комментарии

## Пример полного скрипта

```txt
# Полная настройка группы mod
lp group mod permission set cmi.command.mute true
lp group mod permission set cmi.command.kick true
lp group mod permission set cmi.command.warn true
lp group mod permission set cmi.command.jail true
lp group mod permission set cmi.command.invsee true
lp group mod permission set cmi.command.enderchest true
lp group mod permission set cmi.command.fly true
lp group mod permission set cmi.command.gamemode true
lp group mod permission set cmi.command.vanish true
lp group mod permission set cmi.command.tp true
lp group mod permission set cmi.command.tphere true
lp group mod permission set cmi.command.clear true
lp group mod permission set cmi.command.heal true
lp group mod permission set cmi.command.feed true

lp group mod permission set coreprotect.inspect true
lp group mod permission set coreprotect.lookup true
lp group mod permission set coreprotect.help true
lp group mod permission set coreprotect.teleport true

lp group mod permission set litebans.command.mute true
lp group mod permission set litebans.command.kick true
lp group mod permission set litebans.command.warn true
lp group mod permission set litebans.command.history true
lp group mod permission set litebans.command.check true
```

## Сборка

```bash
git clone https://github.com/ТВОЙ_НИК/GovernixBatch.git
cd GovernixBatch
mvn clean package
```

Готовый jar — в `target/GovernixBatch-1.0.0.jar`.

## Структура проекта

```
GovernixBatch/
├── .github/workflows/build.yml
├── src/main/java/ru/governix/batch/
│   ├── GovernixBatch.java
│   └── BatchListener.java
├── src/main/resources/
│   ├── plugin.yml
│   └── scripts/example.txt
├── .gitignore
├── LICENSE
├── pom.xml
└── README.md
```

## FAQ

**Почему сообщения исчезают из чата?**
Это нормально — команды перехватываются до отправки в публичный чат. Ты видишь их как `▸ команда` серым.

**Работает ли для игроков без прав?**
Нет, только для тех, у кого `governixbatch.use`.

**Можно ли делать так с CMI командами?**
Да. Любая команда от имени консоли работает: `/lp`, `/cmi`, `/litebans`, `/say`, `/time` и т.д.

**Опасность?**
Только ты и те, кому выдал право, могут использовать. Проверяй скрипты перед запуском — они выполняются от консоли.

## Лицензия

MIT. См. [LICENSE](LICENSE).

<p align="center"><img src="docs/logo.png" width="128" alt="ZeroVisual logo"></p>

# ZeroVisual

Бесплатный open-source **легитный** визуальный клиент для Minecraft **1.21.4** (Fabric).
Для тех, кто не может или не хочет платить за платные клиенты.

## Принципы

- **Только визуал, HUD и QoL.** Никакой автоматизации, увеличения хитбоксов и прочих преимуществ — клиент безопасен на серверах.
- **Бесплатно и открыто** навсегда (GPL-3.0).

## ⚠️ Название и официальные сборки

Код открыт (GPL-3.0), его можно менять. Но **название «ZeroVisual» и логотип нельзя использовать в изменённых версиях**. Форк должен называться иначе и не выдавать себя за ZeroVisual, особенно если в нём есть читы (ESP, X-ray и т. п.). Подробнее: [TRADEMARKS.md](TRADEMARKS.md).

Официальные сборки — **только** в [Releases](https://github.com/ZeroCoder407/ZeroVisual/releases). Они подписаны: изменённый или пересобранный клиент показывает в игре **«Неофициальная сборка»**.

## Установка

1. Установите [Fabric Loader](https://fabricmc.net/use/) для 1.21.4.
2. Положите в папку `mods`:
   - `zerovisual-<версия>.jar`
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Mod Menu](https://modrinth.com/mod/modmenu) — рекомендуется

## Сборка

Требуется JDK 21.

```bash
./gradlew build
```

Готовый `.jar` — в `build/libs/`. Запуск клиента для разработки: `./gradlew runClient`.

## Лицензия

Код: [GPL-3.0](LICENSE). Название и логотип ZeroVisual лицензией не передаются (GPL-3.0, пункт 7(e)), см. [TRADEMARKS.md](TRADEMARKS.md).

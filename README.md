<p align="center"><img src="docs/logo.png" width="128" alt="ZeroVisual logo"></p>

# ZeroVisual

Бесплатный open-source **легитный** визуальный клиент для Minecraft **1.21.4** (Fabric).
Для тех, кто не может или не хочет платить за платные клиенты.

## Принципы

- **Только визуал, HUD и QoL.** Никакой автоматизации, увеличения хитбоксов и прочих преимуществ — клиент безопасен на серверах.
- **Бесплатно и открыто** навсегда (GPL-3.0).

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

[GPL-3.0](LICENSE)

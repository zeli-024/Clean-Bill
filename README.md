# Clean Bill - Item Cleaner

Clean Bill is a server-side dropped-item cleaner for Minecraft 1.21.1 on NeoForge. Clients do not need to install it. It provides restart-safe cleanup timers, configurable announcements, unloaded-chunk timestamps, vanilla chest configuration menus, and a persistent three-page Item Pond.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 or newer in the 21.1 line
- Java 21

Place the JAR in the server's `mods` directory and restart the server.

## Main features

- Cleanup intervals use real seconds and minutes rather than exposing ticks.
- Timers pause while the server is offline and survive restarts.
- Items in unloaded chunks are never force-loaded. Timestamped items missed by a cleanup are handled when their chunks naturally load.
- Removal is limited to 200 item entities per tick to smooth large cleanups.
- Scheduled warnings, a per-second countdown, chat or compact action-bar output, clickable clean-now/clean-later controls, and XP or note-block sounds.
- Minimum item-age protection, disabled by default.
- GUI-only configuration using vanilla container screens, so the mod remains server-side.
- Persistent 135-stack Item Pond with take-only access and 45 protected filtered slots.

## Commands

Use `/cb` or `/cleanbill`. The former `/qc` and `/quackyclean` aliases remain available for existing servers.

| Command | Access | Purpose |
|---|---|---|
| `/cb` or `/cb help` | Everyone | Show the short categorized command list |
| `/cb status` | Everyone | Broadcast the timer and Pond status, including the requester |
| `/cb clean` | Configured as all or operators | Start cleanup immediately |
| `/cb cancel` | Configured as all or operators | Delay cleanup by restarting the interval |
| `/cb pause` | Operators | Pause the cleanup timer |
| `/cb resume` | Operators | Resume the cleanup timer |
| `/cb config` | Operators | Open the categorized configuration GUI |
| `/cb itempond` | Configured as all or operators | Open the Item Pond and its controls |

All settings are managed through `/cb config`. Direct setting commands were intentionally removed.

## Configuration GUI

`/cb config` contains five categories:

- **Cleanup** — interval, countdown, minimum item age, and pause/resume.
- **Alerts** — alert schedule, display location, sound, countdown visibility, button access, and private previews.
- **Messages** — edit, preview, or reset the alert, countdown, and result messages.
- **Item Pond** — opens the Pond because all Pond controls live in its bottom toolbar.
- **Maintenance** — reload, restore, full reset, help, and status.

Tooltips explain the current value, affected behavior, click action, default value, and important side effects. In each setting editor, clicking the center setting icon resets only that setting.

Standard duration editors use:

`-1m  -30s  -5s  -1s  [current/reset]  +1s  +5s  +30s  +1m`

The Item Pond wash editor uses:

`-1h  -30m  -5m  [current/reset]  +5m  +30m  +1h`

## Messages and previews

Message editing is started from the Messages GUI. The menu closes and captures the operator's next chat line without publishing it. Type `cancel` to leave the existing message unchanged.

Supported formatting:

- Legacy colors and styles such as `&l&aHello`
- Hex colors such as `<#A27BB5>Hello`
- Combined formatting such as `&l<#A27BB5>Hello`

Default UI and message colors use light gray, pastel green, pastel red, and the pale-purple `#A27BB5` accent.

Preview buttons show only the testing operator an example scheduled alert, countdown, or cleanup result. They use the active display mode and sound but do not change the timer, remove items, or create functional preview controls.

Action-bar messages are deliberately compact:

- `Cleanup in 10m`
- `Cleared 128 items`

## Item Pond

The Item Pond can be opened even while collection is disabled. Disabling collection only causes future cleared items to be destroyed rather than stored.

The bottom toolbar provides:

- Enable/disable collection
- Operators/everyone access
- Filter mode and filtered-storage access
- Previous and next page
- Wash now
- Wash interval
- Pond status
- Close

Normal and filtered Pond inventories are take-only: player items cannot be deposited through clicks, shift-clicking, dragging, or hotbar swaps.

To protect a Pond stack from washing:

1. Left-click **Filtered Storage** to enable filter mode.
2. Click a stack in the normal Pond.
3. Right-click **Filtered Storage** to open the protected 45-slot inventory.

Filtered items can only originate from the normal Pond. They are never removed by automatic or manual washing. If filtered storage is full, the transfer is refused and nothing is destroyed.

## Files and persistence

- Configuration: `config/cleanbill.json`
- Configuration backups: `config/cleanbill-backups/`
- Timers, timestamps, normal Pond contents, and filtered contents are stored with the world.

Existing Quacky Clean 0.1.1 world data, item timestamps, and configuration are migrated or retained automatically. Exact untouched older default messages are migrated to the softer 0.1.1 palette.

## Building

```sh
./gradlew build
```

The release JAR is written to `build/libs/`.

## Logo

No logo is bundled yet. Add a square PNG to `src/main/resources/` and set `logoFile` in `src/main/templates/META-INF/neoforge.mods.toml`.

## License and credit

Clean Bill - Item Cleaner is licensed under Apache License 2.0. Redistributed copies retain the license and NOTICE attribution to z.eli_. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

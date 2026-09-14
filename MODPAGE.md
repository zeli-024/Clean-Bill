# Clean Bill - Item Cleaner

Keep your server tidy without turning item cleanup into another chore.

Clean Bill is a lightweight, server-side item cleaner for Minecraft 1.21.1 on NeoForge. It clears dropped items on a schedule, warns players before it happens, and can preserve everything it collects inside an optional Item Pond. Players do not need to install the mod on their clients.

## Easy cleanup, controlled in-game

Choose how often cleanup runs using normal seconds and minutes—no tick conversion required. Operators can configure the mod through categorized vanilla chest menus opened with `/cb config`; no client-side configuration screen or companion mod is needed.

- Adjustable cleanup interval and countdown start
- Any number of scheduled warning times
- Optional minimum item-age protection for freshly dropped items
- Pause, resume, delay, or start a cleanup immediately
- Per-setting reset buttons and detailed tooltips throughout the GUI

## Friendly player warnings

Warnings can appear in chat or as a short action-bar message. Clean Bill can play a note-block or experience sound and show a second-by-second countdown. A 5-second countdown reads `5, 4, 3, 2`, then cleans instead of displaying `1`.

Clickable **Clean Now** and **Clean Later** controls appear on the same chat line as their alert. Access can be given to operators or everyone.

Alert, countdown, and cleanup-result messages are editable in-game. Formatting supports familiar `&` color/style codes as well as hex colors such as `<#A27BB5>`.

Private preview buttons let an operator test the current message, placement, and sound without changing the timer or deleting anything.

## The Item Pond

Instead of permanently destroying cleared items, Clean Bill can collect them in a persistent Item Pond. Choose a single- or double-chest interface and configure between 1 and 10 pages.

- Open it with `/cb itempond`, even while collection is disabled
- Use 18 storage slots per page in single-chest mode or 45 per page in double-chest mode
- Increase or reduce the page count without deleting items; unsafe reductions are refused
- Allow access for operators only or for everyone
- Take items out, but never insert unrelated items
- Move important stacks completely out of the normal Pond and into a separate 45-slot filtered chest
- Filtered items are protected from every wash
- Wash the normal Pond manually or on a configurable schedule
- Navigate pages and manage every Pond setting from its bottom toolbar

Only operators can use destructive Pond controls.

## Unloaded chunks are handled safely

Clean Bill never force-loads chunks to hunt for item entities. Dropped items receive a persistent timestamp. If an eligible item misses a cleanup because its chunk was unloaded, it is caught when that chunk naturally loads again.

Large cleanups are also spread across ticks, with no more than 200 item entities processed per tick. This avoids deleting a large backlog in one sudden burst.

## Restart-safe

Cleanup and Pond-wash timers are stored with the world and resume after a server restart. Item timestamps, Item Pond contents, and filtered storage are persistent as well. Offline time does not count down while the server is stopped.

Existing Quacky Clean 0.1.1 configurations, world data, item timestamps, and command habits are preserved during the rename. `/qc` and `/quackyclean` remain available as legacy aliases.

## Commands

- `/cb` or `/cb help` — show the command guide
- `/cb status` — broadcast the current timer and Item Pond status
- `/cb clean` — start a cleanup immediately
- `/cb cancel` — restart the cleanup timer and clean later
- `/cb pause` and `/cb resume` — control the schedule
- `/cb config` — open the configuration menus
- `/cb itempond` — open the Item Pond

Command and clickable-button permissions are configurable where appropriate. Status is available to everyone; configuration and administrative controls remain operator-protected.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 or newer in the 21.1 line
- Java 21
- Server installation only

Clean Bill - Item Cleaner is created by **z.eli_** and licensed under Apache License 2.0.

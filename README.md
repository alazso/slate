# Slate

Modern crate plugin foundation for Paper 26.1+.

## Runtime guarantees (major hardening)

- Folia/Paper/Purpur-aware scheduler dispatch through `PlatformScheduler` for world/entity-sensitive operations.
- Open operation ledger persisted in SQL (`open_operations`, `open_operation_steps`) for retry-safe tracking.
- SQL is authoritative state store; optional Redis lock coordination is available for multi-proxy contention control.
- Reward items support adapter-based resolution (`vanilla`, `nexo`, `craftengine`, `oraxen`, `itemsadder`, `mmoitems`) with metadata/PDC overlays.
- Physical key providers support `vanilla`, `nexo`, `craftengine`, and `itemsadder`.
- Preview/menu/animation/display visuals now use provider-aware item resolution with deterministic fallback materials.
- Crate lifecycle and eligibility checks support stateful crates (`active`, `disabled`, `hidden`, `archived`, `seasonal`), additive per-crate permissions, and optional WorldGuard-backed region constraints.
- Inventory GUI framework is now built-in (menu, preview, animation templates, and editor scaffolding) using Folia-safe scheduler dispatch + coroutine suspend flows.

## Supported platforms

- Paper 26.1+
- Purpur 26.1+
- Folia 26.1+
- Java 25+

## Build

```powershell
.\gradlew build
```

Build outputs:

- `build/libs/slate-0.1.0-SNAPSHOT.jar` - shaded deployable plugin jar (includes runtime dependencies)

## Local server smoke tests

```powershell
.\gradlew runServer
.\gradlew runPurpurServer
```

## Repository

https://github.com/alazso/slate

## Cluster coordination (optional Redis)

```yaml
cluster:
  redis:
    enabled: false
    host: localhost
    port: 6379
    password: ""
    database: 0
    lockTtlMs: 5000
    acquireTimeoutMs: 5000
```

When disabled, Slate uses local JVM locking only. When enabled, Redis lock keys are used as cross-proxy coordination hints while SQL remains source of truth.

## Commands

Slate registers three command roots:

- `/slate` with alias `/sla` - admin and maintenance commands.
- `/crates` - player crate interactions.
- `/keys` - player virtual key balance checks.

Player commands:

- `/crates list` - list loaded crate ids (`slate.player.list`).
- `/crates menu` - open player crate menu GUI (`slate.player.menu`).
- `/crates preview <crate>` - open crate preview GUI when enabled; otherwise text preview (`slate.player.preview` and `slate.preview.<crate>`).
- `/crates open <crate> [amount]` - open a crate with virtual keys (`slate.player.open` and `slate.open.<crate>`).
- `/crates claim` - claim pending rewards (`slate.player.claim`).
- `/crates locate` - show the nearest anchor in the current world (`slate.player.locate`).
- `/crates history [crate]` - show recent open history (`slate.player.history`).
- `/crates pity [crate]` - show pity counters (`slate.player.pity`).
- `/keys [crate]` - show virtual key balances (`slate.player.keys`).

Admin commands:

- `/sla help [page]`, `/sla version`, `/sla health`, `/sla reload`
- `/sla key give|take|set|clear|balance ...`
- `/sla anchor set|remove|list|tp|refresh`
- `/sla crate chances|validate|simulate ...`
- `/sla reward weight|chance|tier ...`
- `/sla tier list|create|delete|rename|weight|info|rewards ...`
- `/sla debug helditem|key|crate|player|eligibility|placeholder|database|scheduler|integrations|dump|toggle`
- `/sla session`, `/sla pity get|reset ...`, `/sla migrate`, `/sla backup`, `/sla restore <backupFile>`, `/sla audit [limit]`

See `COMMANDS.md` for the full command, flag, and permission reference.

## Config versions

- Root config now supports `config-version` (current `1`).
- Root config includes `gui` settings (click cooldown, animation timeout, menu layout).
- Crate files now support `crate-version` (current `1`), lifecycle/requirements, and optional `animation` / `preview` GUI sections.
- Probability editing commands remain file-backed (YAML is authoritative) and trigger a crate reload after each edit.

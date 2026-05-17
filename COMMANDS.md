# Slate Commands Reference

This document describes all currently registered command paths, flags, and permissions.

## Registered Root Commands

| Root Command | Alias | Audience | Description |
|---|---|---|---|
| `/slate` | `/sla` | Admin | Main Slate admin command tree. |
| `/crates` | _(none)_ | Player | Player-facing crate interactions. |
| `/keys` | _(none)_ | Player | Player virtual key balance queries. |

---

## `/slate` (`/sla`) Admin Commands

### General Admin

| Command | Permission | Notes |
|---|---|---|
| `/sla help` | `slate.admin.help` | Shows condensed command help text. |
| `/sla version` | `slate.admin.version` | Shows plugin version. |
| `/sla health` | `slate.admin.health` | Shows basic runtime health summary. |
| `/sla reload` | `slate.admin.reload` | Reloads config + crates, clears probability cache. |

### Key Administration

| Command | Permission | Flags | Notes |
|---|---|---|---|
| `/sla key give <player> <crate> <amount> [flags]` | `slate.admin.key.give` | `--virtual`, `--physical`, `--silent`, `--reason "<text>"` | Default mode is virtual unless `--physical` is present. |
| `/sla key take <player> <crate> <amount> [flags]` | `slate.admin.key.take` | `--virtual`, `--physical`, `--reason "<text>"` | Physical take requires target online. |
| `/sla key set <player> <crate> <amount> [flags]` | `slate.admin.key.set` | `--virtual`, `--reason "<text>"` | Physical `set` is explicitly unsupported. |
| `/sla key clear <player> [crate] [flags]` | `slate.admin.key.clear` | `--confirm`, `--reason "<text>"` | `--confirm` is required. Clears virtual keys. |
| `/sla key balance <player> [crate]` | `slate.admin.key.balance` | _(none)_ | Shows virtual key balances. |

### Anchor Administration

| Command | Permission | Notes |
|---|---|---|
| `/sla anchor set <crate>` | `slate.admin.anchor.set` | Player-only command; uses targeted block (range 6). |
| `/sla anchor remove` | `slate.admin.anchor.remove` | Player-only command; removes anchor at targeted block. |
| `/sla anchor list` | `slate.admin.anchor.list` | Lists configured anchors (truncated output). |
| `/sla anchor tp <crate>` | `slate.admin.anchor.tp` | Player-only command; teleports to the first stored anchor for the crate. |
| `/sla anchor scan` | `slate.admin.anchor.scan` | Stub; not implemented yet. |
| `/sla anchor refresh` | `slate.admin.anchor.refresh` | Rebuilds the in-memory anchor cache from storage. |
| `/sla anchor repair` | `slate.admin.anchor.repair` | Stub; not implemented yet. |

### Probability: Crate Commands

Aggregate access accepted by this namespace:
- `slate.admin.crate`
- `slate.admin.probability`

| Command | Direct Permission | Notes |
|---|---|---|
| `/sla crate chances <crate>` | `slate.crate.chances` | Shows mode/structure, configured values, effective chances, empty chance. |
| `/sla crate validate <crate>` | `slate.crate.validate` | Validates crate file and reports up to 10 errors. |
| `/sla crate simulate <crate> <rolls>` | `slate.crate.simulate` | Async simulation; capped to 250,000 rolls. |

### Probability: Reward Commands

Aggregate access accepted by this namespace:
- `slate.admin.reward`
- `slate.admin.probability`

| Command | Direct Permission | Notes |
|---|---|---|
| `/sla reward weight <crate> <reward> <weight>` | `slate.reward.weight` | Weighted mode only; clears `chance` field for reward. |
| `/sla reward chance <crate> <reward> <chance>` | `slate.reward.chance` | Flat percent mode only; clears `weight` field for reward. |
| `/sla reward tier <crate> <reward> <tier>` | `slate.reward.tier` | Tiered structure only; tier must exist in crate. |

### Probability: Tier Commands

Aggregate access accepted by this namespace:
- `slate.admin.probability`

| Command | Direct Permission | Notes |
|---|---|---|
| `/sla tier list <crate>` | `slate.tier.list` | Lists tier ids and weights. |
| `/sla tier create <crate> <tier>` | `slate.tier.create` | Creates tier with default `name=tier`, `weight=1`. |
| `/sla tier delete <crate> <tier>` | `slate.tier.delete` | Fails if any rewards still reference tier. |
| `/sla tier rename <crate> <old-tier> <new-tier>` | `slate.tier.rename` | Renames tier and rewrites reward tier references. |
| `/sla tier weight <crate> <tier> <weight>` | `slate.tier.weight` | Updates tier weight. |
| `/sla tier info <crate> <tier>` | `slate.tier.info` | Shows name, weight, computed tier chance, reward count. |
| `/sla tier rewards <crate> <tier>` | `slate.tier.rewards` | Shows rewards in tier with local/effective chance. |

### Debug

| Command | Permission | Notes |
|---|---|---|
| `/sla debug helditem` | `slate.admin.debug.item` | Prints held item type, display, lore, model data, and PDC keys. |
| `/sla debug key` | `slate.admin.debug.item` | Prints held-item physical key diagnostics (PDC, model data, strict match, recognized crate). |
| `/sla debug crate [crate]` | `slate.admin.debug.crate` | Lists loaded crate diagnostics or details for one crate. |
| `/sla debug player` | `slate.admin.debug.player` | Prints current player identity, location, health, and key player permissions. |
| `/sla debug eligibility <crate>` | `slate.admin.debug.eligibility` | Checks current player's open permission, world, keys, opens, pity, and cooldown for a crate. |
| `/sla debug placeholder <crate> [reward]` | `slate.admin.debug.placeholder` | Dumps computed probability placeholder values. |
| `/sla debug database` | `slate.admin.debug.database` | Shows current database type, pool size, and timeout. |
| `/sla debug scheduler` | `slate.admin.debug.scheduler` | Shows detected scheduler platform and dispatcher. |
| `/sla debug integrations` | `slate.admin.debug.integrations` | Shows optional integration plugin enabled states. |
| `/sla debug dump` | `slate.admin.debug.dump` | Dumps a truncated loaded-crate summary. |
| `/sla debug toggle` | `slate.admin.debug.toggle` | Toggles debug mode in memory. |

### Maintenance And Utility Commands

| Command | Permission | Status |
|---|---|---|
| `/sla session` | `slate.admin.session` | Shows basic session/coordination status. |
| `/sla pity get <player> <crate>` | `slate.admin.pity` | Reads stored pity count for target player/crate. |
| `/sla pity reset <player> <crate>` | `slate.admin.pity` | Resets pity count to 0 for target player/crate. |
| `/sla migrate` | `slate.admin.migrate` | Shows migration dry-run/status guidance (migrations are startup-applied). |
| `/sla backup` | `slate.admin.backup` | Creates sqlite backup file under `plugins/Slate/backups`. |
| `/sla restore <backupFile>` | `slate.admin.restore` | Restores sqlite database from backup file. |
| `/sla audit [limit]` | `slate.admin.audit` | Lists recent operation-ledger state transitions. |

---

## `/crates` Player Commands

Base access for `/crates` is guarded by `slate.player.menu`.

| Command | Permission | Additional Permission Rule | Notes |
|---|---|---|---|
| `/crates` | `slate.player.menu` | _(none)_ | Prints usage hint. |
| `/crates menu` | `slate.player.menu` | _(none)_ | Opens the crate menu GUI. |
| `/crates list` | `slate.player.list` | _(none)_ | Lists loaded crate ids. |
| `/crates preview <crate>` | `slate.player.preview` | Must also have `slate.preview.<crate>` | Opens configured preview GUI (falls back to text preview when GUI preview is disabled). |
| `/crates open <crate> [amount]` | `slate.player.open` | Must also have `slate.open.<crate>` | Opens crate (bulk amount capped by config). |
| `/crates claim` | `slate.player.claim` | _(none)_ | Attempts to deliver pending rewards. |
| `/crates locate` | `slate.player.locate` | _(none)_ | Shows nearest anchor in current world. |
| `/crates history [crate]` | `slate.player.history` | _(none)_ | Shows recent crate open history (optionally filtered by crate). |
| `/crates pity [crate]` | `slate.player.pity` | _(none)_ | Shows current pity counter(s) for player. |

---

## `/keys` Player Commands

| Command | Permission | Notes |
|---|---|---|
| `/keys` | `slate.player.keys` | Shows total virtual keys and per-crate breakdown. |
| `/keys <crate>` | `slate.player.keys` | Shows virtual keys for a single crate. |

---

## Flag Reference

Flags are currently parsed only for `/sla key ...` commands.

| Flag | Applies To | Behavior |
|---|---|---|
| `--virtual` | `key give/take/set` | Forces virtual mode. |
| `--physical` | `key give/take` | Forces physical mode (target must be online). |
| `--silent` | `key give` (virtual path) | Passes silent mode to virtual key grant. |
| `--confirm` | `key clear` | Required safety flag; command fails without it. |
| `--reason "<text>"` | `key give/take/set/clear` | Optional reason text appended to response output. |

### Flag Parsing Notes

| Rule | Detail |
|---|---|
| Token parsing | Flags are split on whitespace. |
| Reason parsing | `--reason` captures remaining substring after marker and trims quotes. |
| Mode conflict | Passing both `--physical` and explicit `--virtual` is rejected. |
| Default mode | `key give/take/set` defaults to virtual when `--physical` is absent. |

---

## Permission Catalog

This is the current permission key set defined in `SlatePermissions`.

| Permission |
|---|
| `slate.player.menu` |
| `slate.player.list` |
| `slate.player.preview` |
| `slate.player.open` |
| `slate.player.claim` |
| `slate.player.locate` |
| `slate.player.history` |
| `slate.player.pity` |
| `slate.player.keys` |
| `slate.animation.skip` |
| `slate.preview.<crate>` _(prefix rule)_ |
| `slate.open.<crate>` _(prefix rule)_ |
| `slate.admin.help` |
| `slate.admin.version` |
| `slate.admin.health` |
| `slate.admin.reload` |
| `slate.admin.audit` |
| `slate.admin.key.give` |
| `slate.admin.key.take` |
| `slate.admin.key.set` |
| `slate.admin.key.clear` |
| `slate.admin.key.balance` |
| `slate.admin.anchor.set` |
| `slate.admin.anchor.remove` |
| `slate.admin.anchor.list` |
| `slate.admin.anchor.tp` |
| `slate.admin.anchor.scan` |
| `slate.admin.anchor.refresh` |
| `slate.admin.anchor.repair` |
| `slate.admin.debug.item` |
| `slate.admin.debug.crate` |
| `slate.admin.debug.player` |
| `slate.admin.debug.eligibility` |
| `slate.admin.debug.placeholder` |
| `slate.admin.debug.database` |
| `slate.admin.debug.scheduler` |
| `slate.admin.debug.integrations` |
| `slate.admin.debug.dump` |
| `slate.admin.debug.toggle` |
| `slate.admin.crate` |
| `slate.admin.reward` |
| `slate.crate.chances` |
| `slate.crate.validate` |
| `slate.crate.simulate` |
| `slate.reward.weight` |
| `slate.reward.chance` |
| `slate.reward.tier` |
| `slate.tier.list` |
| `slate.tier.create` |
| `slate.tier.delete` |
| `slate.tier.rename` |
| `slate.tier.weight` |
| `slate.tier.info` |
| `slate.tier.rewards` |
| `slate.admin.probability` |
| `slate.admin.session` |
| `slate.admin.pity` |
| `slate.admin.migrate` |
| `slate.admin.backup` |
| `slate.admin.restore` |


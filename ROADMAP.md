# Slate Roadmap

Last audited: 2026-05-17 (local)

This roadmap tracks the current repository state. Treat it as a living planning document, not a release guarantee.

## Done

- Paper 26.1+ plugin scaffold with generated plugin metadata, Java 25/Kotlin 25 build targets, shaded runtime dependencies, and Folia support flag.
- Core plugin boot wiring for config, locale/message service, storage, crate manager, key service, opening service, anchor service, listener registration, Redis coordination, and `SlateAPI`.
- Root config loading and validation for database, Redis, and bulk-open settings.
- Crate YAML loading with validation, filename-derived ids, optional recursive scanning, and last-known-good retention on reload failures.
- Persistent SQLite/PostgreSQL storage mappings for virtual keys, pity, cooldowns, open logs, pending prizes, crate anchors, and open-operation audit records.
- Dialect-specific SQL migrations through `V004`, including recovery for older V001 databases that are missing later core tables.
- Virtual keys and physical Slate keys, including strict PDC matching, amount-aware key counting for multi-key crates, and vanilla/Nexo/CraftEngine physical key provider support.
- Reward item delivery for vanilla and reflected external providers: Nexo, CraftEngine, Oraxen, ItemsAdder, and MMOItems.
- Opening pipeline with idempotent operation records, fresh per-command batch request ids, amount-aware key consumption/failure reasons, cooldown/max-pulls/world checks, events, pity handling, item/command/message delivery, pending-prize queueing, and open history logging.
- Player commands for crate list, preview, open, claim, locate, history, pity, and key balances.
- Admin commands for help, health, version, reload, key mutation, anchor set/remove/list, probability inspection and mutation, debug utilities, audit listing, backup, restore, migration status, session info, and pity management.
- `README.md` and `COMMANDS.md` command references aligned with the registered `/slate`/`/sla`, `/crates`, and `/keys` command surface.
- Removed unused, unregistered `PendingCommand` and `StubCommand`; `/crates claim` remains the implemented pending-reward claim path.
- First-pass quick-hit stub wiring for anchor teleport/cache refresh and crate/player/eligibility debug diagnostics.
- Native crate-anchor display groups using Bukkit/Paper `TextDisplay` and `ItemDisplay`, managed through a shared display service/factory with PDC ownership tags, stale cleanup, reload handling, and Folia-aware scheduler boundaries.
- Crate lifecycle/config foundation: `crate-version` and `config-version` parsing/validation, lifecycle states (`active`, `disabled`, `hidden`, `archived`, `seasonal`), centralized crate eligibility checks for open/list/preview paths, additive per-crate permission requirements, optional WorldGuard-backed region requirement abstraction, and YAML-authoritative probability edit guardrails with reload outcome enforcement.
- Focused unit tests for storage persistence/migration behavior, opening behavior, open service, listeners, command registration/permissions/flags/target resolution, player key output, config validation, crate reload retention, crate display config parsing, key service behavior, probability calculations, messages/locales, item utilities, and weighted random selection.

## Todo

### Command Surface And Admin Operations

- Implement or remove remaining visible stubs: `/crates menu`, `/sla anchor scan`, and `/sla anchor repair`.
- Add an admin crate dry-run workflow such as `/sla crate dryrun <crate> <player> [amount]` that reports whether the player can open, keys available/required, cooldown, world/permission status, Vault cost, inventory or pending-delivery mode, possible reward pool, excluded rewards, and estimated delivery path.
- Add safe reload modes: `/sla reload config`, `crates`, `messages`, `displays`, and `all`, plus `/sla reload --check` preflight output for config validity, crate validity counts, invalid crate files, and last-known-good behavior.
- Add admin crate import/export tooling: `/sla crate export <crate>`, `/sla crate import <file>`, `/sla crate clone <crate> <newId>`, and `/sla crate diff <crate> <file>`.
- Add generated crate templates such as `/sla crate template basic`, `tiered`, `percent`, and `seasonal`.
- Harden backup/restore. Current commands are SQLite-only and restore by replacing the database file while warning for restart; define safe operational behavior for live servers.

### Crate Configuration And Lifecycle

- Add migration commands such as `/sla migrate configs` and `/sla migrate crate <crate>` for future schema revisions beyond the current `config-version: 1` and `crate-version: 1` baseline.
- Revisit probability persistence architecture. Current policy keeps YAML authoritative with reload-enforced edits and archived-crate mutation blocking; storage-backed overrides remain deferred.
- Expand WorldGuard integration beyond current optional allow/deny region checks (for example custom WG flags, anchor placement protections, and protected-area animation policies).

### Opening Flow, Costs, And Requirements

- Add mass-open UX that can open all available keys for a crate in one player action while respecting configured caps, cooldowns, costs, and inventory/pending behavior.
- Add animation skip and fast-open support with per-crate settings, permissions such as `slate.open.fast`, and command flags such as `/crates open <crate> --fast` and `/crates open <crate> --animation`.
- Add support for multiple distinct key requirements on the same crate, not just an amount of one crate key.
- Add multi-crate key support so one key can be valid for a configured set of crates.
- Add a transactional economy/cost model for paid opens that defines validation, cost reservation/take, key consumption, reward roll, delivery, pending-queue behavior, and refund policy for cancelled events, pre-roll failures, inventory-full delivery, command reward failure, key+money costs, and bulk-open partial failures.
- Add pushback behavior for crate anchors when players fail configured requirements.

### Rewards, Fulfillment, And Limits

- Wire `Prize.broadcast` and `Prize.broadcastMessage` into delivery, or remove the fields from config parsing until broadcast behavior is designed.
- Add optional per-crate reward re-roll support.
- Add selectable reward crates/openings for non-gambling flows where players choose a reward instead of receiving a random roll.
- Add milestone rewards for opening a configured amount of a crate in a row, separate from pity-tier guarantee behavior.
- Add reward-level permission requirements so rewards can be included or excluded based on player permissions.
- Add reward tags/categories for preview filtering, admin searching, roll limits, broadcast policies, analytics, and future GUI editor organization.
- Add reward roll limits, cooldowns, and claim limits, including global max wins with reset periods, per-player max wins with reset policies, and per-reward cooldowns for one-time ranks, limited seasonal cosmetics, server-wide jackpots, and weekly high-value caps.
- Add optional daily, weekly, and monthly crate/key drops or scheduled prizes.
- Add a virtual reward bundle/satchel/storage system for overflow items when a player is offline or their inventory is full.
- Add dedicated reward/open log output for audit-friendly roll history outside normal server logs.
- Define broadcast semantics: when to broadcast, whether to use locale keys or raw crate messages, how PlaceholderAPI and probability variables should compose, and whether broadcasts should be per-open or aggregated for bulk opens.
- Decide offline physical key operations. Virtual keys already work by UUID/cached player resolution; physical keys need a storage or mailbox model if they should support offline players.

### Items And Integrations

- Complete full custom item support for both rewards and keys across supported vanilla and custom-engine providers.
- Add ExecutableItems compatibility to the shared custom item provider layer for rewards, keys, crate icons, and metadata-preserving item resolution.
- Deepen ItemsAdder compatibility for reward custom items by ItemsAdder id, custom key textures, custom crate icons, and preservation of ItemsAdder metadata/lore/model data.
- Deepen Oraxen compatibility for Oraxen item rewards, custom key items, custom crate visuals, glyph-backed presentation, and fancy menu icons.
- Add MythicMobs/MMOItems compatibility for RPG server workflows, including MythicMobs drops/items, MMOItems weapons/armor, boss crate rewards, and dungeon/raid crate ecosystems.
- Add Vault compatibility for economy costs, money rewards, balance requirements, and broad economy/permissions/chat bridging without directly depending on each downstream plugin. Bukkit permissions should remain directly supported even if Vault permission checks are available.
- Add PlaceholderAPI compatibility for both consuming placeholders and exposing Slate placeholders. Planned examples include `%player_name%` in crate messages, `%slate_keys_<crate>%`, `%slate_total_opened%`, `%slate_cooldown_<crate>%`, `%slate_last_winner_<crate>%`, `%slate_last_prize_<crate>%`, and placeholder rendering inside reward commands, titles, lore, broadcasts, anchor displays, holograms, and animation text.
- Add first-class LuckPerms awareness alongside Vault permission bridging. Planned use cases include group/track checks, group-restricted crates, optional temporary permission/group rewards, and diagnostics that explain exactly which permission blocks a player from opening a crate.

### GUI, Displays, And Player Experience

- Add an in-game GUI editor for managing crates, rewards, keys, displays, requirements, and probability settings without direct YAML edits.
- Add configurable per-crate preview GUIs so each crate can present rewards with custom layout, icons, lore, and navigation.
- Add configurable per-crate opening GUIs and roll animations, including CSGO-style, slot-machine-style, and agency-style animations where players choose from three options.
- Add temporary reward-opening Display-entity animation groups, floating icon animation, per-player display text, and dynamic cooldown display refresh with once-per-second-or-less update behavior.
- Add crate-anchor particle presentation options. Particles should be lag/performance aware with client-side effects preferred where possible.
- Add crate item support so physical crate items can be granted to player inventories and placed/linked into crate anchors or zones.
- Decide product direction for `/crates menu`: inventory GUI, text menu, or integration with an external menu plugin.
- Define anchor management UX beyond set/remove/list/teleport/refresh; scan and repair need a clear operator workflow before implementation.

### Analytics, Observability, And Notifications

- Add richer player-facing crate stats with `/crates stats [crate]`, including total opens, best win, last win, pity progress, keys owned, cooldown, and milestone progress.
- Add admin analytics commands such as `/sla stats crate <crate>`, `/sla stats player <player>`, and `/sla stats rewards <crate>` for opens, most-won rewards, rare wins, key sink/source totals, economy spent, pending delivery count, failed deliveries, and average configured reward value.
- Add optional Discord/webhook notifications for rare rewards, suspicious mass opens, delivery failures, pending prize creation, admin probability edits, and backup/restore events.
- Define observability expectations: audit command depth, structured logs, metrics, and whether operation-step history should be queryable by player/crate/state.

### Reliability, Testing, And Release Readiness

- Add PostgreSQL integration coverage for migrations and `ExposedStorage`; SQLite is tested, but PostgreSQL is only represented by SQL/resources and shared Exposed mappings.
- Add Redis coordination tests around lock acquisition, timeout, release token safety, and behavior when acquisition fails.
- Add a real server smoke-test path for Paper/Purpur/Folia command registration and plugin startup, ideally CI-backed.
- Add tests for pending reward claim edge cases: missing crate/prize, mixed deliverable and undeliverable queues, and command/message placeholder rendering on claim.
- Add integration tests or explicit compatibility documentation for reflected external item providers.
- Decide the cluster story beyond open locks: whether anchors, reloads, cache invalidation, and pending-claim behavior need Redis pub/sub or another coordination layer.
- Define public API stability for `SlateAPI`, events, and storage-facing contracts before other plugins depend on them.
- Define release readiness: minimum supported Paper/Purpur/Folia versions, CI matrix, manual smoke checklist, and compatibility policy for optional item-provider integrations.

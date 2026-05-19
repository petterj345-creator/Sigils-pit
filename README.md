# AbyssSigils

Diablo-style sigil sockets + MythicDungeons-style per-party instanced dungeons (The Abyss) for Paper 1.21.x with MythicMobs.

The dungeon editor is a chest-GUI with anvil inputs — like MythicDungeons. You build a template world, click slots in the editor to set spawn points, mob lists, waves, thresholds, etc. Templates clone to a fresh instance world on entry and that instance is deleted when the party leaves.

## Requires

- Paper 1.21.x
- MythicMobs (hard dependency)
- Vault (soft — needed for money rewards; everything else works without it)
- MMOItems (soft, currently unused for sigils — kept for future)

## Build

```bash
mvn package
```

Drop `target/AbyssSigils-1.0.0.jar` into your `plugins/` folder.

---

## Quick start

```text
1.  /abyss create crypt
    → makes a void world abyss_tpl_crypt, tps you in,
      gives you creative, opens the editor GUI.

2.  Click "Teleport to Template" if you're not already there.
    Build your dungeon — arena, decorations, anything.

3.  Open the editor again (/abyss edit crypt).

4.  Click "Mode" to pick MAP or WAVES:
     • MAP   — PoE-style. Trash flows continuously from spawn points.
               Boss spawns when kill threshold met.
     • WAVES — Infernal-Hordes-style. Discrete waves; "Wave 1/3" bar.
               Boss spawns after the last wave is cleared.

5.  Click "Player Spawn" and "Boss Spawn" — they set to your current location.

6.  Click "Spawn Points" → "Add Spawn Point" at each location.
    Optionally click a point to give it its own mob list (overrides default).

7.  Click "Default Trash Mobs" → "Add" → type a MythicMob ID. Optionally
    right-click to set count, drop-key to set level.
    (MAP mode uses this list as fallback for spawn points without one.)

8.  Click "Boss Mob" to set the boss's MythicMob ID and level.

9.  If WAVES mode: click "Waves" → "Add Wave" → click the wave → add mobs.
    Right-click a wave to set its post-clear delay.

10. Click the green/red wool to see if the template is playable.
    Click the Nether Star to test it solo.

11. Click "Rewards" to set up the loot chest:
     • Drag items from your inventory into the top 27 slots.
       Each one becomes a pool entry (default 50% chance, count 1).
     • Left-click a pool item → change its chance %.
     • Right-click a pool item → change its count range (e.g. 1-3).
     • Shift-click → remove.
     • "Max Items Per Chest" caps how many entries roll into one chest.
     • Money + XP rewards each have their own range + chance.
       Money requires Vault.
```

That's it. The portal block (configured in `config.yml`) will pick a random playable template each time a player right-clicks it.

---

## Player experience

- **Book of Sigils** — right-click the book to open your socket menu. New players get one on first join (configurable). Admins: `/abyss givebook <player>`.
- The socket menu has **10 small sockets** (minor sigils only) and **3 big sockets** (major sigils only). Drag sigils in, they grant their stats automatically.
- **Stat categories:**
  - **Combat** — damage, defense, crit, speed, HP, lifesteal, thorns
  - **Gathering** — extra wood/ore/crop drops (chance-based, stacks)
  - **MMOItems** (requires MMOItems) — Attack/Magic damage, crit chance/power, PvE/PvP damage, applied via MMOItems' stat system
- Only **MAJOR** sigils can carry the powerful stats (big damage %, lifesteal, MMO stats, etc.). MINORS are the everyday workhorses.
- **Right-click the portal block** — enter The Abyss. Sneak to bring nearby players as a party.
- **Boss bar** shows progress (kills X/Y, Wave N/M, or boss HP).
- **Kill the boss** → upgrade altar + reward chest spawn at the death spot.
- **`/abyss leave`** → exit. Instance world is deleted.

---

## How MythicMobs drops sigils

AbyssSigils registers two custom MythicMobs item types. Use them in any droptable:

```yaml
Drops:
  - abyss_sigil{id=wrath;tier=1} 1 0.25
  - abyss_sigil{id=random;tier=2} 1 0.05
  - abyss_sigil_dust 3 1.0
```

- `id=` matches a key in `sigils.yml`. `id=random` rolls any sigil.
- `tier=` clamps to the sigil's max tier.

See `example_mobs.yml`.

---

## Commands

| Command | Notes |
|---|---|
| `/sigils` | open socket menu (or right-click your Book of Sigils) |
| `/abyss leave` | exit a dungeon |
| `/abyss create <name>` | new template + open editor + give you the wand |
| `/abyss edit <name>` | tp to template + give wand + open editor |
| `/abyss list` | all templates + playable status |
| `/abyss delete <name> confirm` | wipe a template |
| `/abyss sigil list` | admin GUI of all sigil definitions |
| `/abyss sigil create [id]` | open the sigil creator |
| `/abyss sigil edit <id>` | edit existing sigil definition |
| `/abyss mythicdrops` | wizard: pick a Mythic mob → add sigil to its drops |
| `/abyss givesigil <p> <id\|random> [tier]` | admin give |
| `/abyss givedust <p> <n>` | admin give |
| `/abyss givebook <p>` | admin give Book of Sigils |
| `/abyss reload` | reload configs |

## The Editor Wand

When you `/abyss create` or `/abyss edit` a template, you get an **Abyss Editor Wand** (blaze rod). While in the template world:
- **Right-click air** → open the template editor menu
- **Right-click any block** → context menu (set as player spawn, boss spawn, spawn point, etc.)
- **Sneak + left-click block** → remove any markers at that block

The wand is auto-removed when you leave the template world.

Everything else (spawn points, waves, mob entries, thresholds, level, etc.) lives in the GUI.

---

## File layout

```
plugins/AbyssSigils/
├── config.yml           portal location, exit location, upgrade costs
├── sigils.yml           sigil definitions
├── data/sockets.yml     per-player socket persistence
└── templates/
    ├── crypt.yml        per-template: mode, spawn points, waves, mobs, boss
    └── ...

(server root)
├── abyss_tpl_crypt/     template world — edited via /abyss edit
└── abyss_inst_<uuid>/   transient instance — auto-deleted on session end
```

---

## Architecture

- **Templates** are the source-of-truth: a world + a YAML. Edited in-game.
- **Instances** are world-folder copies of templates, loaded with `autoSave=false`. Deleted on session end.
- **Sigil data** lives in `ItemStack` PersistentDataContainer (id, tier, encoded substats).
- **Stat application**:
  - Max HP / speed → vanilla `AttributeModifier` (Registry-keyed for 1.21 cross-version compat).
  - Damage / defense / crit → `EntityDamageByEntityEvent`.
- **Editor GUI** is built on a small `Holder` base class with per-slot click handlers; one global listener dispatches by slot.
- **Anvil inputs** use the `PrepareAnvilEvent` hook to read the player's rename text on confirm-click.
- **MythicMobs bridge**: `MythicHook` registers `abyss_sigil` + `abyss_sigil_dust` item types; `MythicMobDeathEvent` drives kill counts.
- **Boss bar**: vanilla `BossBar`, shared across the party, switches between kill/wave progress and boss HP tracking.

## Known limitations

- World cloning is slower than SlimeWorldManager. 1–3s pause on entry for large templates. Swap to SWM if needed.
- No party invite system — proximity-based at the portal.
- Boss HP tracking polls `EntityDamageEvent` post-tick; very high HP-regen mobs may show stale numbers for a tick.
- Per-spawn-point mob lists are only used in MAP mode. In WAVES mode, spawn points are pure locations and waves drive what spawns.

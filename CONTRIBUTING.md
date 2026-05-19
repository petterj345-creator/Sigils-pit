# Contributing

Thanks for your interest in AbyssSigils.

## Building locally

You need JDK 21+ and Maven.

```bash
git clone https://github.com/YOUR_USER/AbyssSigils.git
cd AbyssSigils
mvn clean package
```

The built jar lands in `target/AbyssSigils-<version>.jar`. Drop it in your test server's `plugins/` folder.

## Running a test server

You'll want a Paper 1.21.x server with at least MythicMobs installed. Optional: MMOItems, Vault + an economy plugin (EssentialsX works fine).

A typical dev loop:
1. `mvn clean package`
2. `cp target/AbyssSigils-*.jar /path/to/server/plugins/`
3. Restart the test server (or use a hotswap plugin)
4. In-game: `/abyss create test`, build a small arena, `/abyss test`

## Pull requests

- Open an issue first if it's a non-trivial change so we can discuss the approach.
- Keep changes focused — one feature per PR.
- Match the existing code style: 4-space indent, no wildcard imports except in tests.
- Write a one-line summary of your change in the PR description.

## Project layout

```
src/main/java/com/abyss/sigils/
├── AbyssPlugin.java          main plugin class, wires everything together
├── commands/                 /abyss + /sigils command dispatcher
├── dungeon/                  template/session/manager/rewards/portal/wave system
├── gui/                      all editor + player GUIs (with anvil + editor wand)
├── integration/              MythicMobs, MMOItems, Vault hooks (all soft-deps)
├── sigils/                   sigil definitions, items, stat applier, gathering
├── socket/                   the Book of Sigils + socket store
└── util/                     small helpers (Text colorizer, etc.)
```

## Releasing

Releases are automated: push a tag starting with `v` (e.g. `v1.2.0`) and GitHub Actions builds the jar and attaches it to a new release.

```bash
git tag v1.0.0
git push origin v1.0.0
```

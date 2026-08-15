# Contributing to Dim

Thank you for contributing to Dim. Dim is a GPLv3-licensed fork of GrimAC; preserve existing attribution, license headers, and third-party notices when modifying upstream-derived code.

## Pull requests

- Keep changes compatible with Bukkit, Spigot, Paper, Folia, Purpur, and Fabric where applicable.
- Prefer focused changes that can be cleanly synchronized with upstream.
- Do not package proprietary dependencies or remove required source/license notices.
- Add comments for complex logic and test changes before opening a pull request.
- Use clear commit messages and include reproduction steps for bug fixes.

## Upstream changes

Read [UPSTREAM.md](UPSTREAM.md) before bringing changes from GrimAC. Keep the `ac.grim.grimac` namespace, public API names, internal compatibility keys, and legacy command aliases unless a change is required for correctness.

## Development

Dim uses Gradle Kotlin scripts. Java 17 is required at runtime; the current build toolchain may require a newer JDK for the target Minecraft versions. Run:

```bash
./gradlew build --no-daemon
```

## Security

Please report security issues privately through the repository's security reporting mechanism rather than publishing an exploitable bypass in an issue.

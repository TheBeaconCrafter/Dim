# Keeping Dim synchronized with GrimAC

Dim is intentionally maintained as a low-conflict fork of [GrimAC](https://github.com/GrimAnticheat/Grim). Upstream code and API namespaces remain `ac.grim.grimac`; this is deliberate and should not be changed during routine updates.

## Add the upstream remote once

Git remotes are local configuration, so add the upstream remote in your own clone:

```bash
git remote add upstream https://github.com/GrimAnticheat/Grim.git
git fetch upstream
```

## Sync procedure

1. Create a synchronization branch from the Dim default branch.
2. Fetch upstream and merge the upstream branch into that branch. Replace `<upstream-branch>` with the branch currently used by GrimAC:

   ```bash
   git fetch upstream
   git switch -c sync/upstream-<date>
   git merge --no-commit upstream/<upstream-branch>
   ```

3. Resolve conflicts by preferring upstream for anticheat/check implementation changes unless the change is explicitly listed as Dim-specific below.
4. Preserve the Dim product layer:
   - `Dim` names, metadata, artifact prefixes, and user-facing messages.
   - `/dim` as the primary command, with `/grim` and `/grimac` as compatibility aliases.
   - The BeaconLabsVelocity protocol bridge.
   - `ATTRIBUTIONS.md`, this file, and the GPL/license notices.
5. Run the build and review the final diff before merging the synchronization branch:

   ```bash
   ./gradlew build --no-daemon
   git diff --check
   ```

## Compatibility surfaces to keep stable

The following are intentionally not package-renamed: `ac.grim.grimac`, public Grim API class names, internal `grim.*` permission/check keys, Fabric mixin/access-widener resource names, and legacy command aliases. They are compatibility surfaces, not missed branding opportunities.

When upstream changes one of these surfaces, document the compatibility impact in the Dim changelog rather than silently changing it.

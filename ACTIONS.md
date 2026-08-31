# ACTIONS

## 2026-08-31

- C3 (`Fixes #2`): add catalog by Maven GAV. `POST /catalogs` and `catalog add g:a:v` fetch `classifier=agent-catalog` with Maven Resolver, store `catalogs/g/a/v.yaml` plus examples zip snippets, re-merge. Missing classifier is an error; re-add replaces that version; merge conflict does not store the new catalog. Remotes: `--repo` / `AXIOM_REPOS`, `~/.m2/settings.xml` (with matching `<server>` credentials), Maven Central fallback.
- Javadoc `@since` is `1.0.0` (first public release; not `0.1.0` / SNAPSHOT).
- C3/C4 (`Fixes #1`): `axiom-mcp` process opens `$AXIOM_DATA` or `~/.axiom`, creates it if missing, empty store is valid, HTTP `/health` on `--bind` (default 127.0.0.1:8741), optional `--stdio` MCP in the same JVM. Restart reloads catalogs from disk and re-merges.
- Adopted hybrid TODO.md + GitHub Issues. MCP tasks T5–T10 are issues #1–#6; T16 (deferred auth/gap issues) is #7.

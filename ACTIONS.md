# ACTIONS

## 2026-09-28

- SNAPSHOT is `1.1.0-SNAPSHOT` (tracks the `1.0` line). `@since` stays `1.0.0` until a `1.0` tag exists.

## 2026-08-31

- C4 (`Fixes #4`): `catalog_lookup` / `catalog_search` / `catalog_get` over HTTP (`GET /lookup`, `/search`, `/get`) and MCP stdio. Lookup matches id/title/triggers/blessed.symbol with optional language filter and returns snippet text. Empty lookup is success plus a search/gap hint. Get of an unknown id is an error with nearby hits. Tool descriptions tell agents to look up before writing a helper / try-catch / `*Util`.
- C3 (`Fixes #2`): add catalog by Maven GAV. `POST /catalogs` and `catalog add g:a:v` fetch `classifier=agent-catalog` with Maven Resolver, store `catalogs/g/a/v.yaml` plus examples zip snippets, re-merge. Missing classifier is an error; re-add replaces that version; merge conflict does not store the new catalog. Remotes: `--repo` / `AXIOM_REPOS`, `~/.m2/settings.xml` (with matching `<server>` credentials), Maven Central fallback.
- Javadoc `@since` is `1.0.0` (first public release; not `0.1.0` / SNAPSHOT).
- C3/C4 (`Fixes #1`): `axiom-mcp` process opens `$AXIOM_DATA` or `~/.axiom`, creates it if missing, empty store is valid, HTTP `/health` on `--bind` (default 127.0.0.1:8741), optional `--stdio` MCP in the same JVM. Restart reloads catalogs from disk and re-merges.
- Adopted hybrid TODO.md + GitHub Issues. MCP tasks T5–T10 are issues #1–#6; T16 (deferred auth/gap issues) is #7.

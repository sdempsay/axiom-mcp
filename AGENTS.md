# Agent notes

This is **sdempsay/axiom-mcp**. Use `gh`, not `glab`. Umbrella: [sdempsay/axiom](https://github.com/sdempsay/axiom).

## Backlog

Hybrid tracker (same as review-pipeline):

- **`TODO.md`** — thin index for **MCP/CLI/ingest** tasks only
- **[GitHub Issues](https://github.com/sdempsay/axiom-mcp/issues)** — acceptance criteria
- **`ACTIONS.md`** — work log

Cross-cutting work belongs on the **umbrella**, not here.

**Start:** `TODO.md` → `gh issue view N --repo sdempsay/axiom-mcp`.  
**Ship:** PR with `Fixes #N` → mark TODO `complete` → line in `ACTIONS.md`.  
**Add work:** open an MCP issue first, then a TODO row.

## Session start

- Read `TODO.md`, then the issue
- PRDs: umbrella `prds/C3`–`C5`, `C9`
- `~/.grok/rules/maven.md`

## Versioning (`@since` and SNAPSHOT)

SNAPSHOT minor is one ahead of the release line. This repo is `1.1.0-SNAPSHOT` (tracks `1.0.x`). Never use the POM SNAPSHOT as `@since`. Test classes do not need `@since`.

1. `git tag --sort=-v:refname | head -5`
2. POM e.g. `1.1.0-SNAPSHOT` → release line `1.0`
3. Latest tag on that line (e.g. `1.0.12`)
4. `@since` = that tag + 1 patch (`1.0.13`)
5. No tags on the line yet → `1.(x-1).0` (`1.1.0-SNAPSHOT` → `@since 1.0.0`)

## JUnit

dempsay-parent enables JUnit Jupiter when `src/test/resources/tests.md` exists. Do not hand-add `junit-jupiter` to module POMs.

## Code review

`bin/install-hooks` — pre-commit runs `code-review diff --staged` on Java / `pom.xml`. Bypass: `SKIP_CODE_REVIEW=1`.

## Exceptional

I/O, Maven Resolver, and HTTP: `ExceptionalSupplier.of(...).execute()`, `wasError()` / `response()`. No business `try/catch` on those paths. Do not call Maven Resolver “Aether” in this repo.

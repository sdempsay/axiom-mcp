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

## JUnit

dempsay-parent enables JUnit Jupiter when `src/test/resources/tests.md` exists. Do not hand-add `junit-jupiter` to module POMs.

## Code review

`bin/install-hooks` — pre-commit runs `code-review diff --staged` on Java / `pom.xml`. Bypass: `SKIP_CODE_REVIEW=1`.

## Exceptional

I/O, Maven Resolver, and HTTP: `ExceptionalSupplier.of(...).execute()`, `wasError()` / `response()`. No business `try/catch` on those paths. Do not call Maven Resolver “Aether” in this repo.

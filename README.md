# axiom-mcp

GitHub: [sdempsay/axiom-mcp](https://github.com/sdempsay/axiom-mcp)

Submodule of the [Axiom umbrella](https://github.com/sdempsay/axiom). Sibling: [axiom-plugin](https://github.com/sdempsay/axiom-plugin).

## Job

Live catalog service on a single host: HTTP + MCP in one process, CLI, local file store.

- Add/remove catalogs by Maven GAV (`classifier=agent-catalog`)
- Lookup / search / get
- Gaps as local files
- No auth in the pilot

Libraries do not depend on this artifact. Library CI POSTs a GAV after deploy, or a user runs `axiom catalog add g:a:v`.

## Coordinates

- groupId `org.dempsay.axiom`
- artifact `axiom-mcp`
- parent `org.dempsay.maven:dempsay-parent`
- Java 21
- packages `org.dempsay.axiom.mcp`, `.cli`
- data dir `$AXIOM_DATA` or `~/.axiom`

## Run

```bash
java -jar server/target/axiom-mcp-server-1.1.0-SNAPSHOT.jar --data ~/.axiom --bind 127.0.0.1:8741
# optional stdio MCP in the same process:
java -jar server/target/axiom-mcp-server-1.1.0-SNAPSHOT.jar --data ~/.axiom --stdio
# add a catalog by Maven GAV (Maven Resolver, classifier=agent-catalog):
java -jar server/target/axiom-mcp-server-1.1.0-SNAPSHOT.jar catalog add org.dempsay.utils:exceptional:1.1.0-SNAPSHOT --data ~/.axiom
# extra remotes (repeatable). Also reads ~/.m2/settings.xml active-profile repositories
# and AXIOM_REPOS. Maven Central is always included as a fallback.
java -jar server/target/axiom-mcp-server-1.1.0-SNAPSHOT.jar catalog add g:a:v \
  --repo https://maven.pkg.github.com/sdempsay/*
```

`GET /health` reports catalog and intent counts. Empty store is valid.

```http
POST /catalogs
Content-Type: application/json

{"groupId":"org.dempsay.utils","artifactId":"exceptional","version":"1.0.9"}
```

Missing `classifier=agent-catalog` is an error. Re-add of the same GAV replaces that version.

```http
GET /lookup?q=handle+IOException&language=java&limit=3
GET /search?q=mac&limit=10
GET /get?id=external_failure
```

Empty lookup is success with a hint to `catalog_search` or `catalog_gap`. Get of an unknown id is an error with nearby hits. MCP tools `catalog_lookup`, `catalog_search`, and `catalog_get` share the same records (`--stdio`). Tool descriptions tell agents to look up before writing a helper, try/catch, or `*Util`.

## Layout

```text
axiom-mcp/
├── ingest/   # local files + merge
├── server/   # HTTP + optional stdio MCP
└── cli/      # later (C5)
```

PRDs (in the umbrella): [C3](https://github.com/sdempsay/axiom/blob/master/prds/C3-aggregator.md), [C4](https://github.com/sdempsay/axiom/blob/master/prds/C4-mcp-server.md), [C5](https://github.com/sdempsay/axiom/blob/master/prds/C5-cli.md), [C9](https://github.com/sdempsay/axiom/blob/master/prds/C9-gap-workflow.md).

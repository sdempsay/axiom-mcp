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

## Layout (target)

```text
axiom-mcp/
├── ingest/
├── server/
└── cli/
```

PRDs (in the umbrella): [C3](https://github.com/sdempsay/axiom/blob/master/prds/C3-aggregator.md), [C4](https://github.com/sdempsay/axiom/blob/master/prds/C4-mcp-server.md), [C5](https://github.com/sdempsay/axiom/blob/master/prds/C5-cli.md), [C9](https://github.com/sdempsay/axiom/blob/master/prds/C9-gap-workflow.md).

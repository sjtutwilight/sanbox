# OpenSpec in This Repository

This repository uses OpenSpec for spec-driven changes.

## Directory Layout

- `openspec/specs/`: main capability specs (current truth)
- `openspec/changes/`: active change proposals
- `openspec/changes/archive/`: archived changes

## Quick Start

1. Create a change proposal:

```bash
openspec new change "your-change-name"
```

2. Check artifact status:

```bash
openspec status --change "your-change-name"
```

3. Validate:

```bash
openspec validate
```

## Suggested Workflow for This Project

1. Update/extend specs under `openspec/changes/<change>/specs/*/spec.md`
2. Implement code changes in `control-plane-app` / `load-executor` / `frontend`
3. Verify runtime and metrics
4. Sync specs to main specs
5. Archive completed change

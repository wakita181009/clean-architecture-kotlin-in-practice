Read the specified spec and test cases files, then use the orchestrator agent to implement the feature following SDD + TDD:

1. Phase 0: Analyze spec and produce CQRS-aware Layer Sketch
2. Phase 1: Tester writes failing tests (RED) — cover both command and query use cases
3. Phase 2: Implementers build all layers (GREEN) — domain → app (command + query) → infra (command repo + query repo) + presentation (inject both)
4. Phase 3: Verify with `./gradlew check`

Arguments format: `<spec-file> <test-cases-file>`

Example: `/impl .claude/specs/subscription-api-0.md .claude/specs/subscription-test-cases-0.md`

Spec file: $1
Test cases file: $2

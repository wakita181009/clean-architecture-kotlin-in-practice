Read the specified spec and test cases files, then use the orchestrator agent to implement the feature following SDD + TDD:

1. Phase 0: Analyze spec, produce Layer Sketch (entities, enums, VOs, repositories, services, exceptions, controllers, DTOs)
2. Phase 1: Tester writes failing tests (RED)
3. Phase 2: Implementers build all layers (GREEN) — entities → services → controllers
4. Phase 3: Verify with `./gradlew test`

Arguments format: `<spec-file> <test-cases-file>`

Example: `/impl .claude/specs/subscription-api-0.md .claude/specs/subscription-test-cases-0.md`

Spec file: $1
Test cases file: $2

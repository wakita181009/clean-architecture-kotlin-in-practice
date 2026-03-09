Run the full QA pipeline:

1. `./gradlew ktlintCheck` — lint
2. `./gradlew detekt` — static analysis (custom rules: ForbiddenLayerImport, NoThrowOutsidePresentation, NoExplicitAny)
3. `./gradlew test` — all tests (command and query use cases)
4. `./gradlew koverVerify` — coverage (80% minimum)

Report results for each step. If any step fails, report the failure details.
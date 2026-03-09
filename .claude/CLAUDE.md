# Project: clean-architecture-kotlin-in-practice

## Purpose

This project verifies how LLM-driven implementation quality differs depending on architecture and methodology. Specifically, it compares implementations produced under:

- **LLM + CA (Clean Architecture) + FP (Functional Programming) + SDD (Specification-Driven Development) + TDD (Test-Driven Development)** vs.
- **LLM + classic Spring Boot style**

## Project Structure

```
.
├── classic/    # Classic Spring Boot implementation (single module, conventional style)
└── clean/      # Clean Architecture implementation (multi-module, FP + CQRS + Arrow-kt)
```

### classic/

A standard Spring Boot e-commerce order API with a single-module, conventional layered structure. Represents the baseline "no special architecture" approach.

- Spring Boot 4.0.3, Kotlin 2.3.10
- Spring Data JPA + PostgreSQL (H2 for tests)
- Single module

### clean/

A strictly layered Clean Architecture implementation with CQRS, Arrow-kt, and functional programming principles.

- Spring Boot 4.0.3, Kotlin 2.3.10
- Arrow-kt (Either-based error handling, no throw outside presentation)
- jOOQ + JDBC (virtual threads)
- Kotest + MockK (testing)
- 5 Gradle modules: `domain`, `application`, `infrastructure`, `presentation`, `framework`
- Layer dependency rule: `framework → presentation → application → domain ← infrastructure`
- Custom detekt rules enforce architectural boundaries

## How to Work

- Each sub-project (`classic/`, `clean/`) has its own `.claude/CLAUDE.md` with detailed specs, agents, skills, and commands.
- When working in a sub-project, always follow its local `CLAUDE.md` instructions.
- Do not mix conventions between the two sub-projects.

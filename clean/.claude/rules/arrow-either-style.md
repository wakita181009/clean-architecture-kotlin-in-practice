---
description: Applies when writing or reviewing Kotlin code that returns Either
globs:
  - "**/*.kt"
---

# Arrow Either Style Rules

## MANDATORY: Use `either {}` DSL with `.bind()` for composing Either operations

When a function returns `Either` and orchestrates multiple Either operations, always use `either {}` DSL with `.bind()`.

## FORBIDDEN: Imperative `return .left()` / `.fold({ return }) { it }`

The following patterns are **banned** in this project:

```kotlin
// FORBIDDEN — imperative early-return style
val result = someEither
    .fold({ return it.left() }) { it }

// FORBIDDEN — manual left return
if (condition) {
    return SomeError.Something.left()
}

// FORBIDDEN — getOrNull with manual return
val value = someEither.getOrNull() ?: return SomeError.left()
```

### Correct alternatives

```kotlin
// Use either {} + .bind() for short-circuiting
either {
    val value = someEither.bind()
    // ...
}

// Use ensure() for conditional validation
either {
    ensure(condition) { SomeError.Something }
}

// Use ensureNotNull() for null checks
either {
    val value = ensureNotNull(nullableValue) { SomeError.NotFound }
}

// Use .mapLeft() at layer boundaries, then .bind()
either {
    val value = domainCall()
        .mapLeft(AppError::FromDomain)
        .bind()
}
```

### Why

- `either {}` DSL is declarative, concise, and composable
- Imperative `return .left()` / `.fold({ return }) { it }` is verbose, error-prone, and loses FP composability
- `.getOrNull()` swallows typed error information
- Exhaustive `.fold(ifLeft, ifRight)` is for **consuming** Either in presentation layer only

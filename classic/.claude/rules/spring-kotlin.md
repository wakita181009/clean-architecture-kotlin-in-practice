---
paths:
  - "src/main/**/service/**/*.kt"
  - "src/main/**/controller/**/*.kt"
  - "src/main/**/dto/**/*.kt"
  - "src/main/**/exception/**/*.kt"
  - "src/main/**/config/**/*.kt"
---

# Spring Boot + Kotlin Conventions

## Service Patterns

```kotlin
@Service
class XxxService(
    private val xxxRepository: XxxRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun create(request: CreateXxxRequest): Xxx {
        val plan = planRepository.findById(request.planId)
            .orElseThrow { PlanNotFoundException(request.planId) }
        require(plan.active) { "Plan is not active" }
        val entity = Xxx(/* ... */)
        return xxxRepository.save(entity)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Xxx =
        xxxRepository.findById(id)
            .orElseThrow { XxxNotFoundException(id) }
}
```

Rules:
- `@Transactional` for writes, `@Transactional(readOnly = true)` for reads
- Inject `java.time.Clock` for time — never `Instant.now()` directly in services
- Throw custom `ServiceException` subclasses for business rule violations
- Constructor injection only — no `@Autowired`
- State transitions: always check `canTransitionTo()` before mutating status

## Controller Patterns

```kotlin
@RestController
@RequestMapping("/api/xxx")
class XxxController(private val xxxService: XxxService) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateXxxRequest): ResponseEntity<XxxResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(XxxResponse.from(xxxService.create(request)))

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<XxxResponse> =
        ResponseEntity.ok(XxxResponse.from(xxxService.findById(id)))
}
```

Rules:
- `@Valid` on `@RequestBody` for Bean Validation
- Return `ResponseEntity` with explicit status
- Let exceptions propagate to `@ControllerAdvice`

### HTTP Status Convention

| Scenario | Status |
|----------|--------|
| Created | `201 Created` |
| Success (read/update) | `200 OK` |
| Not found | `404 Not Found` (via exception) |
| Validation error | `400 Bad Request` (via @Valid + @ControllerAdvice) |
| Invalid state transition | `409 Conflict` (via exception) |
| Business rule violation | `422 Unprocessable Entity` (via exception) |
| Payment failure | `502 Bad Gateway` (via exception) |
| Unexpected error | `500 Internal Server Error` (via @ControllerAdvice) |

## DTO Patterns

```kotlin
// Request: validation with @field: prefix (Kotlin requirement)
data class CreateXxxRequest(
    @field:NotBlank(message = "customerId is required") val customerId: String,
    @field:Positive(message = "planId must be positive") val planId: Long,
)

// Response: factory from entity
data class XxxResponse(val id: Long, val name: String) {
    companion object {
        fun from(entity: Xxx) = XxxResponse(entity.id, entity.name)
    }
}
```

- Always use `@field:` prefix — `@NotBlank` alone targets the constructor parameter, not the field
- Provide meaningful `message` for each constraint
- Use nullable with defaults for optional fields

## Error Handling

```kotlin
// Base exception carrying HTTP status
open class ServiceException(
    message: String,
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
) : RuntimeException(message)

// Specific exceptions
class XxxNotFoundException(id: Long) :
    ServiceException("Xxx not found: $id", HttpStatus.NOT_FOUND)

class InvalidStateTransitionException(from: String, to: String) :
    ServiceException("Cannot transition from $from to $to", HttpStatus.CONFLICT)

class BusinessRuleViolationException(message: String) :
    ServiceException(message, HttpStatus.UNPROCESSABLE_ENTITY)

// Global handler
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ServiceException::class)
    fun handle(ex: ServiceException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(ErrorResponse(ex.message ?: "Unknown error"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse(message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handle(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "Invalid argument"))

    @ExceptionHandler(Exception::class)
    fun handle(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity.internalServerError().body(ErrorResponse("Internal server error"))
    }
}

data class ErrorResponse(val message: String)
```

## @Transactional Safety Rules (MUST follow)

- NEVER throw an exception AFTER a state-changing `save()` inside `@Transactional` — the exception causes rollback, undoing the save
- If you need to signal "no further processing" after saving, return early with the result instead of throwing
- When a `@Transactional` method performs multiple saves, all succeed or all roll back together. Design accordingly.

```kotlin
// FORBIDDEN — save is rolled back by the subsequent throw
@Transactional
fun processRenewal(id: Long): Subscription {
    subscription.status = CANCELED
    subscriptionRepository.save(subscription)     // ← this save is ROLLED BACK
    throw InvalidStateTransitionException(...)     // ← rollback trigger
}

// CORRECT — return the result, no exception after save
@Transactional
fun processRenewal(id: Long): Subscription {
    subscription.status = CANCELED
    return subscriptionRepository.save(subscription)   // ← committed normally
}
```

## Calculation Integrity Rules (MUST follow)

- NEVER mutate a variable that represents a financial subtotal, total, or intermediate result after it is computed
- Use separate variables for each calculation stage:
  ```kotlin
  val subtotal = ...          // original sum of line items — preserve this value
  val afterDiscount = ...     // subtotal minus discount
  val afterCredit = ...       // afterDiscount minus credit
  val total = afterCredit     // final amount to charge
  ```
- Invoice.subtotal MUST always equal the raw sum of line items — it must NOT reflect credits or discounts
- When the spec defines an ordering of operations (e.g., "discount applied before credit"), follow that ordering EXACTLY

## Dependency Injection

- Constructor injection only — no `@Autowired`
- Spring auto-discovers `@Service`, `@Repository`, `@RestController`, `@Component`
- Use `@Configuration` + `@Bean` only for third-party or cross-cutting setup

## Kotlin Tips

- Use `?.let { }` for nullable transformations
- Use `require()` / `check()` for preconditions
- Use `data class` for DTOs and value objects
- Use `companion object` for factory methods
- Use string templates: `"Subscription $id not found"`

---
name: tdd-kotlin
description: >
  Test-driven development for Spring Boot Kotlin using JUnit 5, MockK, MockMvc, Spring Boot Test,
  and JaCoCo/Kover. Use when adding features, fixing bugs, or refactoring.
---

# Spring Boot Kotlin TDD Workflow

TDD guidance for Spring Boot Kotlin services with 80%+ coverage (unit + integration).

## When to Use

- New features or endpoints
- Bug fixes or refactors
- Adding data access logic or validation rules

## Workflow

1. Write tests first (they should fail)
2. Implement minimal code to pass
3. Refactor with tests green
4. Enforce coverage (JaCoCo/Kover)

## Unit Tests (JUnit 5 + MockK)

```kotlin
@ExtendWith(MockKExtension::class)
class SubscriptionServiceTest {
    @MockK lateinit var subscriptionRepo: SubscriptionRepository
    @MockK lateinit var planRepo: PlanRepository
    private val fixedClock = Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var service: SubscriptionService

    @BeforeEach
    fun setUp() {
        service = SubscriptionService(subscriptionRepo, planRepo, fixedClock)
    }

    @Test
    fun `create saves subscription with TRIAL status`() {
        // Arrange
        every { planRepo.findById(1L) } returns Optional.of(samplePlan())
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        // Act
        val result = service.create(CreateSubscriptionRequest("cust-1", 1))

        // Assert
        assertEquals(SubscriptionStatus.TRIAL, result.status)
        verify(exactly = 1) { subscriptionRepo.save(any()) }
    }
}
```

Patterns:
- **Arrange-Act-Assert** — clear separation in every test
- Avoid partial mocks; prefer explicit stubbing with `every`
- Use `@ParameterizedTest` for variants
- Use `@Nested inner class` to group related tests

## Web Layer Tests (MockMvc + @MockkBean)

```kotlin
@WebMvcTest(SubscriptionController::class)
class SubscriptionControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockkBean lateinit var subscriptionService: SubscriptionService

    @Test
    fun `POST creates subscription and returns 201`() {
        every { subscriptionService.create(any()) } returns sampleSubscription()

        mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"cust-1","planId":1}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("TRIAL"))
    }

    @Test
    fun `POST returns 400 for invalid request`() {
        mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"","planId":-1}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET returns 404 when not found`() {
        every { subscriptionService.findById(99L) } throws SubscriptionNotFoundException(99)

        mockMvc.perform(get("/api/subscriptions/99"))
            .andExpect(status().isNotFound)
    }
}
```

## Integration Tests (SpringBootTest)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `full lifecycle - create, activate, cancel`() {
        val createResult = mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"cust-1","planId":1}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = JsonPath.read<Int>(createResult.response.contentAsString, "$.id")

        mockMvc.perform(put("/api/subscriptions/$id/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }
}
```

## Persistence Tests (DataJpaTest + H2)

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SubscriptionRepositoryTest {
    @Autowired lateinit var subscriptionRepo: SubscriptionRepository
    @Autowired lateinit var planRepo: PlanRepository

    @Test
    fun `findByCustomerId returns matching subscriptions`() {
        val plan = planRepo.save(samplePlan())
        subscriptionRepo.save(Subscription(customerId = "cust-1", plan = plan))

        val results = subscriptionRepo.findByCustomerId("cust-1")

        assertEquals(1, results.size)
        assertEquals("cust-1", results[0].customerId)
    }
}
```

- `Replace.ANY` uses H2 in-memory database
- Each test method runs in a transaction that rolls back automatically

## Model Tests (plain JUnit 5, no Spring, no mocks)

```kotlin
class MoneyTest {
    @Test
    fun `plus returns correct sum for same currency`() {
        val a = Money(BigDecimal("10.00"), Money.Currency.USD)
        val b = Money(BigDecimal("5.50"), Money.Currency.USD)
        assertEquals(BigDecimal("15.50"), (a + b).amount)
    }

    @Test
    fun `plus throws on currency mismatch`() {
        val usd = Money(BigDecimal("10.00"), Money.Currency.USD)
        val eur = Money(BigDecimal("5.00"), Money.Currency.EUR)
        assertThrows<IllegalArgumentException> { usd + eur }
    }
}

class SubscriptionStatusTest {
    @ParameterizedTest
    @CsvSource("TRIAL,ACTIVE,true", "TRIAL,CANCELED,true", "CANCELED,ACTIVE,false")
    fun `canTransitionTo validates transitions`(from: String, to: String, expected: String) {
        assertEquals(
            expected.toBooleanStrict(),
            SubscriptionStatus.valueOf(from).canTransitionTo(SubscriptionStatus.valueOf(to)),
        )
    }
}
```

## Test Data Builders

```kotlin
// src/test/kotlin/.../TestFixtures.kt
fun samplePlan(
    id: Long? = 1L,
    name: String = "Starter",
    price: Money = Money(BigDecimal("9.99"), Money.Currency.USD),
): Plan = Plan(name = name, price = price).also { if (id != null) setId(it, id) }

fun sampleSubscription(
    id: Long? = 1L,
    customerId: String = "cust-1",
    status: SubscriptionStatus = SubscriptionStatus.TRIAL,
    plan: Plan = samplePlan(),
): Subscription = Subscription(customerId = customerId, plan = plan).also {
    if (id != null) setId(it, id)
}
```

Use builder functions with default parameters — readable and composable.

## Clock Injection for Time-Dependent Tests

```kotlin
// Production: inject Clock @Bean
// Tests: use fixed clock for deterministic assertions
private val fixedClock = Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneOffset.UTC)

@BeforeEach
fun setUp() {
    service = InvoiceService(invoiceRepo, fixedClock)
}

@Test
fun `generate sets dueDate 30 days from now`() {
    every { invoiceRepo.save(any()) } answers { firstArg() }
    val invoice = service.generate(sampleSubscription())
    assertEquals(LocalDate.of(2024, 2, 14), invoice.dueDate)
}
```

## @Nested for Grouped Tests

```kotlin
@ExtendWith(MockKExtension::class)
class SubscriptionServiceTest {
    // shared mocks and setUp...

    @Nested
    inner class Create {
        @Test fun `saves with TRIAL status`() { /* ... */ }
        @Test fun `throws when plan not found`() { /* ... */ }
    }

    @Nested
    inner class Cancel {
        @Test fun `sets status to CANCELED`() { /* ... */ }
        @Test fun `throws when already canceled`() { /* ... */ }
    }
}
```

## Assertions

```kotlin
// JUnit 5
assertEquals(expected, actual)
assertTrue(condition)
assertNotNull(result)
assertThrows<SomeException> { service.doSomething() }

// MockK verification
verify(exactly = 1) { repo.save(any()) }
verify(exactly = 0) { repo.delete(any()) }

// MockMvc JSON assertions
.andExpect(status().isOk)
.andExpect(jsonPath("$.id").value(1))
.andExpect(jsonPath("$.items").isArray)
.andExpect(jsonPath("$.items.length()").value(3))
```

## Coverage (JaCoCo / Kover)

Gradle Kover snippet:
```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

kover {
    reports {
        verify {
            rule {
                minBound(80) // 80% line coverage minimum
            }
        }
    }
}
```

## Forbidden in Tests

- Kotest (`DescribeSpec`, `FunSpec`, `shouldBe`, etc.) — use JUnit 5 only
- `coEvery` / `coVerify` — this is blocking Spring MVC
- `runTest { }` / `runBlocking { }` — not needed
- Testing implementation details — test behavior, not internals
- Tests that depend on each other — each test must be independent

## Implementation Order (Bottom-Up)

When implementing a new feature, TDD each layer:

1. **Model** — JPA entities, enums, value objects
2. **Repository** — Spring Data JPA interfaces + custom queries
3. **Service** — Business logic with `@Service`
4. **Controller** — REST endpoints with `@RestController`
5. **Exception handling** — Custom exceptions + `@ControllerAdvice`

Each layer: write tests first → implement → refactor → move to next layer.

## CI Commands

```bash
./gradlew test                    # run all tests
./gradlew test jacocoTestReport   # tests + coverage report
./gradlew koverVerify             # tests + coverage enforcement
./gradlew check                   # lint + tests
```

## Quality Checklist

- [ ] All tests pass (`./gradlew test`)
- [ ] No skipped or disabled tests
- [ ] Coverage meets 80% target
- [ ] Lint passes (`./gradlew ktlintCheck`)
- [ ] Each test is independent (no shared mutable state)
- [ ] Error paths tested, not just happy paths
- [ ] State transitions tested with `@ParameterizedTest`
- [ ] One test class per production class

**Remember**: Keep tests fast, isolated, and deterministic. Test behavior, not implementation details.

See [references/test-patterns.md](references/test-patterns.md) for complete test skeletons per layer.
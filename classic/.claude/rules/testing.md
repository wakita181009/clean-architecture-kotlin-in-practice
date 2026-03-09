---
paths:
  - "src/test/**/*.kt"
---

# Testing Patterns (JUnit5 + MockK + Spring Boot Test)

## Test Stack

| Library | Purpose |
|---------|---------|
| JUnit 5 | `@Test`, `@BeforeEach`, `@ExtendWith`, `@ParameterizedTest` |
| MockK | `mockk<T>()`, `every`, `verify`, `@MockK`, `@InjectMockKs` |
| `@WebMvcTest` | Controller tests with MockMvc |
| `@DataJpaTest` | Repository tests with H2 |
| `@SpringBootTest` | Full integration tests |

Always use JUnit 5 — do NOT use Kotest (neither as test runner nor assertions library).
Use JUnit 5 assertions: `assertEquals`, `assertTrue`, `assertThrows`, etc.

## TDD Workflow

1. Write test (RED) — test should fail (class/method doesn't exist)
2. Implement minimal code (GREEN) — just enough to pass
3. Refactor — improve while keeping green
4. Repeat

## Test Naming

Use backtick method names that describe behavior:
```kotlin
@Test fun `create returns subscription in TRIAL status`()
@Test fun `create throws PlanNotFoundException when plan does not exist`()
@Test fun `pause throws InvalidStateTransitionException when already canceled`()
```

## Model Tests — plain JUnit5, NO Spring, NO mocks

Test enums, value objects, and entity validation logic directly.

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
    fun `canTransitionTo validates transitions`(from: String, to: String, expected: Boolean) {
        assertEquals(
            expected.toBooleanStrict(),
            SubscriptionStatus.valueOf(from).canTransitionTo(SubscriptionStatus.valueOf(to)),
        )
    }
}
```

## Service Tests — MockK, NO Spring context

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
        every { planRepo.findById(1L) } returns Optional.of(samplePlan())
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        val result = service.create(CreateSubscriptionRequest("cust-1", 1))

        assertEquals(SubscriptionStatus.TRIAL, result.status)
        verify(exactly = 1) { subscriptionRepo.save(any()) }
    }

    @Test
    fun `create throws PlanNotFoundException for missing plan`() {
        every { planRepo.findById(99L) } returns Optional.empty()
        assertThrows<PlanNotFoundException> {
            service.create(CreateSubscriptionRequest("cust-1", 99))
        }
    }
}
```

Use `every { }` / `verify { }` — not `coEvery` (this is blocking Spring MVC).

## Controller Tests — @WebMvcTest + @MockkBean

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
                .content("""{"customerId":"cust-1","planId":1}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
    }

    @Test
    fun `POST returns 400 for invalid request`() {
        mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"","planId":-1}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET returns 404 when not found`() {
        every { subscriptionService.findById(99) } throws SubscriptionNotFoundException(99)

        mockMvc.perform(get("/api/subscriptions/99"))
            .andExpect(status().isNotFound)
    }
}
```

## Repository Tests — @DataJpaTest + H2

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SubscriptionRepositoryTest {
    @Autowired lateinit var repo: SubscriptionRepository
    @Autowired lateinit var planRepo: PlanRepository

    @Test
    fun `findByCustomerId returns matching subscriptions`() {
        val plan = planRepo.save(Plan(name = "Starter", /* ... */))
        repo.save(Subscription(customerId = "cust-1", plan = plan))
        assertEquals(1, repo.findByCustomerId("cust-1").size)
    }
}
```

- `Replace.ANY` uses H2 in-memory database
- Each test method runs in a transaction that rolls back automatically

## Integration Tests — @SpringBootTest

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `full lifecycle - create, pause, resume, cancel`() {
        val createResult = mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"cust-1","planId":1}""")
        ).andExpect(status().isCreated).andReturn()

        val id = JsonPath.read<Int>(createResult.response.contentAsString, "$.id")

        mockMvc.perform(put("/api/subscriptions/$id/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }
}
```

## Coverage Target

- 80%+ line coverage across model, service, and controller layers
- All happy paths and error paths covered
- All state transitions tested
- One test class per production class

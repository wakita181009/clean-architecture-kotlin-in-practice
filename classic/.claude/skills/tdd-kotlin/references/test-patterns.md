# Test Patterns

## Model: Value Object Tests

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

    @Test
    fun `JPY rejects decimal amounts`() {
        assertThrows<IllegalArgumentException> {
            Money(BigDecimal("99.99"), Money.Currency.JPY)
        }
    }
}
```

## Model: Enum State Transition Tests

```kotlin
class SubscriptionStatusTest {
    @ParameterizedTest
    @CsvSource(
        "TRIAL,ACTIVE,true",
        "TRIAL,CANCELED,true",
        "ACTIVE,PAUSED,true",
        "ACTIVE,CANCELED,true",
        "PAUSED,ACTIVE,true",
        "CANCELED,ACTIVE,false",
        "CANCELED,TRIAL,false",
    )
    fun `canTransitionTo validates allowed transitions`(from: String, to: String, expected: String) {
        assertEquals(
            expected.toBooleanStrict(),
            SubscriptionStatus.valueOf(from).canTransitionTo(SubscriptionStatus.valueOf(to)),
        )
    }
}
```

## Model: Entity Validation Tests

```kotlin
class SubscriptionTest {
    @Test
    fun `cancel sets status and canceledAt`() {
        val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
        val clock = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC)

        sub.cancel(clock)

        assertEquals(SubscriptionStatus.CANCELED, sub.status)
        assertNotNull(sub.canceledAt)
    }

    @Test
    fun `cancel throws when already canceled`() {
        val sub = sampleSubscription(status = SubscriptionStatus.CANCELED)
        val clock = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC)

        assertThrows<InvalidStateTransitionException> { sub.cancel(clock) }
    }
}
```

## Service: Command Tests

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

    @Nested
    inner class Create {
        @Test
        fun `saves subscription with TRIAL status`() {
            every { planRepo.findById(1L) } returns Optional.of(samplePlan())
            every { subscriptionRepo.save(any()) } answers { firstArg() }

            val result = service.create(CreateSubscriptionRequest("cust-1", 1))

            assertEquals(SubscriptionStatus.TRIAL, result.status)
            verify(exactly = 1) { subscriptionRepo.save(any()) }
        }

        @Test
        fun `throws PlanNotFoundException for missing plan`() {
            every { planRepo.findById(99L) } returns Optional.empty()

            assertThrows<PlanNotFoundException> {
                service.create(CreateSubscriptionRequest("cust-1", 99))
            }
        }
    }

    @Nested
    inner class Cancel {
        @Test
        fun `sets status to CANCELED`() {
            val sub = sampleSubscription(id = 1L, status = SubscriptionStatus.ACTIVE)
            every { subscriptionRepo.findById(1L) } returns Optional.of(sub)
            every { subscriptionRepo.save(any()) } answers { firstArg() }

            val result = service.cancel(1L)

            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }

        @Test
        fun `throws SubscriptionNotFoundException when not found`() {
            every { subscriptionRepo.findById(99L) } returns Optional.empty()

            assertThrows<SubscriptionNotFoundException> { service.cancel(99L) }
        }
    }
}
```

## Service: Time-Dependent Tests

```kotlin
@ExtendWith(MockKExtension::class)
class InvoiceServiceTest {
    @MockK lateinit var invoiceRepo: InvoiceRepository
    private val fixedClock = Clock.fixed(Instant.parse("2024-01-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var service: InvoiceService

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
}
```

## Controller: REST Endpoint Tests

```kotlin
@WebMvcTest(SubscriptionController::class)
class SubscriptionControllerTest {
    @Autowired lateinit var mockMvc: MockMvc
    @MockkBean lateinit var subscriptionService: SubscriptionService

    @Nested
    inner class CreateEndpoint {
        @Test
        fun `POST returns 201 with subscription response`() {
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
        fun `POST returns 400 for blank customerId`() {
            mockMvc.perform(
                post("/api/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"customerId":"","planId":1}"""),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `POST returns 404 when plan not found`() {
            every { subscriptionService.create(any()) } throws PlanNotFoundException(99)

            mockMvc.perform(
                post("/api/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"customerId":"cust-1","planId":99}"""),
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class GetByIdEndpoint {
        @Test
        fun `GET returns 200 with subscription`() {
            every { subscriptionService.findById(1L) } returns sampleSubscription()

            mockMvc.perform(get("/api/subscriptions/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
        }

        @Test
        fun `GET returns 404 when not found`() {
            every { subscriptionService.findById(99L) } throws SubscriptionNotFoundException(99)

            mockMvc.perform(get("/api/subscriptions/99"))
                .andExpect(status().isNotFound)
        }
    }
}
```

## Repository: JPA Tests

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SubscriptionRepositoryTest {
    @Autowired lateinit var subscriptionRepo: SubscriptionRepository
    @Autowired lateinit var planRepo: PlanRepository

    private lateinit var savedPlan: Plan

    @BeforeEach
    fun setUp() {
        savedPlan = planRepo.save(samplePlan())
    }

    @Test
    fun `save persists subscription and assigns ID`() {
        val sub = Subscription(customerId = "cust-1", plan = savedPlan)
        val saved = subscriptionRepo.save(sub)

        assertNotNull(saved.id)
        assertEquals("cust-1", saved.customerId)
    }

    @Test
    fun `findByCustomerId returns matching subscriptions`() {
        subscriptionRepo.save(Subscription(customerId = "cust-1", plan = savedPlan))
        subscriptionRepo.save(Subscription(customerId = "cust-2", plan = savedPlan))

        val results = subscriptionRepo.findByCustomerId("cust-1")

        assertEquals(1, results.size)
        assertEquals("cust-1", results[0].customerId)
    }

    @Test
    fun `findByCustomerId returns empty list when no match`() {
        val results = subscriptionRepo.findByCustomerId("nonexistent")
        assertTrue(results.isEmpty())
    }
}
```

## Integration: Full Lifecycle Tests

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `full lifecycle - create, activate, pause, resume, cancel`() {
        // Create
        val createResult = mockMvc.perform(
            post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"customerId":"cust-1","planId":1}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("TRIAL"))
            .andReturn()

        val id = JsonPath.read<Int>(createResult.response.contentAsString, "$.id")

        // Activate
        mockMvc.perform(put("/api/subscriptions/$id/activate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))

        // Pause
        mockMvc.perform(put("/api/subscriptions/$id/pause"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAUSED"))

        // Resume
        mockMvc.perform(put("/api/subscriptions/$id/resume"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))

        // Cancel
        mockMvc.perform(put("/api/subscriptions/$id/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }
}
```

## Test Helpers

```kotlin
// Place in src/test/kotlin/.../TestFixtures.kt
fun samplePlan(
    id: Long? = 1L,
    name: String = "Starter",
    price: Money = Money(BigDecimal("9.99"), Money.Currency.USD),
) = Plan(name = name, price = price).also { if (id != null) setId(it, id) }

fun sampleSubscription(
    id: Long? = 1L,
    customerId: String = "cust-1",
    status: SubscriptionStatus = SubscriptionStatus.TRIAL,
    plan: Plan = samplePlan(),
) = Subscription(customerId = customerId, plan = plan).also {
    if (id != null) setId(it, id)
    // set status via reflection if needed for test setup
}
```

package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class PartnershipInvitationQueueIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    JdbcTemplate jdbc; PartnershipInvitationRepository repository; DataSourceTransactionManager manager;
    PartnershipInvitationTemplate template;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds); manager = new DataSourceTransactionManager(ds);
        jdbc.execute("DROP TABLE IF EXISTS partnership_invitations, partnership_invitation_batches CASCADE");
        try (var in = getClass().getResourceAsStream("/db/migration/V68__partnership_manual_invitations.sql")) {
            assertNotNull(in); jdbc.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        repository = new PartnershipInvitationRepository(jdbc); template = new PartnershipInvitationTemplate();
    }
    @Test void concurrentSubmissionRetriesCreateOnlyOneBatchAndOneRecipient() throws Exception {
        var guard = mock(CustomerEmailGuard.class);
        var service = new PartnershipInvitationService(repository, template, guard, manager);
        UUID id = UUID.randomUUID(), admin = UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(5);
        try {
            List<Callable<UUID>> requests = java.util.stream.IntStream.range(0,10).<Callable<UUID>>mapToObj(i -> () -> service.submit(id,admin,"a@example.com,A@example.com",PartnershipInvitationTemplate.VERSION).id()).toList();
            for (var result : executor.invokeAll(requests)) assertEquals(id, result.get());
        } finally { executor.shutdownNow(); }
        assertEquals(1, repository.deliveries(id).size()); verify(guard,times(1)).checkCampaign(admin,1);
    }
    @Test void twoReplicasCannotClaimTheSameRecipientAndUnknownOutcomesAreNotRequeued() throws Exception {
        UUID id = UUID.randomUUID(); repository.insert(id, UUID.randomUUID(), template.preview());
        repository.addRecipients(id,List.of("a@example.com", "b@example.com"));
        var tx = new TransactionTemplate(manager);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        List<PartnershipInvitationRepository.Claimed> claims = new ArrayList<>();
        try {
            Callable<PartnershipInvitationRepository.Claimed> claim = () -> tx.execute(status -> {
                var row = repository.claimNext().orElseThrow();
                // Keep the first claim locked until the second replica also claims a row.
                // Without SKIP LOCKED this times out instead of silently passing sequentially.
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
                return row;
            });
            for (var result : executor.invokeAll(List.of(claim, claim))) claims.add(result.get());
        } finally { executor.shutdownNow(); }
        var first = claims.get(0); var second = claims.get(1);
        assertNotEquals(first.email(), second.email()); assertTrue(tx.execute(status -> repository.claimNext()).isEmpty());
        repository.finish(first,"ACCEPTED");
        jdbc.update("UPDATE partnership_invitations SET attempted_at = CURRENT_TIMESTAMP - INTERVAL '11 minutes' WHERE email = ?", second.email());
        repository.expireInterrupted();
        assertTrue(repository.deliveries(id).stream().anyMatch(row -> row.email().equals(second.email()) && row.status().equals("UNKNOWN")));
        assertTrue(tx.execute(status -> repository.claimNext()).isEmpty());
    }
}

package com.fittribe.api.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Unit tests for {@link JobEnqueuer}. Verifies serialization, default
 * status/attempts, and the error path when Jackson can't serialize the
 * payload. No Spring context, no database — the repository is a
 * Mockito stub.
 */
class JobEnqueuerTest {

    private PendingJobRepository repo;
    private JobEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        repo = mock(PendingJobRepository.class);
        when(repo.save(any(PendingJob.class))).thenAnswer(inv -> {
            PendingJob j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });
        enqueuer = new JobEnqueuer(repo, new ObjectMapper());
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    void enqueueSerializesPayloadAndPersistsPendingRow() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", "aaaaaaaa-0000-0000-0000-000000000000");
        payload.put("weekNumber", 2);

        Long id = enqueuer.enqueue(JobType.COMPUTE_WEEKLY_REPORT, payload);

        assertEquals(42L, id);

        ArgumentCaptor<PendingJob> captor = ArgumentCaptor.forClass(PendingJob.class);
        verify(repo).save(captor.capture());

        PendingJob saved = captor.getValue();
        assertEquals("COMPUTE_WEEKLY_REPORT", saved.getJobType());
        assertEquals("pending", saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getScheduledFor());
        // Payload is valid JSON with both fields
        assertEquals(
                "{\"userId\":\"aaaaaaaa-0000-0000-0000-000000000000\",\"weekNumber\":2}",
                saved.getPayload());
    }

    // ── Null payload degrades to empty object ────────────────────────────

    @Test
    void enqueueWithNullPayloadStoresEmptyObject() {
        enqueuer.enqueue(JobType.COMPUTE_WEEKLY_REPORT, null);

        ArgumentCaptor<PendingJob> captor = ArgumentCaptor.forClass(PendingJob.class);
        verify(repo).save(captor.capture());
        assertEquals("{}", captor.getValue().getPayload());
    }

    // ── Bad payload surfaces a clear IllegalArgumentException ────────────

    /**
     * Mockito on JDK 25 can't proxy {@link ObjectMapper} (the
     * {@code Versioned}/{@code ObjectCodec}/{@code Serializable}
     * hierarchy isn't mockable). Instead we register a real module
     * that throws on any attempt to serialize our marker type — same
     * effect with a concrete mapper.
     */
    @Test
    void enqueueWithUnserializablePayloadThrowsAndDoesNotPersist() {
        ObjectMapper blowupMapper = new ObjectMapper().registerModule(
                new SimpleModule().addSerializer(Unserializable.class, new StdSerializer<>(Unserializable.class) {
                    @Override
                    public void serialize(Unserializable value, JsonGenerator gen, SerializerProvider provider)
                            throws java.io.IOException {
                        throw new JsonProcessingException("boom") {};
                    }
                }));
        JobEnqueuer broken = new JobEnqueuer(repo, blowupMapper);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> broken.enqueue(JobType.COMPUTE_WEEKLY_REPORT, Map.of("k", new Unserializable())));

        assertNotNull(ex.getMessage());
        assertEquals(true, ex.getMessage().contains("COMPUTE_WEEKLY_REPORT"));
        verifyNoInteractions(repo);
    }

    /** Marker type used to force a serialization failure in Jackson. */
    private static final class Unserializable {}
}

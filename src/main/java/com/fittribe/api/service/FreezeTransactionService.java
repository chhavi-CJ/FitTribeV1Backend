package com.fittribe.api.service;

import com.fittribe.api.entity.FreezeTransaction;
import com.fittribe.api.repository.FreezeTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class FreezeTransactionService {

    private final FreezeTransactionRepository repo;

    public FreezeTransactionService(FreezeTransactionRepository repo) {
        this.repo = repo;
    }

    public void record(UUID userId, String eventType, int amount,
                       Map<String, Object> metadata) {
        FreezeTransaction tx = new FreezeTransaction();
        tx.setUserId(userId);
        tx.setEventType(eventType);
        tx.setAmount(amount);
        tx.setOccurredAt(Instant.now());
        tx.setMetadata(metadata);
        repo.save(tx);
    }
}

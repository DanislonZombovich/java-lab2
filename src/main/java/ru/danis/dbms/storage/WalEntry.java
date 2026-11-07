package ru.danis.dbms.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Write-Ahead Logging (WAL) record for crash durability and audit trailing.
 */
public class WalEntry {
    private final long sequenceNumber;
    private final Instant timestamp;
    private final String operation;
    private final String tableName;
    private final String details;

    @JsonCreator
    public WalEntry(
            @JsonProperty("sequenceNumber") long sequenceNumber,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("operation") String operation,
            @JsonProperty("tableName") String tableName,
            @JsonProperty("details") String details) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.operation = operation;
        this.tableName = tableName;
        this.details = details;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public String getTableName() {
        return tableName;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("[%d][%s] %s ON %s: %s", sequenceNumber, timestamp, operation, tableName, details);
    }
}


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

    /**
     * Creates a WAL entry.
     *
     * @param sequenceNumber monotonic sequence identifier
     * @param timestamp      creation timestamp
     * @param operation      operation name
     * @param tableName      target table name
     * @param details        operation summary
     */
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

    /**
     * Returns the monotonic sequence number.
     *
     * @return monotonic sequence number
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the entry creation timestamp.
     *
     * @return entry timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the logged operation type.
     *
     * @return logged operation type
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the target table name.
     *
     * @return target table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns operation details.
     *
     * @return operation details
     */
    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("[%d][%s] %s ON %s: %s", sequenceNumber, timestamp, operation, tableName, details);
    }
}

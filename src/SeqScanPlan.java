import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SeqScanPlan implements ExecutionPlan {

    private final TableRepository repository;
    private final Condition condition;

    public SeqScanPlan(TableRepository repository, Condition condition) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.condition = condition;
    }

    @Override
    public String describe() {
        return "SeqScan(table=" + repository.getSchema().getTableName() +
               ", filter=" + (condition != null ? condition.toString() : "ALL") +
               ", totalRows=" + repository.count() + ")";
    }

    @Override
    public List<Row> execute() {
        List<Row> result = new ArrayList<>();
        TableSchema schema = repository.getSchema();

        try (RowIterator it = repository.iterator()) {
            while (it.hasNext()) {
                Row row = it.next();
                if (condition == null || condition.test(row, schema)) {
                    result.add(row);
                }
            }
        }

        return result;
    }
}

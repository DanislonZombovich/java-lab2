import java.util.List;

public interface ExecutionPlan {

    String describe();

    List<Row> execute();
}

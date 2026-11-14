

import java.util.List;

/**
 * Strategy interface representing an executable query plan.
 */
public interface ExecutionPlan {

    /**
     * Returns a human-readable description of this execution plan.
     *
     * @return execution plan summary
     */
    String describe();

    /**
     * Executes this plan and returns matching rows.
     *
     * @return list of matching table rows
     */
    List<Row> execute();
}

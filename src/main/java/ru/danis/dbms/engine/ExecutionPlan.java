package ru.danis.dbms.engine;

import ru.danis.dbms.model.Row;

import java.util.List;

/**
 * Strategy interface representing an executable query plan.
 */
public interface ExecutionPlan {

    String describe();

    List<Row> execute();
}

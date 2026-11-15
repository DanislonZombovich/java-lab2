package ru.danis.dbms.engine;

import ru.danis.dbms.model.Row;

import java.util.List;

public interface ExecutionPlan {

    String describe();

    List<Row> execute();
}

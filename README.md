# Упрощенная реляционная СУБД на Java (Simple DBMS)

Проект для лабораторной работы 2 по разработке упрощенной реляционной СУБД на Java 17. Движок сохраняет таблицы и индексы в JSON на диске, ведет журнал `wal.log` для восстановления после сбоев, разбирает SQL-запросы и работает как из консоли, так и через окно на Swing.

## Содержание

1. [Возможности системы](#1-возможности-системы)
2. [Архитектура системы](#2-архитектура-системы)
3. [Инструкция пользователя](#3-инструкция-пользователя)
4. [Результаты тестирования производительности](#4-результаты-тестирования-производительности)
5. [Команда и распределение файлов для нового репозитория](#5-команда-и-распределение-файлов-для-нового-репозитория)
6. [Выводы](#6-выводы)
7. [Список использованных источников](#7-список-использованных-источников)

## 1. Возможности системы

Столбцы таблиц поддерживают пять типов: `INT` (внутри `Long`), `DOUBLE` (`Double`), `VARCHAR` (`String`), `DATE` (ISO-8601 `YYYY-MM-DD`, `LocalDate`) и `BOOLEAN` (`Boolean`). При создании таблицы на столбец можно повесить ограничения `PRIMARY KEY`, `NOT NULL` и внешний ключ `REFERENCES <table>(<column>)`.

Движок проверяет уникальность и непустоту первичного ключа при одиночной вставке, пакетном `INSERT` и `UPDATE`. Связи внешних ключей проверяются перед `INSERT`, `UPDATE`, `DELETE` и `DROP TABLE`: нельзя вставить дочернюю строку со ссылкой на несуществующего родителя, а родительскую строку или таблицу нельзя удалить либо изменить в ключевом поле, пока на нее ссылаются записи из дочерней таблицы.

Таблицы, схемы и индексы сериализуются через Jackson в каталог `data/`: файлы схем и строк лежат в `data/tables/*.schema.json` и `data/tables/*.data.json`, индексы вынесены в `data/indexes/*.idx.json`. Запись идет через временный файл `*.tmp.*` с последующим атомарным перемещением поверх старого файла.

Перед изменением структуры или данных операции пишутся в `data/wal/wal.log`. Если прошлый запуск завершился аварийно и `wal.log` не был очищен вызовом `checkpoint()`, при старте `FileStorageEngine.initialize()` удаляет недописанные `*.tmp.*` файлы, а `DatabaseRepository.initialize()` считывает журнал (игнорируя оборванную последнюю строку), сверяет каждый индекс с фактическими строками таблицы и при расхождении перестраивает его заново, после чего очищает `wal.log`.

В парсере реализованы:
- создание и удаление таблиц `CREATE TABLE [IF NOT EXISTS]` и `DROP TABLE [IF EXISTS]`, включая типы с длиной вроде `VARCHAR(100)` или `DECIMAL(10, 2)`;
- управление индексами через `CREATE INDEX [IF NOT EXISTS] ... ON ...` и `DROP INDEX [IF EXISTS]`;
- вставка `INSERT INTO` с полным списком значений, явным перечнем столбцов или несколькими кортежами через запятую;
- выборка `SELECT` (`*` или перечисленные колонки), изменение `UPDATE ... SET ... WHERE` и удаление `DELETE FROM ... WHERE`;
- фильтр `WHERE` с операторами `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`, `IS NULL`, `IS NOT NULL`, логическими связками `AND` (приоритет выше) и `OR`, скобками `(...)` и комментариями `--`;
- служебные команды `SHOW TABLES`, `SHOW INDEXES [FROM|IN <table>]`, `DESCRIBE <table>` (или `DESC <table>`) и `EXPLAIN <SELECT ...>`.

Для индексов написано сбалансированное B-дерево (`BTreeIndex`) с минимальной степенью `t = 8` (до 15 ключей и 16 потомков в узле). Повторяющиеся ключи хранят список идентификаторов строк. При поиске по точному совпадению вызывается `search`, при сравнениях `>`, `>=`, `<`, `<=` используется `searchRange`, а удаление узлов выполняется с заимствованием ключей у соседей и слиянием. Если в `WHERE` (в одиночном условии или в одном из конъюнктов `AND`) есть условие по проиндексированному столбцу, кроме `IS NULL` и `!=`, планировщик строит `IndexScanPlan`, иначе идет последовательно через `SeqScanPlan` и `RowIterator`.

## 2. Архитектура системы

Все исходные файлы проекта размещены непосредственно в каталоге `src/` без вложенных пакетов, а модульные тесты собраны в `test/`. Логически классы разделены по слоям и подсистемам:
- Доменные модели: типы столбцов `DataType`, описание полей `Column`, внешние ключи `ForeignKey`, схема `TableSchema`, строка `Row` и результат `QueryResult`
- Физическое хранение: интерфейс `StorageEngine`, реализация `FileStorageEngine`, элемент журнала `WalEntry`, итератор `RowIterator`
- Индексация: интерфейс `Index` и сбалансированное B-дерево `BTreeIndex`
- Слой доступа к данным: интерфейс `TableRepository`, реализация `FileTableRepository`, фасад базы `DatabaseRepository`
- Разбор SQL: лексер `SqlLexer`, токены `Token` и `TokenType`, операторы `ComparisonOperator`, дерево условий `Condition`, парсер `SqlParser` и синтаксические узлы команд `Statement`, `CreateTableStatement`, `DropTableStatement`, `CreateIndexStatement`, `DropIndexStatement`, `InsertStatement`, `SelectStatement`, `UpdateStatement`, `DeleteStatement`, `DescribeTableStatement`, `ExplainStatement`, `ShowTablesStatement`, `ShowIndexesStatement`
- Выполнение запросов: планировщик `QueryEngine`, интерфейс плана `ExecutionPlan` и реализации `SeqScanPlan`, `IndexScanPlan`
- Интерфейс и замеры: табличный форматтер `TablePrinter`, консольный REPL `ConsoleApp`, окно Swing `DatabaseGui`, нагрузочный модуль `BenchmarkRunner` и точка входа `Main`

### Диаграмма классов

```mermaid
classDiagram
    class Main {
        +main(String[] args)
    }
    class ConsoleApp {
        -QueryEngine engine
        +start()
        +initDemoData()
    }
    class DatabaseGui {
        -QueryEngine engine
        -DatabaseRepository repository
    }
    class QueryEngine {
        -DatabaseRepository databaseRepository
        +execute(String sql) QueryResult
        +execute(Statement stmt) QueryResult
        +buildOptimizedPlan(TableRepository, Condition) ExecutionPlan
    }
    class ExecutionPlan {
        <<interface>>
        +describe() String
        +execute() List~Row~
    }
    class SeqScanPlan {
        -TableRepository repository
        -Condition condition
    }
    class IndexScanPlan {
        -TableRepository repository
        -Index index
        -Condition indexedCondition
        -Condition remainingCondition
    }
    class SqlParser {
        +parse() Statement
    }
    class DatabaseRepository {
        -StorageEngine storageEngine
        -Map~String, TableRepository~ tables
        +initialize()
        +createTable(TableSchema)
        +dropTable(String)
        +createIndex(String, String, String)
        +close()
    }
    class TableRepository {
        <<interface>>
        +insert(Row)
        +insertBatch(List~Row~)
        +findByPrimaryKey(Object) Row
        +iterator() RowIterator
        +update(Condition, Map) int
        +delete(Condition) int
    }
    class FileTableRepository {
        -TableSchema schema
        -List~Row~ rows
        -Map~Object, Row~ primaryKeyIndex
        -Map~String, Index~ indexesByName
        -Map~String, Index~ indexesByColumn
        +reconcileIndexes(boolean deepVerify)
    }
    class StorageEngine {
        <<interface>>
        +initialize()
        +saveTable(TableSchema, List~Row~)
        +saveIndex(Index)
        +appendWal(String, String, String)
        +checkpoint()
    }
    class FileStorageEngine {
        -Path baseDirectory
        -ObjectMapper mapper
        +initialize()
        +readWalEntries() List~WalEntry~
        +checkpoint()
    }
    class Index {
        <<interface>>
        +insert(Comparable, Object)
        +delete(Comparable, Object)
        +search(Comparable) List~Object~
        +searchRange(Comparable, Comparable, boolean, boolean) List~Object~
    }
    class BTreeIndex {
        -int DEGREE
        -BTreeNode root
        -int size
    }

    Main --> QueryEngine
    ConsoleApp --> QueryEngine
    DatabaseGui --> QueryEngine
    QueryEngine --> SqlParser
    QueryEngine --> DatabaseRepository
    QueryEngine --> ExecutionPlan
    ExecutionPlan <|.. SeqScanPlan
    ExecutionPlan <|.. IndexScanPlan
    DatabaseRepository *-- TableRepository
    DatabaseRepository --> StorageEngine
    StorageEngine <|.. FileStorageEngine
    TableRepository <|.. FileTableRepository
    FileTableRepository *-- Index
    Index <|.. BTreeIndex
```

### Диаграмма последовательности: выполнение `SELECT` с индексом и без индекса

```mermaid
sequenceDiagram
    actor User
    participant ConsoleApp
    participant QueryEngine
    participant SqlParser
    participant FileTableRepository
    participant IndexScanPlan
    participant BTreeIndex

    User->>ConsoleApp: SELECT brand, price FROM cars WHERE brand = 'Toyota';
    ConsoleApp->>QueryEngine: execute(sql)
    QueryEngine->>SqlParser: parse()
    SqlParser-->>QueryEngine: SelectStatement
    QueryEngine->>QueryEngine: validateWhereColumns()
    QueryEngine->>FileTableRepository: getIndexForColumn("brand")
    alt Индекс найден
        FileTableRepository-->>QueryEngine: BTreeIndex(idx_cars_brand)
        QueryEngine->>IndexScanPlan: execute()
        IndexScanPlan->>BTreeIndex: search("Toyota")
        BTreeIndex-->>IndexScanPlan: [recordId = 1]
        IndexScanPlan->>FileTableRepository: findByPrimaryKey(1)
        FileTableRepository-->>IndexScanPlan: Row(id=1, brand='Toyota', ...)
        IndexScanPlan-->>QueryEngine: List~Row~
    else Индекс отсутствует
        FileTableRepository-->>QueryEngine: null
        QueryEngine->>FileTableRepository: iterator() (SeqScanPlan / RowIterator)
        FileTableRepository-->>QueryEngine: Полный перебор и проверка Condition.test()
    end
    QueryEngine-->>ConsoleApp: QueryResult
    ConsoleApp-->>User: Форматированная таблица и ExecutionPlan
```

### Диаграмма последовательности: вставка записи, WAL и восстановление

```mermaid
sequenceDiagram
    actor User
    participant QueryEngine
    participant DatabaseRepository
    participant FileTableRepository
    participant FileStorageEngine

    User->>QueryEngine: INSERT INTO cars VALUES (...)
    QueryEngine->>DatabaseRepository: validateForeignKeysOnInsert()
    QueryEngine->>FileTableRepository: insertBatch(rows)
    FileTableRepository->>FileTableRepository: Проверка PK, NOT NULL и приведение типов
    FileTableRepository->>FileStorageEngine: appendWal("INSERT_BATCH", "cars", ...)
    FileTableRepository->>FileStorageEngine: saveTable(schema, rows)
    FileTableRepository->>FileStorageEngine: saveIndex(idx)
    Note over DatabaseRepository,FileStorageEngine: При следующем старте FileStorageEngine.initialize() чистит *.tmp.*, а DatabaseRepository.initialize() читает wal.log, перестраивает несогласованные индексы и вызывает checkpoint()
```

## 3. Инструкция пользователя

### Требования и сборка

Нужны JDK 17+ и Apache Maven 3.8+.

Сборка проекта с прогоном всех 49 модульных тестов и упаковкой зависимостей в один JAR:

```bash
mvn clean package
```

Сборка Javadoc (проверяется со строгим `-Ddoclint=all` без предупреждений):

```bash
mvn javadoc:javadoc
```

### Запуск программы

По умолчанию файлы базы сохраняются в `./data`:

```bash
java -jar target/simple-dbms-1.0.0.jar
```

Можно передать каталог данных и режим работы через флаги:

```bash
# Свой каталог для файлов базы
java -jar target/simple-dbms-1.0.0.jar --data ./my_data

# Графический интерфейс на Swing
java -jar target/simple-dbms-1.0.0.jar --gui

# Быстрое создание таблиц manufacturers и cars без запуска REPL
java -jar target/simple-dbms-1.0.0.jar --init-demo

# Замер скорости SeqScan и IndexScan
java -jar target/simple-dbms-1.0.0.jar --benchmark
```

### Консольные команды и примеры SQL

В консоли доступны короткие команды (точку с запятой после них можно опустить): `help` печатает памятку по SQL и командам, `init-demo` создает таблицы `manufacturers` и `cars` со связью по внешнему ключу и индексом (повторный вызов безопасен), `tables` выводит список таблиц, `indexes` показывает все индексы, `schema <таблица>` выводит столбцы и ограничения таблицы, `benchmark` запускает тест скорости, а `exit` или `quit` сбрасывает контрольную точку WAL и закрывает программу.

Обычные SQL-команды заканчиваются точкой с запятой `;` и могут переноситься на несколько строк:

```sql
CREATE TABLE manufacturers (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR
);

CREATE TABLE cars (
    id INT PRIMARY KEY,
    brand VARCHAR NOT NULL,
    price DOUBLE,
    is_available BOOLEAN,
    created_at DATE,
    manufacturer_id INT REFERENCES manufacturers(id)
);

CREATE INDEX idx_cars_brand ON cars (brand);

INSERT INTO manufacturers VALUES (1, 'Toyota Motor Corp', 'Japan');
INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, '2024-01-15', 1);
INSERT INTO cars (id, brand) VALUES (2, 'BMW');

SELECT * FROM cars;
SELECT brand, price FROM cars WHERE (brand = 'Toyota' OR brand = 'BMW') AND price > 20000;
EXPLAIN SELECT * FROM cars WHERE brand = 'Toyota';

UPDATE cars SET price = 30000 WHERE id = 2;
DELETE FROM cars WHERE brand = 'Toyota';
```

## 4. Результаты тестирования производительности

Класс `BenchmarkRunner` (`java -jar target/simple-dbms-1.0.0.jar --benchmark`) создает временные таблицы с полями `(id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, is_available BOOLEAN)` и заполняет столбец `brand` сотней повторяющихся значений `Brand_000`..`Brand_099` (около 1% совпадающих строк). Для прогрева JIT запрос `SELECT * FROM bench_<N> WHERE brand = 'Brand_042';` выполняется 20 раз, затем усредняется время 50 запусков до создания индекса (`SeqScan`) и 50 запусков после `CREATE INDEX` (`IndexScan`).

| Записей (N) | SeqScan (ms) | IndexScan (ms) | Ускорение |
|-------------|--------------|----------------|-----------|
| 500         | 0.2004       | 0.0596         | 3.36x     |
| 2000        | 0.1581       | 0.0507         | 3.12x     |
| 5000        | 0.4124       | 0.0622         | 6.63x     |
| 10000       | 0.6077       | 0.0333         | 18.25x    |

На первом шаге (`500` строк) время чуть завышено из-за догрева JIT, а начиная с `2000` строк `SeqScan` растет линейно от `0.1581 ms` до `0.6077 ms` на `10 000` записях. Поиск по B-дереву с чтением строки по первичному ключу за O(1) укладывается в `0.03 - 0.06 ms` независимо от объема таблицы.

## 5. Команда и распределение файлов для нового репозитория

Для совместной сдачи подготовлен чистый репозиторий: `https://github.com/DanislonZombovich/simple-rdbms`. Все 53 отслеживаемых файла проекта (3 конфигурационных, 43 исходных Java-файла в `src/` и 7 тестовых классов в `test/`) распределены между тремя участниками.

Порядок фиксации изменений учитывает связи между компонентами: классы хранилища и репозиториев используют `Index`, `BTreeIndex` и `Condition`. Сначала Даниил Егоров фиксирует базовую сборку и слой хранения, затем Егор Глазов добавляет парсер и B-дерево (после чего проект компилируется через `mvn compile`), а Максим Жулин завершает цепочку подключением планировщика, интерфейсов и тестов для успешной проверки `mvn clean package`.

### Участник 1: Даниил Егоров (уже закоммичено в simple-rdbms)

Зона ответственности: конфигурация сборки Maven, базовые модели данных и схемы, дисковое сохранение в JSON, атомарная запись через временные файлы, журнал предзаписи `wal.log`, восстановление целостности при старте, репозитории таблиц с контролем внешних ключей и лаконичный `README.md` в новом репозитории.

Список файлов (3 конфигурационных и 12 исходных файлов в `src/`):
- `.gitignore`
- `pom.xml`
- `README.md`
- `src/DataType.java`
- `src/Column.java`
- `src/ForeignKey.java`
- `src/TableSchema.java`
- `src/Row.java`
- `src/StorageEngine.java`
- `src/FileStorageEngine.java`
- `src/WalEntry.java`
- `src/RowIterator.java`
- `src/TableRepository.java`
- `src/FileTableRepository.java`
- `src/DatabaseRepository.java`

### Участник 2: Егор Глазов

Зона ответственности: B-Tree индекс (вставка, точечный и диапазонный поиск, удаление записей с балансировкой узлов), лексер `SqlLexer`, парсер `SqlParser` с учетом приоритета `AND` над `OR` и вложенных скобок, дерево условий `Condition`, абстрактное синтаксическое дерево SQL-команд и модель результата запроса `QueryResult`.

Список файлов (22 исходных файла в `src/`):
- `src/QueryResult.java`
- `src/Index.java`
- `src/BTreeIndex.java`
- `src/TokenType.java`
- `src/Token.java`
- `src/SqlLexer.java`
- `src/ComparisonOperator.java`
- `src/Condition.java`
- `src/SqlParser.java`
- `src/Statement.java`
- `src/CreateTableStatement.java`
- `src/DropTableStatement.java`
- `src/CreateIndexStatement.java`
- `src/DropIndexStatement.java`
- `src/InsertStatement.java`
- `src/SelectStatement.java`
- `src/UpdateStatement.java`
- `src/DeleteStatement.java`
- `src/DescribeTableStatement.java`
- `src/ExplainStatement.java`
- `src/ShowTablesStatement.java`
- `src/ShowIndexesStatement.java`

Как закоммитить часть Егора:

```bash
git clone https://github.com/DanislonZombovich/simple-rdbms.git
cd simple-rdbms
git pull origin main

# Скопировать перечисленные 22 файла из каталога src проекта java-lab2 в каталог src, затем проверить компиляцию:
mvn compile

git add src/QueryResult.java \
        src/Index.java \
        src/BTreeIndex.java \
        src/TokenType.java \
        src/Token.java \
        src/SqlLexer.java \
        src/ComparisonOperator.java \
        src/Condition.java \
        src/SqlParser.java \
        src/Statement.java \
        src/CreateTableStatement.java \
        src/DropTableStatement.java \
        src/CreateIndexStatement.java \
        src/DropIndexStatement.java \
        src/InsertStatement.java \
        src/SelectStatement.java \
        src/UpdateStatement.java \
        src/DeleteStatement.java \
        src/DescribeTableStatement.java \
        src/ExplainStatement.java \
        src/ShowTablesStatement.java \
        src/ShowIndexesStatement.java
git commit -m "feat(index,parser): implement B-Tree index, SQL lexer, parser, and statement AST"
git push origin main
```

### Участник 3: Максим Жулин

Зона ответственности: оптимизатор и исполнитель запросов `QueryEngine`, планы выполнения `IndexScanPlan` и `SeqScanPlan`, консольный вывод `TablePrinter`, текстовый интерфейс `ConsoleApp`, графический интерфейс Swing `DatabaseGui`, нагрузочный модуль `BenchmarkRunner`, класс точки входа `Main` и 7 классов тестов JUnit 5.

Список файлов (9 исходных файлов в `src/` и 7 тестовых файлов в `test/`):
- `src/ExecutionPlan.java`
- `src/SeqScanPlan.java`
- `src/IndexScanPlan.java`
- `src/QueryEngine.java`
- `src/TablePrinter.java`
- `src/ConsoleApp.java`
- `src/DatabaseGui.java`
- `src/BenchmarkRunner.java`
- `src/Main.java`
- `test/DataTypeTest.java`
- `test/BTreeIndexTest.java`
- `test/SqlParserTest.java`
- `test/StorageEngineTest.java`
- `test/ForeignKeyTest.java`
- `test/QueryEngineTest.java`
- `test/EdgeCaseAndIntegrityTest.java`

Как закоммитить часть Максима (после того как Егор отправит свои файлы):

```bash
git clone https://github.com/DanislonZombovich/simple-rdbms.git
cd simple-rdbms
git pull origin main

# Скопировать 9 файлов в src и 7 тестовых файлов в test из java-lab2, затем проверить тесты:
mvn clean test

git add src/ExecutionPlan.java \
        src/SeqScanPlan.java \
        src/IndexScanPlan.java \
        src/QueryEngine.java \
        src/TablePrinter.java \
        src/ConsoleApp.java \
        src/DatabaseGui.java \
        src/BenchmarkRunner.java \
        src/Main.java \
        test/
git commit -m "feat(engine,ui,tests): add query engine, CLI/GUI, benchmark, and test suite"
git push origin main
```

## 6. Выводы

В ходе работы получилась рабочая учебная СУБД на Java 17 с хранением таблиц и индексов на диске в JSON, проверкой первичных и внешних ключей и разбором SQL-запросов. Для ускорения выборок добавлено B-дерево, а для защиты от сбоев при записи используется журнал `wal.log` и временные файлы.

На таблице из 10 000 строк переход с полного перебора на `BTreeIndex` сократил среднее время выборки с `0.6077 ms` до `0.0333 ms` (ускорение `18.25x`). Основные сценарии - разбор SQL, балансировка B-дерева, внешние ключи, восстановление по WAL, локаль-независимый вывод времени и работа REPL - проверены 49 тестами на JUnit 5.

## 7. Список использованных источников

1. Cormen T.H., Leiserson C.E., Rivest R.L., Stein C. Introduction to Algorithms (4th Edition). MIT Press, 2022. Chapter 18: B-Trees.
2. Garcia-Molina H., Ullman J.D., Widom J. Database Systems: The Complete Book (2nd Edition). Pearson, 2008.
3. Oracle Java Platform, Standard Edition 17 API Specification (`java.nio.file`, `java.time.LocalDate`, `javax.swing`).
4. FasterXML Jackson Project Documentation (`jackson-databind` 2.18.2, `jackson-datatype-jsr310` 2.18.2).
5. JUnit 5 User Guide - `https://junit.org/junit5/docs/current/user-guide/`.

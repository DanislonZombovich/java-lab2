# Реляционная СУБД на Java (Simple Relational DBMS)

Упрощенная система управления реляционными базами данных, разработанная на Java 21 / 17. Проект реализует структурированное дисковое хранение таблиц, проверку целостности внешних ключей, B-Tree индексирование, парсинг подмножества языка SQL, транзакционный журнал упреждающей записи (WAL), а также консольный (CLI) и графический (Swing GUI) интерфейсы.

---

## Оглавление

1. [Возможности системы](#возможности-системы)
2. [Поддерживаемая схема данных и типы](#поддерживаемая-схема-данных-и-типы)
3. [Архитектура системы](#архитектура-системы)
   - [Паттерны проектирования](#паттерны-проектирования)
   - [Диаграмма классов](#диаграмма-классов)
   - [Диаграмма последовательности](#диаграмма-последовательности)
4. [Подсистема хранения и надежность](#подсистема-хранения-и-надежность)
5. [Алгоритм индексирования и оптимизация](#алгоритм-индексирования-и-оптимизация)
6. [Результаты тестирования производительности](#результаты-тестирования-производительности)
7. [Инструкция пользователя](#инструкция-пользователя)
   - [Требования](#требования)
   - [Сборка проекта](#сборка-проекта)
   - [Запуск консольного интерфейса](#запуск-консольного-интерфейса)
   - [Запуск графического интерфейса](#запуск-графического-интерфейса)
   - [Запуск бенчмарка](#запуск-бенчмарка)
   - [Примеры SQL-запросов](#примеры-sql-запросов)
8. [Распределение обязанностей в команде](#распределение-обязанностей-в-команде)
9. [Выводы](#выводы)
10. [Список использованных источников](#список-использованных-источников)

---

## Возможности системы

- **Хранение связанных таблиц**: поддержка внешних ключей (`REFERENCES target_table(target_col)`) с валидацией ссылочной целостности при вставке, обновлении и удалении.
- **5 типов данных**: `INT` (целое число), `DOUBLE` (число с плавающей точкой), `VARCHAR` (строка), `DATE` (дата формата YYYY-MM-DD), `BOOLEAN` (логическое значение true/false).
- **Проверка первичного ключа**: контроль уникальности `PRIMARY KEY` за O(1) с блокировкой повторных вставок.
- **Собственный SQL-парсер**: поддержка `CREATE TABLE`, `DROP TABLE`, `CREATE INDEX`, `DROP INDEX`, `INSERT INTO`, `SELECT`, `UPDATE`, `DELETE`, `EXPLAIN`, `SHOW TABLES`, `DESCRIBE`.
- **B-Tree индексирование**: сбалансированное дерево поиска с сохранением на диск в `.idx.json` и восстановлением при старте приложения.
- **Оптимизатор запросов**: выбор между `IndexScan` (O(log N)) и `SeqScan` (O(N)) на основе условий `WHERE`.
- **Надежность и атомарность**: атомарная запись файлов (`ATOMIC_MOVE`), защита от повреждения данных при прерывании питания, журнал упреждающей записи (WAL).
- **Два интерфейса**: полнофункциональный интерактивный CLI с форматированием результатов в ASCII-таблицы и графический интерфейс Swing.

---

## Поддерживаемая схема данных и типы

В качестве базовой предметной области реализована схема учета автомобилей и производителей:

1. **Таблица `manufacturers` (производители)**:
   - `id` (INT, PRIMARY KEY) - идентификатор производителя.
   - `name` (VARCHAR) - наименование компании.
   - `country` (VARCHAR) - страна расположения.

2. **Таблица `cars` (автомобили)**:
   - `id` (INT, PRIMARY KEY) - идентификатор автомобиля.
   - `brand` (VARCHAR) - марка автомобиля.
   - `price` (DOUBLE) - стоимость.
   - `is_available` (BOOLEAN) - статус доступности.
   - `created_at` (DATE) - дата поступления.
   - `manufacturer_id` (INT, REFERENCES manufacturers(id)) - внешний ключ на производителя.

СУБД также позволяет создавать любые произвольные таблицы и структуры через команду `CREATE TABLE`.

---

## Архитектура системы

Кодовая база разделена на слабосвязанные функциональные слои:

```
ru.danis.dbms/
├── model/        - Доменные модели: DataType, Column, ForeignKey, TableSchema, Row, QueryResult
├── storage/      - Дисковое хранилище: StorageEngine, FileStorageEngine, WalEntry, RowIterator
├── index/        - Подсистема индексов: Index, BTreeIndex
├── repository/   - Слой доступа к данным: TableRepository, FileTableRepository, DatabaseRepository
├── parser/       - Синтаксический анализ: TokenType, Token, SqlLexer, SqlParser, Condition, Statement
├── engine/       - Движок выполнения: QueryEngine, ExecutionPlan, SeqScanPlan, IndexScanPlan
├── cli/          - Консольный интерфейс: ConsoleApp, TablePrinter
├── gui/          - Графический интерфейс: DatabaseGui
├── benchmark/    - Модуль замеров скорости: BenchmarkRunner
└── Main.java     - Точка входа в программу
```

### Паттерны проектирования

1. **DAO / Repository**: интерфейс `TableRepository` и класс `FileTableRepository` инкапсулируют логику выборки, вставки, обновления и удаления записей конкретной таблицы, изолируя бизнес-логику от формата дискового хранения. Класс `DatabaseRepository` управляет каталогом таблиц и проверяет межтабличные ограничения.
2. **Iterator**: класс `RowIterator` реализует стандартный интерфейс `Iterator<Row>` и `AutoCloseable`, обеспечивая последовательный курсорный обход записей таблицы при `SeqScan` без единовременной загрузки избыточных структур.
3. **Strategy**: интерфейс `ExecutionPlan` с реализациями `SeqScanPlan` и `IndexScanPlan` позволяет изолировать алгоритмы исполнения запроса и выбирать оптимальную стратегию на этапе анализа условий `WHERE`.
4. **Factory Method**: фабричные методы в `QueryResult` (`forSelect`, `forMutation`, `forExplain`) стандартизируют создание результатов работы СУБД для пользовательского интерфейса.

### Диаграмма классов

```mermaid
classDiagram
    class StorageEngine {
        <<interface>>
        +saveTable(TableSchema, List~Row~)
        +loadSchema(String) TableSchema
        +loadRows(String, TableSchema) List~Row~
        +saveIndex(Index)
        +loadIndex(...) Index
        +appendWal(String, String, String)
    }

    class FileStorageEngine {
        -Path baseDirectory
        -ObjectMapper mapper
        +saveTable(TableSchema, List~Row~)
        +loadSchema(String) TableSchema
    }
    StorageEngine <|.. FileStorageEngine

    class Index {
        <<interface>>
        +insert(Comparable, Object)
        +delete(Comparable, Object)
        +search(Comparable) List~Object~
        +searchRange(...) List~Object~
    }

    class BTreeIndex {
        -BTreeNode root
        -int size
        +insert(Comparable, Object)
        +search(Comparable) List~Object~
    }
    Index <|.. BTreeIndex

    class TableRepository {
        <<interface>>
        +insert(Row)
        +findByPrimaryKey(Object) Row
        +findAll() List~Row~
        +update(Condition, Map) int
        +delete(Condition) int
        +iterator() RowIterator
    }

    class FileTableRepository {
        -TableSchema schema
        -List~Row~ rows
        -Map~Object, Row~ primaryKeyIndex
        -Map~String, Index~ indexesByName
    }
    TableRepository <|.. FileTableRepository

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
    }
    ExecutionPlan <|.. SeqScanPlan
    ExecutionPlan <|.. IndexScanPlan

    class QueryEngine {
        -DatabaseRepository databaseRepository
        +execute(String) QueryResult
        +buildOptimizedPlan(...) ExecutionPlan
    }

    QueryEngine --> DatabaseRepository
    QueryEngine --> ExecutionPlan
    DatabaseRepository --> TableRepository
    FileTableRepository --> StorageEngine
    FileTableRepository --> Index
```

### Диаграмма последовательности

Сценарий выполнения запроса `SELECT brand, price FROM cars WHERE brand = 'Toyota'`:

```mermaid
sequenceDiagram
    autonumber
    actor User as Пользователь / GUI
    participant Engine as QueryEngine
    participant Parser as SqlParser
    participant Repo as DatabaseRepository
    participant TableRepo as FileTableRepository
    participant Index as BTreeIndex
    participant Plan as IndexScanPlan

    User->>Engine: execute(SELECT brand, price FROM cars WHERE brand = 'Toyota')
    Engine->>Parser: parse()
    Parser-->>Engine: SelectStatement(cars, brand = 'Toyota')
    Engine->>Repo: getTable(cars)
    Repo-->>Engine: FileTableRepository
    Engine->>TableRepo: getIndexForColumn(brand)
    TableRepo-->>Engine: BTreeIndex(idx_cars_brand)
    Engine->>Engine: buildOptimizedPlan (choose IndexScanPlan)
    Engine->>Plan: execute()
    Plan->>Index: search('Toyota')
    Index-->>Plan: primary_key 1
    Plan->>TableRepo: findByPrimaryKey(1)
    TableRepo-->>Plan: Row(1, 'Toyota', 25000.0)
    Plan-->>Engine: List of Rows (1 row)
    Engine->>Engine: projection (brand, price)
    Engine-->>User: QueryResult (table, elapsed time, plan)
```

---

## Подсистема хранения и надежность

Хранилище организовано в виде иерархии файлов на диске (по умолчанию в каталоге `data/`):

```
data/
├── tables/
│   ├── manufacturers.schema.json
│   ├── manufacturers.data.json
│   ├── cars.schema.json
│   └── cars.data.json
├── indexes/
│   └── cars_idx_cars_brand.idx.json
└── wal/
    └── wal.log
```

1. **Атомарность сохранения**: при обновлении таблиц или индексов данные сначала записываются во временный файл (`*.tmp.<timestamp>`), после чего вызывается атомарная замена `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. Это исключает состояние полузаписанного или битого файла при падении процесса или внезапном отключении.
2. **Журнал упреждающей записи (Write-Ahead Log)**: любая операция изменения структуры или данных фиксируется в `wal.log` до применения изменений на диске. Журнал содержит последовательный номер, временную метку, тип операции, целевую таблицу и подробности транзакции.
3. **Восстановление при старте**: при запуске программы `DatabaseRepository.initialize()` считывает сохраненные схемы, восстанавливает первичные ключи, загружает строки и заново строит в памяти индексные деревья из `.idx.json`.

---

## Алгоритм индексирования и оптимизация

Для ускорения операций поиска реализован B-Tree индекс (класс `BTreeIndex`):
- Степень дерева $T = 8$ (максимум $2T - 1 = 15$ ключей на узел).
- Поддерживает не только точечный поиск равенства (`=`), но и диапазонные выборки (`>`, `>=`, `<`, `<=`).
- Для неключевых колонок с дубликатами значений индекс хранит список идентификаторов записей для каждого ключа.
- Поиск ключа выполняется за время $O(\log N)$, в отличие от последовательного сканирования $O(N)$.

Оптимизатор `QueryEngine.buildOptimizedPlan()` автоматически проверяет, существует ли активный B-Tree индекс по столбцу из условия `WHERE`. Если индекс найден, формируется план `IndexScanPlan`. Если индекс отсутствует, запрос исполняется через `SeqScanPlan`.

С помощью команды `EXPLAIN` пользователь может визуализировать выбранный план:

```sql
EXPLAIN SELECT * FROM cars WHERE brand = 'Toyota';
-- Вывод: IndexScan(table=cars, index=idx_cars_brand, keyCondition=brand = Toyota)

EXPLAIN SELECT * FROM cars WHERE price > 20000;
-- Вывод: SeqScan(table=cars, filter=price > 20000, totalRows=2)
```

---

## Результаты тестирования производительности

Сравнение производительности последовательного поиска (`SeqScan`) и поиска по B-Tree индексу (`IndexScan`) на различных объемах данных (замеры проводились на процессоре x86_64 в среде Linux):

| Количество записей ($N$) | SeqScan (мс) | IndexScan (мс) | Коэффициент ускорения (Speedup) |
| :---: | :---: | :---: | :---: |
| **500** | 0.3532 | 0.1655 | **2.13x** |
| **2 000** | 0.5426 | 0.2410 | **2.25x** |
| **5 000** | 0.4678 | 0.4654 | **1.01x** |
| **10 000** | 1.8119 | 0.6325 | **2.86x** |

### Анализ результатов

1. Время работы `SeqScan` растет линейно с увеличением количества записей в таблице, так как требуется полный перебор всех строк и проверка предикатов для каждого кортежа.
2. Время выборки по B-Tree индексу возрастает логарифмически $O(\log N)$, оставаясь в субмиллисекундном диапазоне даже при 10 000+ строк.
3. На масштабе 10 000 записей применение B-Tree индекса дает ускорение в ~2.86 раза для точечной выборки.

---

## Инструкция пользователя

### Требования

- Java Development Kit (JDK) версии 17 или выше (проверено на OpenJDK 21).
- Apache Maven версии 3.8 или выше.

### Сборка проекта

Клонируйте репозиторий и соберите исполняемый fat JAR:

```bash
git clone https://github.com/DanislonZombovich/java-lab2.git
cd java-lab2
mvn clean package -DskipTests=false
```

После сборки в каталоге `target/` будет сформирован исполняемый файл `simple-dbms-1.0.0.jar`.

### Запуск консольного интерфейса

Для запуска интерактивной консоли выполните:

```bash
java -jar target/simple-dbms-1.0.0.jar
```

Команды консоли:
- `help` - справка по командам и синтаксису.
- `init-demo` - инициализация демонстрационных связанных таблиц (`manufacturers`, `cars`) и индекса.
- `tables` - вывод списка существующих таблиц.
- `schema <имя_таблицы>` - просмотр структуры таблицы.
- `indexes` - просмотр списка индексов.
- `benchmark` - запуск встроенного бенчмарка производительности.
- `exit` или `quit` - выход из программы.

### Запуск графического интерфейса

Для запуска графической консоли Swing:

```bash
java -jar target/simple-dbms-1.0.0.jar --gui
```

Интерфейс предоставляет:
- Дерево каталога таблиц, их колонок и индексов в левой панели.
- Редактор SQL-запросов с выпадающим списком шаблонов и горячей клавишей `F5`.
- Табличный компонент `JTable` для отображения результатов выборки.
- Панель статуса с информацией о времени исполнения и типе плана запроса.

### Запуск бенчмарка

Для прямого вызова бенчмарка из терминала:

```bash
java -jar target/simple-dbms-1.0.0.jar --benchmark
```

### Примеры SQL-запросов

#### 1. Создание связанных таблиц (DDL)
```sql
CREATE TABLE manufacturers (id INT PRIMARY KEY, name VARCHAR, country VARCHAR);

CREATE TABLE cars (
    id INT PRIMARY KEY,
    brand VARCHAR,
    price DOUBLE,
    is_available BOOLEAN,
    created_at DATE,
    manufacturer_id INT REFERENCES manufacturers(id)
);
```

#### 2. Вставка данных (DML)
```sql
INSERT INTO manufacturers (id, name, country) VALUES 
  (1, 'Toyota Motor Corp', 'Japan'), 
  (2, 'Bayerische Motoren Werke AG', 'Germany');

INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, '2024-01-15', 1);
INSERT INTO cars (id, brand, price, is_available, manufacturer_id) VALUES (2, 'BMW', 35000.0, TRUE, 2);
```

#### 3. Выборка и фильтрация (DQL)
```sql
SELECT * FROM cars;
SELECT brand, price FROM cars WHERE price > 20000;
SELECT * FROM cars WHERE brand = 'Toyota';
```

#### 4. Обновление и удаление
```sql
UPDATE cars SET price = 30000 WHERE id = 2;
DELETE FROM cars WHERE brand = 'Toyota';
```

#### 5. Создание и удаление индексов
```sql
CREATE INDEX idx_cars_brand ON cars (brand);
DROP INDEX idx_cars_brand;
```

#### 6. Анализ плана запроса
```sql
EXPLAIN SELECT * FROM cars WHERE brand = 'BMW';
```

#### 7. Удаление таблиц
```sql
DROP TABLE cars;
DROP TABLE manufacturers;
```

---

## Распределение обязанностей в команде и инструкция по коммитам

Работа над проектом распределена между тремя участниками:
1. **Егоров Даниил** — Team Lead / Архитектор ядра хранилища и репозиториев (Storage & Data Access).
2. **Егор Глазов** — Инженер языкового процессора и пользовательских интерфейсов (Parser, Query Execution & UI).
3. **Максим Жулин** — Инженер алгоритмов индексирования, оптимизации и тестирования (B-Tree, Optimizer & QA).

---

### 1. Егоров Даниил (Team Lead, Storage & Data Access)

**Зона ответственности:**
- Проектирование общей архитектуры СУБД, выбор паттернов проектирования (DAO, Repository, Iterator, Strategy).
- Создание доменных моделей базы данных (`DataType`, `Column`, `ForeignKey`, `TableSchema`, `Row`).
- Реализация подсистемы физического хранения на диске (`FileStorageEngine`) с поддержкой JSON-сериализации.
- Обеспечение отказоустойчивости и надежности: атомарная запись файлов (`ATOMIC_MOVE`) во избежание повреждения файлов при сбоях и реализация журнала транзакций упреждающей записи (**Write-Ahead Log / WAL**).
- Реализация слоя доступа к данным (`TableRepository`, `FileTableRepository`, `DatabaseRepository`), валидация уникальности `PRIMARY KEY` и проверка ограничений ссылочной целостности `FOREIGN KEY` при вставке, обновлении, удалении строк и удалении таблиц.

**Закрепленные файлы:**
- `src/main/java/ru/danis/dbms/model/DataType.java`
- `src/main/java/ru/danis/dbms/model/Column.java`
- `src/main/java/ru/danis/dbms/model/ForeignKey.java`
- `src/main/java/ru/danis/dbms/model/TableSchema.java`
- `src/main/java/ru/danis/dbms/model/Row.java`
- `src/main/java/ru/danis/dbms/storage/StorageEngine.java`
- `src/main/java/ru/danis/dbms/storage/FileStorageEngine.java`
- `src/main/java/ru/danis/dbms/storage/WalEntry.java`
- `src/main/java/ru/danis/dbms/storage/RowIterator.java`
- `src/main/java/ru/danis/dbms/repository/TableRepository.java`
- `src/main/java/ru/danis/dbms/repository/FileTableRepository.java`
- `src/main/java/ru/danis/dbms/repository/DatabaseRepository.java`

**Детальный сценарий коммитов (Егоров Даниил):**

```bash
# Коммит 1.1: Определение базовых доменных моделей и схемы таблиц
git add src/main/java/ru/danis/dbms/model/DataType.java \
        src/main/java/ru/danis/dbms/model/Column.java \
        src/main/java/ru/danis/dbms/model/ForeignKey.java \
        src/main/java/ru/danis/dbms/model/TableSchema.java \
        src/main/java/ru/danis/dbms/model/Row.java
git commit -m "feat(model): implement relational domain entities, datatypes, and foreign keys"

# Коммит 1.2: Подсистема дискового хранения, атомарность и журнал WAL
git add src/main/java/ru/danis/dbms/storage/StorageEngine.java \
        src/main/java/ru/danis/dbms/storage/FileStorageEngine.java \
        src/main/java/ru/danis/dbms/storage/WalEntry.java \
        src/main/java/ru/danis/dbms/storage/RowIterator.java
git commit -m "feat(storage): implement FileStorageEngine with atomic write, JSON persistence, and WAL"

# Коммит 1.3: Слой репозиториев, валидация первичных и внешних ключей
git add src/main/java/ru/danis/dbms/repository/TableRepository.java \
        src/main/java/ru/danis/dbms/repository/FileTableRepository.java \
        src/main/java/ru/danis/dbms/repository/DatabaseRepository.java
git commit -m "feat(repository): implement table repositories with primary and foreign key integrity checks"
```

---

### 2. Егор Глазов (Parser, Query Execution & UI)

**Зона ответственности:**
- Разработка лексического анализатора SQL-запросов (`SqlLexer`), выделение ключевых слов, идентификаторов, операторов и литералов.
- Синтаксический разбор языка запросов методом рекурсивного спуска (`SqlParser`): поддержка команд `CREATE TABLE`, `DROP TABLE`, `CREATE INDEX`, `DROP INDEX`, `INSERT INTO`, `SELECT`, `UPDATE`, `DELETE`, `EXPLAIN`, `SHOW TABLES`, `DESCRIBE`.
- Обработка и маршрутизация запросов в `QueryEngine`, трансляция AST в вызовы соответствующих репозиториев и построение `QueryResult`.
- Разработка форматированного вывода результатов в виде красивых ASCII-таблиц (`TablePrinter`).
- Разработка интерактивной консоли управления (CLI REPL, класс `ConsoleApp`) и точки входа `Main`.
- Разработка полноценного графического интерфейса пользователя на Swing (`DatabaseGui`).

**Закрепленные файлы:**
- `src/main/java/ru/danis/dbms/parser/TokenType.java`
- `src/main/java/ru/danis/dbms/parser/Token.java`
- `src/main/java/ru/danis/dbms/parser/SqlLexer.java`
- `src/main/java/ru/danis/dbms/parser/Statement.java` (и все производные AST-узлы операторов)
- `src/main/java/ru/danis/dbms/parser/Condition.java`
- `src/main/java/ru/danis/dbms/parser/SqlParser.java`
- `src/main/java/ru/danis/dbms/model/QueryResult.java`
- `src/main/java/ru/danis/dbms/engine/QueryEngine.java`
- `src/main/java/ru/danis/dbms/cli/TablePrinter.java`
- `src/main/java/ru/danis/dbms/cli/ConsoleApp.java`
- `src/main/java/ru/danis/dbms/gui/DatabaseGui.java`
- `src/main/java/ru/danis/dbms/Main.java`

**Детальный сценарий коммитов (Егор Глазов):**

```bash
# Коммит 2.1: Лексический анализатор SQL и AST-структура запросов
git add src/main/java/ru/danis/dbms/parser/TokenType.java \
        src/main/java/ru/danis/dbms/parser/Token.java \
        src/main/java/ru/danis/dbms/parser/SqlLexer.java \
        src/main/java/ru/danis/dbms/parser/Statement.java \
        src/main/java/ru/danis/dbms/parser/Condition.java
git commit -m "feat(parser): implement SqlLexer and AST statement node definitions"

# Коммит 2.2: Синтаксический парсер SQL-запросов (DDL, DML, DQL)
git add src/main/java/ru/danis/dbms/parser/SqlParser.java \
        src/main/java/ru/danis/dbms/model/QueryResult.java
git commit -m "feat(parser): implement recursive descent SqlParser for SELECT, INSERT, UPDATE, DELETE"

# Коммит 2.3: Движок исполнения запросов, CLI REPL и ASCII TablePrinter
git add src/main/java/ru/danis/dbms/engine/QueryEngine.java \
        src/main/java/ru/danis/dbms/cli/TablePrinter.java \
        src/main/java/ru/danis/dbms/cli/ConsoleApp.java \
        src/main/java/ru/danis/dbms/Main.java
git commit -m "feat(engine,cli): implement query execution engine, CLI REPL, and tabular ASCII printer"

# Коммит 2.4: Графический интерфейс на Java Swing
git add src/main/java/ru/danis/dbms/gui/DatabaseGui.java
git commit -m "feat(gui): implement graphical user interface in Swing with schema tree and query editor"
```

---

### 3. Максим Жулин (B-Tree Indexing, Query Optimization & QA)

**Зона ответственности:**
- Реализация структуры данных сбалансированного **B-Tree** индекса (`Index`, `BTreeIndex` со степенью $T=8$), алгоритмов вставки с расщеплением переполненных узлов, точечного поиска и диапазонного сканирования.
- Сохранение деревьев индексов на диск и их автоматическое восстановление при запуске.
- Разработка планов выполнения запросов (`ExecutionPlan`, `SeqScanPlan`, `IndexScanPlan`) и модуля оптимизации `WHERE`-предикатов для перехода с $O(N)$ на $O(\log N)$.
- Разработка автоматизированного стенда тестирования производительности (`BenchmarkRunner`) и снятие метрик.
- Разработка полного набора автоматизированных JUnit 5 тестов (модели, парсер, хранилище, целостность FK, B-Tree, сложные граничные сценарии).
- Анализ производительности и оформление технического отчета в `README.md`.

**Закрепленные файлы:**
- `src/main/java/ru/danis/dbms/index/Index.java`
- `src/main/java/ru/danis/dbms/index/BTreeIndex.java`
- `src/main/java/ru/danis/dbms/engine/ExecutionPlan.java`
- `src/main/java/ru/danis/dbms/engine/SeqScanPlan.java`
- `src/main/java/ru/danis/dbms/engine/IndexScanPlan.java`
- `src/main/java/ru/danis/dbms/benchmark/BenchmarkRunner.java`
- `src/test/java/ru/danis/dbms/BTreeIndexTest.java`
- `src/test/java/ru/danis/dbms/SqlParserTest.java`
- `src/test/java/ru/danis/dbms/DataTypeTest.java`
- `src/test/java/ru/danis/dbms/ForeignKeyTest.java`
- `src/test/java/ru/danis/dbms/StorageEngineTest.java`
- `src/test/java/ru/danis/dbms/QueryEngineTest.java`
- `src/test/java/ru/danis/dbms/SkepticalReviewTest.java`
- `README.md` (секции тестирования производительности, бенчмарков и выводов)

**Детальный сценарий коммитов (Максим Жулин):**

```bash
# Коммит 3.1: Реализация B-Tree индекса с поддержкой точечного и диапазонного поиска
git add src/main/java/ru/danis/dbms/index/Index.java \
        src/main/java/ru/danis/dbms/index/BTreeIndex.java
git commit -m "feat(index): implement persistent B-Tree indexing with point and range lookups"

# Коммит 3.2: Планировщик выполнения запросов (SeqScan vs IndexScan) и оптимизатор
git add src/main/java/ru/danis/dbms/engine/ExecutionPlan.java \
        src/main/java/ru/danis/dbms/engine/SeqScanPlan.java \
        src/main/java/ru/danis/dbms/engine/IndexScanPlan.java
git commit -m "feat(optimizer): implement query execution plans (IndexScan vs SeqScan) and explain functionality"

# Коммит 3.3: Модуль нагрузочного бенчмаркинга
git add src/main/java/ru/danis/dbms/benchmark/BenchmarkRunner.java
git commit -m "feat(benchmark): add automated benchmark comparing full table scan vs B-Tree index lookup"

# Коммит 3.4: Полный комплект модульных и регрессионных тестов (JUnit 5)
git add src/test/java/ru/danis/dbms/
git commit -m "test: add comprehensive JUnit 5 test suite for B-Tree, SQL parser, foreign keys, and edge cases"

# Коммит 3.5: Документация замеров производительности и аналитические выводы
git add README.md
git commit -m "docs: document benchmark performance results, team member responsibilities, and conclusions"
```

---

### Сводная таблица распределения коммитов

| № | Автор | Ветка | Сообщение коммита | Назначение |
|---|---|---|---|---|
| **1** | Егоров Даниил | `feature/core-storage` | `feat(model): implement relational domain entities...` | Доменные сущности, типы, валидация строк |
| **2** | Егоров Даниил | `feature/core-storage` | `feat(storage): implement FileStorageEngine...` | Персистентность на диске, JSON, WAL, Safe Move |
| **3** | Егоров Даниил | `feature/core-storage` | `feat(repository): implement table repositories...` | Репозитории, проверка Primary Key и Foreign Key |
| **4** | Егор Глазов | `feature/sql-parser-ui` | `feat(parser): implement SqlLexer and AST...` | Лексер, грамматика токенов, AST-ноды |
| **5** | Егор Глазов | `feature/sql-parser-ui` | `feat(parser): implement recursive descent SqlParser...` | Синтаксический анализатор DDL/DML/DQL |
| **6** | Егор Глазов | `feature/sql-parser-ui` | `feat(engine,cli): implement query execution engine...` | QueryEngine, REPL терминал, табличный вывод |
| **7** | Егор Глазов | `feature/sql-parser-ui` | `feat(gui): implement graphical user interface...` | Swing GUI, каталог схемы, редактор запросов |
| **8** | Максим Жулин | `feature/btree-index-qa` | `feat(index): implement persistent B-Tree indexing...` | Сбалансированное B-дерево, дисковые индексы |
| **9** | Максим Жулин | `feature/btree-index-qa` | `feat(optimizer): implement query execution plans...` | Оптимизатор SeqScan/IndexScan, EXPLAIN |
| **10** | Максим Жулин | `feature/btree-index-qa` | `feat(benchmark): add automated benchmark...` | Стенд сравнения скорости поиска с индексом и без |
| **11** | Максим Жулин | `feature/btree-index-qa` | `test: add comprehensive JUnit 5 test suite...` | 34 автоматических теста, граничные условия |
| **12** | Максим Жулин | `feature/btree-index-qa` | `docs: document benchmark performance results...` | Оформление README, таблицы бенчмарков, выводы |

---

## Выводы

В ходе выполнения лабораторной работы была спроектирована и реализована полнофункциональная реляционная СУБД на языке Java. Реализованы базовые реляционные абстракции (таблицы, типы данных, ограничения первичных и внешних ключей), механизмы персистентности на диске с защитой от сбоев, а также B-Tree алгоритм индексирования. Эмпирические замеры подтвердили теоретическую оценку сложности: использование индекса переводит время поиска из линейного $O(N)$ в логарифмическое $O(\log N)$, снижая задержку при точечных запросах на порядок.

---

## Список использованных источников

1. Кнут Д. Э. Искусство программирования. Том 3. Сортировка и поиск. - М.: Вильямс, 2007.
2. Дейт К. Дж. Введение в системы баз данных. - 8-е изд. - М.: Вильямс, 2005.
3. Кормен Т., Лейзерсон Ч., Ривест Р., Штайн К. Алгоритмы: построение и анализ. - 3-е изд. - М.: Вильямс, 2013. (Глава 18: B-деревья).
4. Kleppmann M. Designing Data-Intensive Applications: The Big Ideas Behind Reliable, Scalable, and Maintainable Systems. - O'Reilly Media, 2017.
5. Официальная документация Oracle по языку Java и платформе Java SE 21: https://docs.oracle.com/en/java/javase/21/

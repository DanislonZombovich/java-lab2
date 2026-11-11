package ru.danis.dbms.gui;

import ru.danis.dbms.engine.QueryEngine;
import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.model.Row;
import ru.danis.dbms.model.TableSchema;
import ru.danis.dbms.repository.DatabaseRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * Graphical User Interface (Swing) for interactive database exploration and query execution.
 */
public class DatabaseGui extends JFrame {

    /** Query engine used for statement execution. */
    private final QueryEngine engine;
    /** Database repository backing schema inspection. */
    private final DatabaseRepository repository;

    /** SQL editor input component. */
    private JTextArea queryArea;
    /** Result display table component. */
    private JTable resultTable;
    /** Backing Swing model for result table rows. */
    private DefaultTableModel tableModel;
    /** Status bar displaying execution metrics or errors. */
    private JLabel statusLabel;
    /** Hierarchical database schema tree view. */
    private JTree schemaTree;

    /**
     * Constructs and initializes the DBMS Swing management window.
     *
     * @param engine query execution engine used to evaluate SQL statements
     */
    public DatabaseGui(QueryEngine engine) {
        this.engine = engine;
        this.repository = engine.getDatabaseRepository();

        setTitle("Simple Relational DBMS - Management Console");
        setSize(1000, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    repository.close();
                } catch (IOException ex) {
                    System.err.println("Error while closing repository: " + ex.getMessage());
                }
            }
        });

        initUi();
        refreshSchemaTree();
    }

    private void initUi() {
        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(240, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Database Schema"));

        schemaTree = new JTree(new DefaultMutableTreeNode("Database"));
        schemaTree.setRootVisible(true);
        schemaTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) schemaTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof String name) {
                if (repository.hasTable(name)) {
                    queryArea.setText("SELECT * FROM " + name + ";");
                }
            }
        });
        leftPanel.add(new JScrollPane(schemaTree), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh Schema");
        refreshBtn.addActionListener(e -> refreshSchemaTree());
        leftPanel.add(refreshBtn, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));

        JPanel topQueryPanel = new JPanel(new BorderLayout(5, 5));
        topQueryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JComboBox<String> presetCombo = new JComboBox<>(new String[]{
                "-- Select Sample Query --",
                "SELECT * FROM cars;",
                "SELECT brand, price FROM cars WHERE price > 20000;",
                "SELECT * FROM cars WHERE brand = 'Toyota';",
                "EXPLAIN SELECT * FROM cars WHERE brand = 'Toyota';",
                "INSERT INTO cars VALUES (3, 'Audi', 32000.0, TRUE, '2024-03-01', 2);",
                "UPDATE cars SET price = 28000 WHERE id = 1;",
                "DELETE FROM cars WHERE brand = 'Audi';",
                "SHOW TABLES;",
                "SHOW INDEXES;"
        });
        presetCombo.addActionListener(e -> {
            String selected = (String) presetCombo.getSelectedItem();
            if (selected != null && !selected.startsWith("--")) {
                queryArea.setText(selected);
            }
        });

        JButton runBtn = new JButton("Execute (F5)");
        runBtn.setBackground(new Color(40, 140, 60));
        runBtn.setForeground(Color.WHITE);
        runBtn.setFocusPainted(false);
        runBtn.addActionListener(e -> runQuery());

        JButton explainBtn = new JButton("Explain");
        explainBtn.addActionListener(e -> {
            String q = queryArea.getText().trim();
            if (q.isEmpty()) {
                return;
            }
            if (!q.toLowerCase().startsWith("explain")) {
                queryArea.setText("EXPLAIN " + q);
            }
            runQuery();
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> queryArea.setText(""));

        JButton demoBtn = new JButton("Init Demo Data");
        demoBtn.addActionListener(e -> {
            initDemoData();
            refreshSchemaTree();
        });

        toolbar.add(new JLabel("Templates:"));
        toolbar.add(presetCombo);
        toolbar.add(runBtn);
        toolbar.add(explainBtn);
        toolbar.add(clearBtn);
        toolbar.add(demoBtn);

        queryArea = new JTextArea(5, 60);
        queryArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        queryArea.setText("SELECT * FROM cars;");
        queryArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F5 || (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER)) {
                    runQuery();
                }
            }
        });

        topQueryPanel.add(toolbar, BorderLayout.NORTH);
        topQueryPanel.add(new JScrollPane(queryArea), BorderLayout.CENTER);

        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultTable.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Results"));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topQueryPanel, tableScroll);
        rightSplit.setResizeWeight(0.25);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        statusLabel = new JLabel(" Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        statusLabel.setPreferredSize(new Dimension(0, 26));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplit.setDividerLocation(240);

        add(mainSplit, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void runQuery() {
        String sql = queryArea.getText().trim();
        if (sql.isEmpty()) {
            return;
        }

        try {
            QueryResult result = engine.execute(sql);
            renderResult(result);
            refreshSchemaTree();
        } catch (Exception ex) {
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            statusLabel.setForeground(Color.RED);
            statusLabel.setText(" Error: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Query Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderResult(QueryResult result) {
        statusLabel.setForeground(new Color(20, 100, 20));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(" %s | Executed in %.2f ms", result.getMessage(), result.getExecutionTimeMs()));
        if (result.getExecutionPlan() != null) {
            sb.append(" | Plan: ").append(result.getExecutionPlan());
        }
        statusLabel.setText(sb.toString());

        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        if (!result.isSelect()) {
            return;
        }

        List<String> cols = result.getColumnNames();
        for (String c : cols) {
            tableModel.addColumn(c);
        }

        for (Row r : result.getRows()) {
            Object[] rowData = new Object[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                Object val = r.get(cols.get(i));
                rowData[i] = val == null ? "NULL" : val;
            }
            tableModel.addRow(rowData);
        }
    }

    private void refreshSchemaTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Database");
        for (String tbl : repository.getTableNames()) {
            DefaultMutableTreeNode tblNode = new DefaultMutableTreeNode(tbl);
            TableSchema schema = repository.getTable(tbl).getSchema();

            DefaultMutableTreeNode colsNode = new DefaultMutableTreeNode("Columns (" + schema.getColumns().size() + ")");
            for (var col : schema.getColumns()) {
                colsNode.add(new DefaultMutableTreeNode(col.toString()));
            }
            tblNode.add(colsNode);

            var indexes = repository.getTable(tbl).getIndexes();
            DefaultMutableTreeNode idxNode = new DefaultMutableTreeNode("Indexes (" + indexes.size() + ")");
            for (var idx : indexes) {
                idxNode.add(new DefaultMutableTreeNode(idx.getName() + " on (" + idx.getColumnName() + ")"));
            }
            tblNode.add(idxNode);

            root.add(tblNode);
        }
        schemaTree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < schemaTree.getRowCount(); i++) {
            schemaTree.expandRow(i);
        }
    }

    private void initDemoData() {
        try {
            engine.execute("CREATE TABLE IF NOT EXISTS manufacturers (id INT PRIMARY KEY, name VARCHAR, country VARCHAR);");
            engine.execute("CREATE TABLE IF NOT EXISTS cars (id INT PRIMARY KEY, brand VARCHAR, price DOUBLE, is_available BOOLEAN, created_at DATE, manufacturer_id INT REFERENCES manufacturers(id));");

            if (repository.getTable("manufacturers").count() == 0) {
                engine.execute("INSERT INTO manufacturers (id, name, country) VALUES (1, 'Toyota Motor Corp', 'Japan'), (2, 'Bayerische Motoren Werke AG', 'Germany');");
            }
            if (repository.getTable("cars").count() == 0) {
                engine.execute("INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE, '2024-01-15', 1);");
                engine.execute("INSERT INTO cars (id, brand, price, is_available, manufacturer_id) VALUES (2, 'BMW', 35000.0, TRUE, 2);");
            }

            engine.execute("CREATE INDEX IF NOT EXISTS idx_cars_brand ON cars (brand);");
            JOptionPane.showMessageDialog(this, "Sample database schema and records initialized successfully!", "Demo Loaded", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Demo Initialization Error", JOptionPane.WARNING_MESSAGE);
        }
    }
}

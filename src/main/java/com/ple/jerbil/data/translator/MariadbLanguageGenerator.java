package com.ple.jerbil.data.translator;

import com.ple.jerbil.data.*;
import com.ple.jerbil.data.query.*;
import com.ple.jerbil.data.selectExpression.*;
import com.ple.jerbil.data.selectExpression.NumericExpression.*;
import com.ple.jerbil.data.selectExpression.NumericExpression.function.Sum;
import com.ple.jerbil.data.selectExpression.booleanExpression.*;
import com.ple.jerbil.data.sync.Diff;
import com.ple.util.*;
import org.jetbrains.annotations.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

public class MariadbLanguageGenerator implements LanguageGenerator {

  public static MariadbLanguageGenerator only = new MariadbLanguageGenerator();

  public static LanguageGenerator make() {
    return only;
  }

  @Override
  public String toSql(CompleteQuery completeQuery) {
    final String sql;
    switch (completeQuery.queryType) {
      case select -> sql = toSql((SelectQuery) completeQuery);
      case delete -> sql = toSql((DeleteQuery) completeQuery);
      case update -> sql = toSql((UpdateQuery) completeQuery);
      case insert -> sql = toSql((InsertQuery) completeQuery);
      case create -> sql = toSql((CreateQuery) completeQuery);
      default -> throw new IllegalStateException("Unexpected value: " + completeQuery.queryType);
    }
    return sql;
  }

  @Override
  public DatabaseContainer getDbFromSql(String dbCreateString, Database db, IList<String> tblCreateStringList) {
    IMap<String, TableContainer> tables = IArrayMap.empty;
    for (String tblCreateStr : tblCreateStringList) {
      tables = tables.put(getTableNameFromSql(tblCreateStr), getTableFromSql(tblCreateStr, db));
    }
    return DatabaseContainer.make(db, tables);
  }

  @Override
  public TableContainer getTableFromSql(String createTableString, Database db) {
    final String tableName = getTableNameFromSql(createTableString);
    final Table table = Table.make(tableName, db);
    final StorageEngine engine = getEngineFromSql(createTableString);
    final IMap<String, Column> columns = getColumnsFromSql(createTableString, table);
    final IMap<String, Index> indexes = getIndexesFromSql(createTableString, table, columns);
    final NumericColumn autoIncColumn = getAutoIncColumn(indexes.get("primary").indexedColumns);
    return TableContainer.make(table, columns, engine, indexes, autoIncColumn);
  }

  private NumericColumn getAutoIncColumn(IMap<String, IndexedColumn> primary) {
    for (IEntry<String, IndexedColumn> indexedColumnEntry : primary) {
      if (indexedColumnEntry.value.column.hints.isAutoInc()) {
        return (NumericColumn) indexedColumnEntry.value.column;
      }
    }
    return null;
  }

  private IMap<String, Index> getIndexesFromSql(String createTableString, Table table, IMap<String, Column> columns) {
    IMap<String, Index> indexes = IArrayMap.empty;
    createTableString = removeBackticks(createTableString);
    final String[] lines = formatToIndexLines(createTableString);
    for (String line : lines) {
      Index index = getIndexFromSql(line, table, columns);
      indexes = indexes.put(index.indexName, index);
    }
    return indexes;
  }

  public Index getIndexFromSql(String indexString, Table table, IMap<String, Column> columns) {
    IndexType indexType = getIndexTypeFromSql(indexString);
    String indexName = getIndexNameFromSql(indexString, indexType);
    IList<IndexedColumn> indexedColumns = getIndexedColumnsFromSql(indexString, columns);
    return Index.make(indexType, indexName, table, indexedColumns.toArray());
  }

  private String getIndexNameFromSql(String indexString, IndexType indexType) {
    if (indexType.equals(IndexType.primary)) {
      return "primary";
    }
    return indexString.replaceAll("^.*KEY ", "").replaceAll("\\(.*\\),?", "");
  }

  private IndexType getIndexTypeFromSql(String indexString) {
    String type = indexString.replaceAll("KEY \\(.*", "KEY");
    if (type.indexOf("KEY") == 0) {
      return IndexType.secondary;
    } else if (type.contains("PRIMARY")) {
      return IndexType.primary;
    } else if (type.contains("FULLTEXT")) {
      return IndexType.fulltext;
    } else if (type.contains("UNIQUE")) {
      return IndexType.unique;
    } else if (type.contains("FOREIGN")) {
      return IndexType.foreign;
    }
    return null;
  }

  private IList<IndexedColumn> getIndexedColumnsFromSql(String indexString, IMap<String, Column> columns) {
    IList<IndexedColumn> indexedColumns = IArrayList.empty;
    int prefixSize = 0;
    //FIXME: Once you figure out how to obtain sortOrder from Show create table or Show index commands use that method
    // here to obtain sortOrder for each column. Figure out a method that works for mariadb and mysql both if possible.
    SortOrder sortOrder = null;
    final String[] indexColumnsArr = indexString.replaceFirst("^.*KEY.*\\(", "").replaceAll("\\),", "")
        .replaceAll("\\)", "").split(",");
    for (String colName : indexColumnsArr) {
      prefixSize = Integer.parseInt(colName.toLowerCase(Locale.ROOT).replaceAll("[^0-9]", ""));
      indexedColumns = indexedColumns.add(IndexedColumn.make(columns.get(colName), prefixSize, null));
    }
    return indexedColumns;
  }

  private StorageEngine getEngineFromSql(String createTableSql) {
    final String tableSql = createTableSql.toLowerCase();
    final int engineIndex = tableSql.indexOf("\n) engine=") + 10;
    final String engineName = tableSql.substring(engineIndex, tableSql.indexOf(" ", engineIndex));
    switch (engineName) {
      case "aria":
        return StorageEngine.simple;
      case "innodb":
        return StorageEngine.transactional;
      default:
        return StorageEngine.simple;
    }
  }

  private String getTableNameFromSql(String createTableSql) {
    createTableSql = createTableSql.replaceFirst("^.* `", "");
    final String tableName = createTableSql.substring(0, createTableSql.indexOf("`"));
    return tableName;
  }

  private IMap<String, Column> getColumnsFromSql(String createTableString, Table table) {
    String[] columnLines = splitToColumnLines(createTableString);
    String[] keyLines = splitToKeyLines(createTableString);
    IMap<String, Column> columns = IArrayMap.empty;
    Column columnFromSql = null;
    for (String columnLine : columnLines) {
      columnFromSql = getColumnFromSql(columnLine, keyLines, table);
      columns = columns.put(columnFromSql.columnName, columnFromSql);
    }
    return columns;
  }

  private String[] splitToKeyLines(String createTableString) {
    return createTableString
        .replaceAll("\n\s+`.*", "")
        .replaceAll("^CREATE TABLE.*\n", "")
        .replaceAll("\n\\) ENGINE", "")
        .split(",\n");
  }

  @Override
  public Column getColumnFromSql(String columnLine, String[] keyLines, Table table) {
    columnLine = columnLine.stripLeading();
    final String colName = columnLine.replaceFirst(" .*", "");
    BuildingHints hints = BuildingHints.make();
    for (String keyLine : keyLines) {
      if (keyLine.contains(colName)) {
        hints = getHintFromSql(hints, keyLine);
      }
    }
    columnLine = columnLine.toLowerCase(Locale.ROOT);
    hints = getColumnHints(columnLine, hints);
    final DataSpec dataSpec = getDataSpecFromSql(columnLine);
    final Literal defaultValue = getDefaultValFromSql(columnLine);
//    final Expression generatedFrom = getGeneratedFromSql(columnLine);
    if (columnLine.contains("AUTO_INCREMENT")) {
      return NumericColumn.make(colName, table, dataSpec, (NumericExpression) defaultValue, hints);
    } else if (dataSpec.dataType == DataType.integer || dataSpec.dataType == DataType.decimal ||
        dataSpec.dataType == DataType.bigint || dataSpec.dataType == DataType.tinyint) {
      return NumericColumn.make(colName, table, dataSpec, (NumericExpression) defaultValue, hints);
    } else if (dataSpec.dataType == DataType.varchar || dataSpec.dataType == DataType.enumeration) {
      return StringColumn.make(colName, table, dataSpec, (StringExpression) defaultValue, hints);
    } else if (dataSpec.dataType == DataType.datetime) {
      return DateColumn.make(colName, table, dataSpec, (DateExpression) defaultValue, hints);
    } else if (dataSpec.dataType == DataType.bool) {
      return BooleanColumn.make(colName, table, dataSpec, (BooleanExpression) defaultValue, hints);
    } else {
      //FIXME: replace this with observability bridge logging.
      System.out.println("Failed to determine data type of this column:" + colName);
    }
    return null;
  }

  private BuildingHints getColumnHints(String columnLine, BuildingHints hints) {
    if (columnLine.contains("unsigned")) {
      hints = hints.unsigned();
    }
    if (columnLine.contains("auto_increment")) {
      hints = hints.autoInc();
    }
    if (!columnLine.contains("not null")) {
      hints = hints.allowNull();
    }
    if (columnLine.contains("invisible")) {
      hints = hints.invisible();
    }
    if (columnLine.contains("on update current_timestamp")) {
      hints = hints.autoUpdateTime();
    }
    return hints;
  }

  private BuildingHints getHintFromSql(BuildingHints hints, String keyLine) {
    if (keyLine.contains("PRIMARY")) {
      return hints.primary();
    } else if (keyLine.contains("UNIQUE")) {
      return hints.unique();
    } else if (keyLine.contains("FULLTEXT")) {
      return hints.fulltext();
    } else if (keyLine.contains("FOREIGN")) {
      return hints.foreign();
    } else if (keyLine.contains("KEY")) {
      return hints.index();
    }
    return hints;
  }

  public BuildingHints getHintsFromSql(String tableLine, IMap<String, String[]> columnsInIndex) {
    //TODO: Finish implementing.
    final String colName = tableLine.replaceFirst(" .*", "");
    BuildingHints hints = BuildingHints.make();
    String indexType = "";
    if (columnsInIndex.keys().contains("PRIMARY KEY")) {
      indexType = "PRIMARY KEY";
    }
    for (String column : columnsInIndex.get(indexType)) {
      if (column.equals(colName)) {
        hints = hints.primary();
      }
    }
    if (columnsInIndex.keys().contains("PRIMARY KEY")) {
      for (String column : columnsInIndex.get("PRIMARY KEY")) {
        if (column.equals(colName)) {
          hints = hints.primary();
        }
      }
    }
    if (columnsInIndex.keys().contains("KEY")) {
      for (String column : columnsInIndex.get("KEY")) {
        if (column.equals(colName)) {
          hints = hints.index();
        }
      }
    }
    return hints;
  }

  private Literal getDefaultValFromSql(String tableLine) {
    String defaultVal = tableLine.replaceAll("^.*default", "").replaceFirst(",$", "");
    if (defaultVal.replaceAll("[0-9]", "").length() == 0) {
      return Literal.make(Integer.parseInt(defaultVal));
    } else if (defaultVal.replaceAll("[0-9]", "").replace(".", "").length() == 0) {
      return Literal.make(Double.parseDouble(defaultVal));
    } else if (defaultVal.replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2}","").length() == 0) {
      return Literal.make(LocalDate.parse(defaultVal));
    } else if (defaultVal.replaceAll("[0-9]{2}:[0-9]{2}:[0-9]{2}","").length() == 0) {
      return Literal.make(LocalTime.parse(defaultVal));
    } else if (defaultVal.replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}","").length() == 0) {
      return Literal.make(LocalDateTime.parse(defaultVal));
    } else if (defaultVal.replace("false","").replace("true","").length() == 0) {
      return Literal.make(Boolean.parseBoolean(defaultVal));
    }
      return Literal.make(defaultVal);
  }

  private DataSpec getDataSpecFromSql(String tableLine) {
    DataType dataType = null;
    int size = 0;
    String regex = "";
    int endIndex = 0;
    if (tableLine.contains(" enum(")) {
      tableLine = tableLine.replaceFirst("^.* enum\\(", "").replaceAll("'", "");
      endIndex = tableLine.indexOf(") ");
      return DataSpec.make(DataType.enumeration, tableLine.substring(0, endIndex));
    } else if (tableLine.contains(" set(")) {
      tableLine = tableLine.replaceFirst("^.* set\\(", "").replaceAll("'", "");
      endIndex = tableLine.indexOf(") ");
      return DataSpec.make(DataType.enumeration, tableLine.substring(0, endIndex));
    } else if (tableLine.contains(" varchar(")) {
      regex = "^.* varchar\\(";
      dataType = DataType.varchar;
    } else if (tableLine.contains(" int(")) {
      regex = "^.* int\\(";
      dataType = DataType.integer;
    } else if (tableLine.contains(" bigint(")) {
      regex = "^.* bigint\\(";
      dataType = DataType.bigint;
    } else if (tableLine.contains(" tinyint(")) {
      regex = "^.* tinyint\\(";
      dataType = DataType.tinyint;
    }
    tableLine = tableLine.replaceFirst(regex, "");
    endIndex = tableLine.indexOf(")");
    size = Integer.parseInt(tableLine.substring(0, endIndex));
    return DataSpec.make(dataType, size);
  }

  private String[] formatToIndexLines(String createTableString) {
    return createTableString
        .replaceAll("\n\\) ENGINE=.*", "")
        .replaceAll("^CREATE TABLE .*\n", "").split("\n");
  }

  private String[] splitToColumnLines(String createTableString) {
    return createTableString
        .replaceAll("\n\\) ENGINE=.*", "")
        .replaceAll("^CREATE TABLE .*\n", "")
        .replaceAll("\n\s+PRIMARY KEY .*", "")
        .replaceAll("\n\s+KEY .* \\(.*", "")
        .split("\n");
  }

  private String removeBackticks(String tableInfo) {
    return tableInfo.replaceAll("`", "");
  }

  /* Uncomment when generatedFrom is supported.
    private Expression getGeneratedFromSql(String tableLine) {
      Expression expression = null;
      tableLine = tableLine
          .replaceAll("^.*generated always as \\(", "")
          .replaceAll("\\).*$", "");
      final String[] parts = tableLine.split(" ");
      for (String part : parts) {
        if (part.replaceAll("'", "").length() < part.length()) {
          // This means it is a StringExpression.
        } else if (part.replaceAll("[a-z]","").length() < part.length()) {
          // This means it is a Column.
        } else if (part.replaceAll("[0-9]", "").length() < part.length()) {
          // This means it is a NumericExpression.
          if (part.length() == 1) {
            if (part.contains("+")) {
            } else if (part.contains("-")) {
            } else if (part.contains("*")) {
            } else if (part.contains("/")) {
            }
          }
        }
      }
      return expression;
    }
  */

  private String toSql(SelectQuery selectQuery) {
    String sql = "";
    String preSelect = "";
    String postSelect = "";
    String separator = "";
    IList<TableContainer> tableList = IArrayList.empty;
    if (selectQuery.queryFlags != null) {
      if (selectQuery.queryFlags.isDistinct()) {
        postSelect += "distinct ";
      }
      if (selectQuery.queryFlags.isExplain()) {
        preSelect += "explain";
        separator = " ";
      }
      if (selectQuery.queryFlags.isAnalyze()) {
        preSelect += separator + "analyze";
        separator = " ";
      }
    }
    sql += preSelect + separator + "select " + postSelect;
    if (selectQuery.fromExpression != null) {
      if (selectQuery.fromExpression instanceof Join) {
        tableList = tableList.addAll(selectQuery.fromExpression.tableList());
      } else if (selectQuery.fromExpression instanceof TableContainer) {
        tableList = tableList.add((TableContainer) selectQuery.fromExpression);
      }
      if (selectQuery.select.size() == 1 && selectQuery.select.toArray()[0] instanceof SelectAllExpression) {
        sql += "*\nfrom " + toSql(selectQuery.fromExpression) + "\n";
      } else {
        IList<SelectExpression> transformedSelect = transformColumns(selectQuery.select, tableList);
        sql += toSqlSelect(transformedSelect) + "\n" + "from " + toSql(selectQuery.fromExpression) + "\n";
      }
      if (selectQuery.where != null) {
        BooleanExpression transformedWhere = transformColumns(selectQuery.where, tableList);
        sql += toSqlWhere(transformedWhere) + "\n";
      }
      if (selectQuery.groupBy != null) {
        IList<SelectExpression> transformedGroupBy = transformColumns(selectQuery.groupBy, tableList);
        sql += "group by " + toSqlSelect(transformedGroupBy) + "\n";
      }
      if (selectQuery.having != null) {
        BooleanExpression transformedHaving = transformColumns(selectQuery.having, tableList);
        sql += toSqlHaving(transformedHaving) + "\n";
      }
      if (selectQuery.orderBy != null) {
        IMap<SelectExpression, SortOrder> transformedOrderBy = transformColumns(selectQuery.orderBy, tableList);
        sql += "order by " + toSqlOrderBy(transformedOrderBy) + "\n";
      }
      if (selectQuery.limit != null) {
        sql += "limit " + selectQuery.limit.offset + ", " + selectQuery.limit.limit + "\n";
      }
    } else {
      sql += toSqlSelect(selectQuery.select);
    }
    if (selectQuery.union != null) {
      if (selectQuery.union.unionType.equals(UnionType.all)) {
        sql += "union all\n";
      } else {
        sql += "union\n";
      }
      sql += toSql(selectQuery.union.selectQuery);
    }
    return sql;
  }

  //TODO: make it possible for Order to be null, so that nothing is specified in language generator and default is used from mysql instead.
  private String toSqlOrderBy(IMap<SelectExpression, SortOrder> orderBy) {
    String sql = "";
    String order = "";
    String separator = "";
    for (IEntry<SelectExpression, SortOrder> entry : orderBy) {
      if (entry.value.equals(SortOrder.ascending)) {
        order = " asc";
      } else {
        order = " desc";
      }
      sql += separator + toSql(entry.key) + order;
      separator = ", ";
    }
    return sql;
  }

  private IMap<SelectExpression, SortOrder> transformColumns(IMap<SelectExpression, SortOrder> orderBy,
                                                             IList<TableContainer> tableList) {
    IMap<SelectExpression, SortOrder> transformedMap = IArrayMap.empty;
    for (IEntry<SelectExpression, SortOrder> entry : orderBy) {
      //TODO: handle aliasedExpressions here.
      if (entry.key instanceof Column) {
        transformedMap = transformedMap.put(transformColumns(entry.key, tableList), entry.value);
      } else {
        transformedMap = transformedMap.put(entry.key, entry.value);
      }
    }
    return transformedMap;
  }

  //TODO: Make transformColumns work for ArithmeticExpressions or BooleanExpressions that contain columns
  private IList<SelectExpression> transformColumns(IList<SelectExpression> select, IList<TableContainer> tableList) {
    final SelectExpression[] selectArr = select.toArray();
    String tableName = "";
    int matchingColumns = 0;
    for (int i = 0; i < selectArr.length; i++) {
      if (selectArr[i] instanceof Column && !(selectArr[i] instanceof QueriedColumn)) {
        final Column column = (Column) selectArr[i];
        for (TableContainer table : tableList) {
          for (int j = 0; j < table.columns.size(); j++) {
            if (table.columns.values().get(j).columnName.equals(column.getColumnName())) {
              matchingColumns++;
            }
          }
          if (matchingColumns > 1) {
            tableName = table.table.tableName;
          }
          matchingColumns = 0;
        }
        selectArr[i] = QueriedColumn.make((Column) selectArr[i], tableName);
        tableName = "";
      }
    }
    return select;
  }

  private BooleanExpression transformColumns(BooleanExpression be, IList<TableContainer> tableList) {
    final BooleanExpression result;
    if (be instanceof Or) {
      Or or = (Or) be;
      IList<BooleanExpression> list = IArrayList.make();
      for (BooleanExpression condition : or.conditions) {
        list = list.add(transformColumns(condition, tableList));
      }
      result = or.make(list);
    } else if (be instanceof And) {
      And and = (And) be;
      IList<BooleanExpression> list = IArrayList.make();
      for (BooleanExpression condition : and.conditions) {
        list = list.add(transformColumns(condition, tableList));
      }
      result = and.make(list);
    } else if (be instanceof Equals) {
      Equals eq = (Equals) be;
      final SelectExpression e1 = transformColumns(eq.e1, tableList);
      final SelectExpression e2 = transformColumns(eq.e2, tableList);
      result = Equals.make(e1, e2);
    } else if (be instanceof GreaterThan) {
      GreaterThan gt = (GreaterThan) be;
      final SelectExpression e1 = transformColumns(gt.e1, tableList);
      final SelectExpression e2 = transformColumns(gt.e2, tableList);
      result = GreaterThan.make(e1, e2);
    } else if (be instanceof GreaterOrEqual) {
      GreaterOrEqual ge = (GreaterOrEqual) be;
      final SelectExpression e1 = transformColumns(ge.e1, tableList);
      final SelectExpression e2 = transformColumns(ge.e2, tableList);
      result = GreaterOrEqual.make(e1, e2);
    } else {
      result = be;
    }
    return result;
  }

  private SelectExpression transformColumns(SelectExpression e, IList<TableContainer> tableList) {
    String tableName = "";
    Column col;
    int matchingColumns = 0;
    if (e instanceof Column) {
      col = (Column) e;
      for (TableContainer table : tableList) {
        for (int i = 0; i < table.columns.size(); i++) {
          if (table.columns.values().get(i).columnName.equals(col.getColumnName())) {
            matchingColumns++;
            if (table.columns.values().get(i).equals(col)) {
              tableName = col.table.tableName;
            }
          }
        }
      }
    } else {
      return e;
    }
    if (matchingColumns <= 1) {
      tableName = "";
    }
    return QueriedColumn.make(col, tableName);
  }

  //TODO combine this method with toSqlWhere
  private String toSqlHaving(BooleanExpression having) {
    if (having == null) {
      return "";
    }
    String fullHavingClause = "having ";
    fullHavingClause += toSqlBooleanExpression(having);
    if (fullHavingClause.endsWith(")") && !fullHavingClause.contains(") against(")) {
      fullHavingClause = fullHavingClause.replaceAll("where \\(", "where ").replaceAll("\\)\\z", "");
    }
    return fullHavingClause;
  }

  //TODO: Make this work for both having and where.
  //FIXME: Currently, this makes use of having + AND or OR incompatible. because of if (fullWhereClause.endsWith(")").
  private String toSqlWhere(BooleanExpression where) {
    if (where == null) {
      return "";
    }
    String fullWhereClause = "where ";
    fullWhereClause += toSqlBooleanExpression(where);
    if (fullWhereClause.endsWith(")") && !fullWhereClause.contains(") against(")) {
      fullWhereClause = fullWhereClause.replaceAll("where \\(", "where ").replaceAll("\\)\\z", "");
    }
    return fullWhereClause;
  }

  private String toSqlBooleanExpression(BooleanExpression booleanExpression) {
    String boolExpString = "";
    if (booleanExpression instanceof Equals) {
      final Equals eq = (Equals) booleanExpression;
      boolExpString += toSql(eq.e1) + " = " + toSql(eq.e2);
    } else if (booleanExpression instanceof GreaterThan) {
      final GreaterThan gt = (GreaterThan) booleanExpression;
      if (gt.e1 instanceof ArithmeticExpression) {
        boolExpString += toSqlArithmetic("", (ArithmeticExpression) gt.e1) + " > " + toSql(gt.e2);
      } else {
        boolExpString += toSql(gt.e1) + " > " + toSql(gt.e2);
      }
    } else if (booleanExpression instanceof BooleanColumn) {
      boolExpString += ((BooleanColumn) booleanExpression).columnName + " = 1";
    } else if (booleanExpression instanceof True) {
      boolExpString += toSql(((True) booleanExpression).expression) + " = 1";
    } else if (booleanExpression instanceof False) {
      boolExpString += toSql(((False) booleanExpression).expression) + " <> 1";
    } else if (booleanExpression instanceof Null) {
      boolExpString += toSql(((Null) booleanExpression).expression) + " is null";
    } else if (booleanExpression instanceof NonNull) {
      boolExpString += toSql(((NonNull) booleanExpression).expression) + " is not null";
    } else if (booleanExpression instanceof Regexp) {
      boolExpString += toSql(
          ((Regexp) booleanExpression).e1) + " regexp '" + ((Regexp) booleanExpression).e2.value + "'";
    } else if (booleanExpression instanceof NotRegexp) {
      boolExpString += toSql(
          ((NotRegexp) booleanExpression).e1) + " not regexp '" + ((NotRegexp) booleanExpression).e2.value + "'";
    } else if (booleanExpression instanceof Between) {
      final Between bt = (Between) booleanExpression;
      boolExpString += toSql(bt.n1) + " between " + bt.n2.value + " and " + bt.n3.value;
    } else if (booleanExpression instanceof NotBetween) {
      final NotBetween bt = (NotBetween) booleanExpression;
      boolExpString += toSql(bt.n1) + " not between " + bt.n2.value + " and " + bt.n3.value;
    } else if (booleanExpression instanceof Like) {
      final Like l = (Like) booleanExpression;
      boolExpString += toSql(l.s1) + " like '" + l.s2.value + "'";
    } else if (booleanExpression instanceof NotLike) {
      final NotLike nl = (NotLike) booleanExpression;
      boolExpString += toSql(nl.s1) + " not like '" + nl.s2.value + "'";
    } else if (booleanExpression instanceof Match) {
      boolExpString += "match(" + toSql(
          ((Match) booleanExpression).s1) + ") against('" + ((Match) booleanExpression).s2.value + "')";
    } else if (booleanExpression instanceof GreaterOrEqual) {
      final GreaterOrEqual ge = (GreaterOrEqual) booleanExpression;
      if (ge.e1 instanceof ArithmeticExpression) {
        boolExpString += toSqlArithmetic("", (ArithmeticExpression) ge.e1) + " >= " + toSql(ge.e2);
      } else {
        boolExpString += toSql(ge.e1) + " >= " + toSql(ge.e2);
      }
    } else if (booleanExpression instanceof And) {
      final And and = (And) booleanExpression;
      final BooleanExpression[] beArray = and.conditions.toArray();
      boolExpString += "(";
      for (int i = 0; i < beArray.length; i++) {
        if (i == beArray.length - 1) {
          boolExpString += toSqlBooleanExpression(beArray[i]);
        } else {
          boolExpString += toSqlBooleanExpression(beArray[i]) + "\nand ";
        }
      }
      boolExpString += ")";
    } else if (booleanExpression instanceof Or) {
      final Or or = (Or) booleanExpression;
      final BooleanExpression[] boolExp = or.conditions.toArray();
      boolExpString += "(";
      for (int i = 0; i < boolExp.length; i++) {
        if (i == boolExp.length - 1) {
          boolExpString += toSqlBooleanExpression(boolExp[i]);
        } else {
          boolExpString += toSqlBooleanExpression(boolExp[i]) + " or ";
        }
      }
      boolExpString += ")";
    } else {
      throw new IllegalStateException("Unexpected value: " + booleanExpression.getClass().getSimpleName());
    }
    return boolExpString;
  }

  private String toSql(SelectExpression e) {
    final String output;
    if (e instanceof QueriedColumn) {
      //TODO: Check if column name has space and if so put `backticks` around it.
      final QueriedColumn s = (QueriedColumn) e;
      if (s.tableName != "") {
        output = s.tableName + "." + s.column.getColumnName();
      } else {
        output = s.column.getColumnName();
      }
    } else if (e instanceof LiteralString) {
      final LiteralString litStr = (LiteralString) e;
      output = "'" + litStr.value + "'";
    } else if (e instanceof LiteralNumber) {
      final LiteralNumber n = (LiteralNumber) e;
      output = n.value.toString();
    } else if (e instanceof AliasedExpression) {
      final AliasedExpression ae = (AliasedExpression) e;
      output = ae.alias;
    } else if (e instanceof Column) {
      output = ((Column) e).columnName;
    } else if (e instanceof ArithmeticExpression) {
      output = toSqlArithmetic("", (ArithmeticExpression) e);
    } else if (e instanceof DateExpression) {
      output = toSqlDate((DateExpression) e);
    } else if (e instanceof LiteralBoolean) {
      output = ((LiteralBoolean) e).bool.toString();
    } else {
      throw new RuntimeException("Unknown Expression ");
    }
    return output;
  }

  private String toSqlDate(DateExpression e) {
    if (e instanceof CurrentTimestamp) {
      return "current_timestamp";
    } else if (e instanceof LiteralDate) {
      return ((LiteralDate) e).dateTime.toString();
    }
    return null;
  }

  private String toSql(FromExpression fromExpression) {
    String fullFromExpressionList = "";
    if (fromExpression instanceof TableContainer) {
      TableContainer table = (TableContainer) fromExpression;
      fullFromExpressionList += table.table.tableName;
    } else if (fromExpression instanceof Join) {
      Join join = (Join) fromExpression;
      fullFromExpressionList += getJoinString(join);
    }
    return fullFromExpressionList;
  }

  @NotNull
  private String getJoinString(Join join) {
    String result = "";
    if (join.fe1 instanceof Join) {
      result += getJoinString((Join) join.fe1);
      TableContainer t2 = (TableContainer) join.fe2;
      //FIXME: using needs to choose the name of the field based on primary key of the fromexpression and if a matching field exists in compared fromexpression. If not, this needs to throw an error.
      result += "\ninner join " + t2.table.tableName + " using (" + t2.table.tableName + "Id)";
    } else {
      TableContainer t1 = (TableContainer) join.fe1;
      TableContainer t2 = (TableContainer) join.fe2;
      result += t1.table.tableName + "\ninner join " + t2.table.tableName + " using (" + t1.table.tableName + "Id)";
    }
    return result;
  }

  private String toSqlSelect(IList<SelectExpression> select) {
    final SelectExpression[] selectArr = select.toArray();
    String fullSelectList = "";
    if (selectArr.length == 0) {
      return "*";
    }
    for (int i = 0; i < selectArr.length; i++) {
      if (selectArr[i] instanceof QueriedColumn) {
        final Column column = ((QueriedColumn) selectArr[i]).column;
        if (column instanceof NumericColumn || column instanceof StringColumn || column instanceof EnumeralColumn || column instanceof DateColumn) {
          fullSelectList += column.columnName;
        }
      } else if (selectArr[i] instanceof SelectAllExpression) {
        fullSelectList += "*";
      } else if (selectArr[i] instanceof CountAgg) {
        fullSelectList += "count(*)";
      } else if (selectArr[i] instanceof ArithmeticExpression) {
        final ArithmeticExpression arExp = (ArithmeticExpression) selectArr[i];
        fullSelectList += toSqlArithmetic(fullSelectList, arExp);
      } else if (selectArr[i] instanceof AliasedExpression) {
        final AliasedExpression ae = (AliasedExpression) selectArr[i];
        if (ae.countAgg != null) {
          fullSelectList += "count(*) as " + ae.alias;
        }
        if (ae.expression != null) {
          if (ae.expression instanceof Column) {
            final Column column = (Column) ae.expression;
            fullSelectList += column.getColumnName() + " as " + ae.alias;
          } else if (ae.expression instanceof ArithmeticExpression) {
            final ArithmeticExpression arExp = (ArithmeticExpression) ae.expression;
            fullSelectList += toSqlArithmetic(fullSelectList, arExp) + " as " + ae.alias;
          } else if (ae.expression instanceof Sum) {
            final Sum sum = (Sum) ae.expression;
            fullSelectList += "sum(" + sum.numericColumn.columnName + ") as " + ae.alias;
          }
        }
      }
      if (selectArr.length != i + 1) {
        fullSelectList += ", ";
      }
    }
    return fullSelectList;
  }

  private String toSqlArithmetic(String sql, ArithmeticExpression arExp) {
    String operator = "";
    sql = "";
    if (arExp.type == Operator.plus) {
      operator = " + ";
    } else if (arExp.type == Operator.minus) {
      operator = " - ";
    } else if (arExp.type == Operator.times) {
      operator = " * ";
    } else if (arExp.type == Operator.dividedby) {
      operator = " / ";
    } else if (arExp.type == Operator.modulus) {
      operator = " % ";
    }
    if (arExp.e1 instanceof ArithmeticExpression && !(arExp.e2 instanceof ArithmeticExpression)) {
      sql += toSqlArithmetic(sql, (ArithmeticExpression) arExp.e1);
      sql += operator + getNumericExpression((NumericExpression) arExp.e2);
    } else if (arExp.e2 instanceof ArithmeticExpression && !(arExp.e1 instanceof ArithmeticExpression)) {
      sql += getNumericExpression((NumericExpression) arExp.e1) + operator + "(" + toSqlArithmetic(sql,
          (ArithmeticExpression) arExp.e2) + ")";
    } else if (arExp.e1 instanceof ArithmeticExpression && arExp.e2 instanceof ArithmeticExpression) {
      sql += "(" + toSqlArithmetic(sql, (ArithmeticExpression) arExp.e1) + ")" + operator + "(" + toSqlArithmetic(sql,
          (ArithmeticExpression) arExp.e2) + ")";
    } else {
      sql += getNumericExpression((NumericExpression) arExp.e1) + operator + getNumericExpression(
          (NumericExpression) arExp.e2);
    }
    return sql;
  }

  private String getNumericExpression(NumericExpression e) {
    if (e instanceof NumericColumn) {
      return ((NumericColumn) e).columnName;
    } else {
      return ((LiteralNumber) e).value.toString();
    }
  }

  //TODO: Add support for specifying Null on a column. Leave not null off because that is default.
  public String toSql(Column column) {
    String sql = checkToAddBackticks(column.getColumnName());
    String dataSpec = dataTypeToSql(column);
    String nullVal = " not null";
    String defaultVal = "";
    String onUpdateVal = "";
    String autoIncrement = "";
    String unsigned = "";
    String invisible = "";
//    String generatedFrom = "";
    if (column.hints.isAllowNull() || column.hints.isPrimary() || column.defaultValue != null || column.hints.isInvisible()) {
      nullVal = "";
    }
    if (column instanceof NumericColumn && column.hints.isAutoInc()) {
      autoIncrement = " auto_increment";
    }
    if (column instanceof NumericColumn && column.hints.isUnsigned()) {
      unsigned = " unsigned";
    }
    if (column.defaultValue != null) {
      defaultVal = " default (" + toSql(column.defaultValue) + ")";
      if (defaultVal.contains("current_timestamp")) {
        defaultVal = defaultVal.replace("(", "").replace(")", "");
      }
    }
    if (column.hints.isAutoUpdateTime()) {
      onUpdateVal = " on update current_timestamp";
    }
    if (column.hints.isInvisible()) {
      invisible = " invisible";
    }
//    if (column.generatedFrom != null) {
//      generatedFrom = " as (" + toSql(column.generatedFrom) + ")";
//    }
    sql += dataSpec + unsigned + nullVal + defaultVal + onUpdateVal + autoIncrement + invisible;// + generatedFrom;
    return sql;
  }

  private String dataTypeToSql(Column column) {
    String sql = " " + column.dataSpec.getSqlName();
    if (column instanceof NumericColumn) {
      if (column.dataSpec.preciseScale != null) {
        sql += "(" + column.dataSpec.preciseScale[0] + ", " + column.dataSpec.preciseScale[1] + ")";
      } else if (column.dataSpec.size != 0) {
        sql += "(" + column.dataSpec.size + ")";
      }
    } else if (column instanceof StringColumn) {
      if (!column.dataSpec.dataType.equals(DataType.text)) {
        sql += "(" + column.dataSpec.size + ")";
      }
    } else if (column instanceof EnumeralColumn) {
      final EnumSpec enumSpec = (EnumSpec) column.dataSpec;
      sql += toSql(enumSpec);
    }
    return sql;
  }

  private String toSql(EnumSpec enumSpec) {
    String sql = "(";
    String separator = "'";
    for (String value : enumSpec.values) {
      sql += separator + value + "'";
      separator = ",'";
    }
    sql += ")";
    return sql;
  }

  @Override
  public String toSql(Diff diff) {
    //TODO: Implement this. Make sure it returns null if diffs are empty.
    return null;
  }

  public String toSql(UpdateQuery updateQuery) {
    String sql = "update " + checkToAddBackticks(toSql(updateQuery.fromExpression)) + "\nset ";
    IList<TableContainer> tableList = IArrayList.empty;
    String separator = "";
    final IList<Column> keys = updateQuery.set.get(0).keys();
    final IList<Expression> values = updateQuery.set.get(0).values();
    for (int i = 0; i < keys.size(); i++) {
      sql += separator + keys.get(i).columnName + " = " + toSql(values.get(i));
      separator = ",\n";
    }
    sql += "\n";
    if (updateQuery.fromExpression instanceof Join) {
      tableList = tableList.addAll(updateQuery.fromExpression.tableList());
    } else if (updateQuery.fromExpression instanceof TableContainer) {
      tableList = tableList.add((TableContainer) updateQuery.fromExpression);
    }
    if (updateQuery.where != null) {
      BooleanExpression transformedWhere = transformColumns(updateQuery.where, tableList);
      sql += toSqlWhere(transformedWhere) + "\n";
    }
    if (updateQuery.orderBy != null) {
      IMap<SelectExpression, SortOrder> transformedOrderBy = transformColumns(updateQuery.orderBy, tableList);
      sql += "order by " + toSqlOrderBy(transformedOrderBy) + "\n";
    }
    if (updateQuery.limit != null) {
      sql += "limit " + updateQuery.limit.offset + ", " + updateQuery.limit.limit + "\n";
    }
    return sql;
  }

  public String toSql(DeleteQuery deleteQuery) {
    String sql = "delete from " + checkToAddBackticks(toSql(deleteQuery.fromExpression)) + "\n";
    IList<TableContainer> tableList = IArrayList.empty;
    if (deleteQuery.fromExpression instanceof Join) {
      tableList = tableList.addAll(deleteQuery.fromExpression.tableList());
    } else if (deleteQuery.fromExpression instanceof TableContainer) {
      tableList = tableList.add((TableContainer) deleteQuery.fromExpression);
    }
    if (deleteQuery.where != null) {
      BooleanExpression transformedWhere = transformColumns(deleteQuery.where, tableList);
      sql += toSqlWhere(transformedWhere) + "\n";
    }
    if (deleteQuery.orderBy != null) {
      IMap<SelectExpression, SortOrder> transformedOrderBy = transformColumns(deleteQuery.orderBy, tableList);
      sql += "order by " + toSqlOrderBy(transformedOrderBy) + "\n";
    }
    if (deleteQuery.limit != null) {
      sql += "limit " + deleteQuery.limit.offset + ", " + deleteQuery.limit.limit + "\n";
    }
    return sql;
  }

  public String toSql(InsertQuery insertQuery) {
    String sql = "";
    if (insertQuery.queryFlags.isReplace()) {
      sql += "replace ";
    } else {
      sql += "insert ";
    }
    sql += "into " + toSql(insertQuery.fromExpression) + "\n(";
    String separator = "";
    for (Column column : insertQuery.set.get(0).keys()) {
      sql += separator + column.getColumnName();
      separator = ", ";
    }
    sql += ")" + " values\n";
    int i = 0;
    for (IMap<Column, Expression> entry : insertQuery.set) {
      separator = "";
      sql += "(";
      for (Expression value : entry.values()) {
        sql += separator + toSql(value);
        separator = ", ";
      }
      if (i >= insertQuery.set.toArray().length - 1) {
        sql += ")\n";
      } else {
        sql += "),\n";
        i++;
      }
    }
    return sql;
  }

  public String toSql(CreateQuery createQuery) {
    String sql = "";
    String indexes = "";
    if (createQuery.db != null) {
      return "create database " + checkToAddBackticks(createQuery.db.databaseName);
    }
    String engine = "";
    if (createQuery.fromExpression instanceof TableContainer) {
      final TableContainer t = (TableContainer) createQuery.fromExpression;
      if (t.storageEngine == StorageEngine.simple || t.storageEngine == null) {
        engine = "Aria";
      } else if (t.storageEngine == StorageEngine.transactional) {
        engine = "Innodb";
      }
      sql = "create table " + checkToAddBackticks(t.table.tableName) + " (\n";
      final IMap<String, Column> columns = t.columns;
      String separator = "  ";
      for (IEntry<String, Column> column : columns) {
        sql += separator + toSql(column.value);
        separator = ",\n  ";
      }
      sql += ",";
      indexes = toSql(t.indexes.values());
    }
    sql += indexes + "\n) ENGINE=" + engine + "\n";
    return sql;
  }

  private String checkToAddBackticks(String name) {
    if (MariadbReservedWords.wordsHashSet.contains(name)) {
      return "`" + name + "`";
    }
    return name;
  }

  private String toSql(IList<Index> indexes) {
    IList<Column> columns = IArrayList.empty;
    String separtor = "";
    String sql = "";
    String references = "";
    boolean generateName = true;
    for (Index index : indexes) {
      for (IEntry<String, IndexedColumn> indexedColumn : index.indexedColumns) {
        columns = columns.add(indexedColumn.value.column);
      }
      if (index.type.equals(IndexType.primary)) {
        sql += separtor + "\n  primary key";
        generateName = false;
      } else if (index.type.equals(IndexType.secondary)) {
        sql += separtor + "\n  key";
      } else if (index.type.equals(IndexType.fulltext)) {
        sql += separtor + "\n  fulltext index";
      } else if (index.type.equals(IndexType.unique)) {
        sql += separtor + "\n  unique key";
      } else if (index.type.equals(IndexType.foreign)) {
        sql += separtor + "\n  foreign key";
//        references = toSql(index.fkReference); //TODO: Finish implementing foreign key with Index.
      }
      separtor = ",";
      sql += " " + toSqlIndexColumns(columns, generateName);
      generateName = true;
      columns = IArrayList.empty;
    }
    return sql;
  }

  //TODO: Add formatting to handle weird column names. For example item_id should become itemid.
  private String toSqlIndexColumns(IList<Column> columns, boolean generateName) {
    String sql = "";
    String indexName = "";
    String indexedColumns = "";
    String separator = "";
    for (Column column : columns) {
      indexedColumns += separator + checkToAddBackticks(column.columnName);
      separator = ",";
    }
    if (generateName) {
      indexName = DatabaseService.formatIndexName(indexedColumns) + "idx ";
    }
    sql += indexName + "(" + indexedColumns + ")";
    return sql;
  }

}

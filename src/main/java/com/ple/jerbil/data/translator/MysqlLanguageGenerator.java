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

public class MysqlLanguageGenerator implements LanguageGenerator {

  public static LanguageGenerator make() {
    return new MysqlLanguageGenerator();
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
  public Database fromSql(String dbCreateString) {
    return null;
  }

  @Override
  public TableContainer fromSql(String createTableString, Database db) {
    final String tableName = getTableNameFromSql(createTableString);
    final StorageEngine engine = getEngineFromSql(createTableString);
//FIXME: broken after changes to tables and datbases.
//    return Table.make(tableName, db, engine);
    return null;
  }

  @Override
  public Column fromSql(String createTableString, TableContainer table) {
    final String formattedTable = formatTable(createTableString);
    //TODO: Finish implementing.

    return null;
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

  private IList<Column> createColumnsFromTableString(String formattedTable) {
    IMap<String, String[]> indexedColumns = IHashMap.empty;
    IList<Column> columns = IArrayList.make();
    if (formattedTable.contains("PRIMARY KEY (")) {
      final int primaryIndex = formattedTable.indexOf("PRIMARY KEY (") + 13;
      final String[] primaryColumns = formattedTable.substring(primaryIndex, formattedTable.indexOf(")", primaryIndex))
          .split(",");
      indexedColumns = indexedColumns.put("PRIMARY KEY", primaryColumns);
    }
    if (formattedTable.contains("\n  KEY ")) {
      final int keyIndex = formattedTable.indexOf("\n  KEY ") + 7;
      final String refinedKeyValues = formattedTable.substring(keyIndex).replaceAll("^.*\\(", "");
      final String[] keyColumns = refinedKeyValues.substring(0, refinedKeyValues.indexOf(")\n")).split(",");
      indexedColumns = indexedColumns.put("KEY", keyColumns);
    }
    final String[] tableLines = formatToColumnLines(formattedTable);
    for (String tableLine : tableLines) {
      columns = columns.add(getColumnFromSql(tableLine, indexedColumns));
    }
    return columns;
  }

  private Column getColumnFromSql(String tableLine, IMap<String, String[]> indexedColumns) {
    tableLine = tableLine.stripLeading();
    final String colName = tableLine.replaceFirst(" .*", "");
    final DataSpec dataSpec = getDataSpecFromSql(tableLine);
    final Expression generatedFrom = getGeneratedFromSql(tableLine);
    boolean indexed = false;  //FIXME: Find out why item.name column is not becoming indexed in fromSql() even though it says KEY name (name) in existing table.
    boolean primary = false;
    if (indexedColumns.keys().contains("PRIMARY KEY")) {
      for (String column : indexedColumns.get("PRIMARY KEY")) {
        if (column.equals(colName)) {
          primary = true;
        }
      }
    }
    if (indexedColumns.keys().contains("KEY")) {
      for (String column : indexedColumns.get("KEY")) {
        if (column.equals(colName)) {
          indexed = true;
        }
      }
    }
/* //FIXME: broken after changes to tables and datbases.
    if (tableLine.contains("AUTO_INCREMENT")) {
      return NumericColumn.make(colName, dataSpec, indexed, primary, true, (NumericExpression) generatedFrom);
    } else if (dataSpec.dataType == DataType.integer || dataSpec.dataType == DataType.decimal || dataSpec.dataType == DataType.bigint || dataSpec.dataType == DataType.tinyint) {
      return NumericColumn.make(colName, dataSpec, indexed, primary, false, (NumericExpression) generatedFrom);
    } else if (dataSpec.dataType == DataType.varchar || dataSpec.dataType == DataType.enumeration) {
      return StringColumn.make(colName, dataSpec, indexed, primary);
    } else if (dataSpec.dataType == DataType.datetime) {
      return DateColumn.make(colName, dataSpec, indexed, primary);
    } else if (dataSpec.dataType == DataType.bool) {
      return BooleanColumn.make(colName, dataSpec, indexed, primary);
    } else {
      System.out.println("Failed to determine data type of this column:" + colName);
    }
*/
    return null;
  }

  private Expression getGeneratedFromSql(String tableLine) {
    //TODO: Implement this method.
    return null;
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

  private String[] formatToColumnLines(String formattedTable) {
    return formattedTable
        .replaceAll("\n\\) ENGINE=.*", "")
        .replaceAll("^CREATE TABLE .*\n", "")
        .replaceAll("\n\s+PRIMARY KEY .*", "")
        .replaceAll("\n\s+KEY .* \\(.*", "")
        .split("\n");
  }

  private String formatTable(String tableInfo) {
    return tableInfo.replaceAll("`", "");
  }

  private String toSql(SelectQuery selectQuery) {
    String sql = "";
    final IList<TableContainer> tableList;
    if (selectQuery.queryFlags != null) {
      if ((selectQuery.queryFlags.flags >> 7 & 1) == 1) {
        sql += "select distinct ";
      }
    } else {
      sql += "select ";
    }
    if (selectQuery.fromExpression != null) {
      tableList = selectQuery.fromExpression.tableList();
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
        IMap<SelectExpression, Order> transformedOrderBy = transformColumns(selectQuery.orderBy, tableList);
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
  private String toSqlOrderBy(IMap<SelectExpression, Order> orderBy) {
    String sql = "";
    String order = "";
    String separator = "";
    for (IEntry<SelectExpression, Order> entry : orderBy) {
      if (entry.value.equals(Order.ascending)) {
        order = " asc";
      } else {
        order = " desc";
      }
      sql += separator + toSql(entry.key) + order;
      separator = ", ";
    }
    return sql;
  }

  private IMap<SelectExpression, Order> transformColumns(IMap<SelectExpression, Order> orderBy,
                                                         IList<TableContainer> tableList) {
    IMap<SelectExpression, Order> transformedMap = IArrayMap.empty;
    for (IEntry<SelectExpression, Order> entry : orderBy) {
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
    if (fullHavingClause.endsWith(")")) {
      fullHavingClause = fullHavingClause.replaceAll("where \\(", "where ").replaceAll("\\)\\z", "");
    }
    return fullHavingClause;
  }

  //TODO: Make this work for both having and where.
  private String toSqlWhere(BooleanExpression where) {
    if (where == null) {
      return "";
    }
    String fullWhereClause = "where ";
    fullWhereClause += toSqlBooleanExpression(where);
    if (fullWhereClause.endsWith(")")) {
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
      boolExpString += toSql(((Regexp) booleanExpression).e1) + " regexp '" + ((Regexp) booleanExpression).e2.value + "'";
    } else if (booleanExpression instanceof NotRegexp) {
      boolExpString += toSql(((NotRegexp) booleanExpression).e1) + " not regexp '" + ((NotRegexp) booleanExpression).e2.value + "'";
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
      output = ((Column)e).columnName;
    } else {
      throw new RuntimeException("Unknown Expression ");
    }
    return output;
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
    try {
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
    } catch (Exception e) {
      //TODO: Ask if this is good practice to do these types of error messages and catching of exceptions in framework.
      System.out.println(e.getMessage());
    }
    return sql;
  }

  private String getNumericExpression(NumericExpression e) throws ClassNotFoundException {
    if (e instanceof NumericColumn) {
      return ((NumericColumn) e).columnName;
    } else if (e instanceof LiteralNumber) {
      return ((LiteralNumber) e).value.toString();
    } else {
      throw new ClassNotFoundException(
          "This class " + e.getClass() + " is not supported in getNumericExpression() method. Must be added in order to support it's use.");
    }
  }

  //TODO: Add support for specifying Null on a column. Leave not null off because that is default.
  public String toSql(Column column) {
/* FIXME: broken after changes to database/table system.

    String sql = column.getColumnName() + " ";
    String primary = "";
    String autoIncrement = "";
    String preciseScale = "";
    if (column.isPrimary()) {
      primary = " primary key";
    }
    if (column instanceof NumericColumn) {
      final NumericColumn numCol = (NumericColumn) column;
      if (numCol.dataSpec.preciseScale != null) {
        preciseScale = "(" + numCol.dataSpec.preciseScale[0] + ", " + numCol.dataSpec.preciseScale[1] + ")";
      }
      if (numCol.isAutoIncrement()) {
        autoIncrement = " auto_increment";
      }
      if (numCol.generatedFrom != null) {
        sql += numCol.dataSpec.getSqlName() + preciseScale + " as (" + toSqlArithmetic(sql, (ArithmeticExpression) numCol.generatedFrom) + ")";
      } else {
        sql += numCol.dataSpec.getSqlName() + preciseScale + " not null" + primary + autoIncrement;
      }
    } else if (column instanceof StringColumn) {
      final StringColumn strCol = (StringColumn) column;
      if (strCol.dataSpec.dataType == DataType.varchar) {
        sql += strCol.dataSpec.dataType.name() + "(" + strCol.dataSpec.size + ")" + " not null" + primary;
      } else if (strCol.dataSpec.dataType == DataType.enumeration) {
        final EnumSpec enumSpec = (EnumSpec) strCol.dataSpec;
        sql += "enum(";
        String separator = "'";
        for (String value : enumSpec.values) {
          sql += separator + value + "'";
          separator = ",'";
        }
        sql += ") not null" + primary;
      }
    }
    sql = sql.replaceAll("not null primary key", "primary key");
    return sql;
*/
    return null;
  }

  @Override
  public String toSql(Diff diff) {
    return null;
  }

  public String toSql(UpdateQuery updateQuery) {
    return null;
  }

  public String toSql(DeleteQuery deleteQuery) {
    return null;
  }

  public String toSql(InsertQuery insertQuery) {
    String sql = "insert into " + toSql(insertQuery.fromExpression) + "\n(";
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
/*
//FIXME: broken after changes to tables and datbases.
    if (createQuery.db != null) {
      return "create database " + createQuery.db.databaseName;
    }
    String engine = "";
    if (createQuery.fromExpression.tableList().get(0).storageEngine == StorageEngine.simple) {
      engine = "Aria";
    } else if (createQuery.fromExpression.tableList().get(0).storageEngine == StorageEngine.transactional) {
      engine = "Innodb";
    }
    String sql = "create table " + toSql(createQuery.fromExpression) + " (\n";
    final IList<Column> columns = createQuery.fromExpression.tableList().get(0).columns;
    String separator = "  ";
    for (int i = 0; i < columns.toArray().length; i++) {
      sql += separator + toSql(columns.get(i));
      separator = ",\n  ";
    }
    String indexes = gatherIndexes(columns);
    if (indexes != "") {
      if (indexes.contains("primary")) {
        sql = sql.replaceAll(" primary key", "");
      }
    }
    sql += indexes + "\n) ENGINE=" + engine + "\n";
    return sql;
*/
    return null;
  }

  private String gatherIndexes(IList<Column> columns) {

/*  //FIXME: broken after change to new database/table system.
    String multiIndex = "";
    String separator = "";
    int primaryCount = 0;
    int indexCount = 0;
    String indexedColumns = "";
    for (Column column : columns) {
      if (column.isPrimary()) {
        primaryCount++;
      } else if (column.isIndexed()) {
        indexCount++;
        indexedColumns += separator + column.getColumnName();
        separator = ",";
      }
    }
    separator = "";
    if (primaryCount > 0) {
      multiIndex += ",\n  primary key (";
      for (Column column : columns) {
        if (column.isPrimary()) {
          multiIndex += separator + column.getColumnName();
          separator = ",";
        }
      }
      multiIndex += ")";
    }
    if (indexCount > 0) {
      multiIndex += ",\n  key " + generateIndexName(indexedColumns) + "_idx (";
      multiIndex += indexedColumns;
      multiIndex += ")";
    }
    return multiIndex;
*/
    return null;
  }

  private String generateIndexName(String indexedColumns) {
    return indexedColumns.replaceAll(",", "_")
        .replaceAll("[aeiou]", "")
        .replaceAll("([a-z])\\1*", "$1");
  }

}

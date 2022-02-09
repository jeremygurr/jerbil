package com.ple.jerbil.data.query;

import com.ple.jerbil.data.*;
import com.ple.jerbil.data.selectExpression.AliasedExpression;
import com.ple.jerbil.data.selectExpression.Column;
import com.ple.jerbil.data.selectExpression.CountAgg;
import com.ple.jerbil.data.selectExpression.booleanExpression.BooleanExpression;
import com.ple.util.IArrayList;
import com.ple.util.IList;

import java.util.Objects;


/**
 * Table is a database Object which contains columns.
 */
@Immutable
public class Table extends FromExpression implements TableContainer {

  public final StorageEngine engine;
  public final String name;
  public IList<Column> columns;
  public static Table[] emptyArray = new Table[0];

  protected Table(String name) {
    this(StorageEngine.simple, name, IArrayList.make());
  }

  protected Table(StorageEngine engine, String name, IList<Column> columns) {
    this.engine = engine;
    this.name = name;
    this.columns = columns;
//    this.columns = IArrayList.make(Column.make(this));
  }

  public static Table make(StorageEngine engine, String name, IList<Column> columns) {
    return new Table(engine, name, columns);
  }

  public String toSql() {
    return CreateQuery.make(this).toSql();
  }

  public static Table fromSql(String showCreateTable) {
    if (DataGlobal.bridge == null) {
      throw new NullPointerException("Global.sqlGenerator not set.");
    }
    return fromSql(DataGlobal.bridge.getGenerator(), showCreateTable);
  }

  public static Table fromSql(LanguageGenerator generator, String showCreateTable) {
    return generator.fromSql(showCreateTable);
  }

  public QueryWithFrom where(BooleanExpression condition) {
    return QueryWithFrom.make(this).where(condition);
  }

  public QueryWithFrom join(FromExpression... tables) {
    FromExpression result = this;
    for (FromExpression table : tables) {
      result = Join.make(result, table);
    }
    return QueryWithFrom.make(result);
  }

  public CreateQuery create() {
    return CreateQuery.make(this);
  }

  public Table set(Column column) {
    return null;
  }

  public Table remove(Column column) {
    return null;
  }

  public PartialInsertQuery insert() {
    return PartialInsertQuery.make(this);
  }

  public CompleteQuery select(CountAgg agg) {
    return SelectQuery.make(this, IArrayList.make(agg));
  }

  public SelectQuery select(AliasedExpression... aliasedExpressions) {
    return SelectQuery.make(this, IArrayList.make(aliasedExpressions));
  }

  @Override
  protected void diffJoin() {
  }

  @Override
  public IList<Table> tableList() {
    return IArrayList.make(this);
  }

  public void add(Column... columnArr) {
    for (Column column : columnArr) {
      columns = columns.add(column);
    }
  }

  public void add(Column column) {
    final IList<Column> newColumns = columns.add(column);
    columns = newColumns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Table)) return false;
    Table table = (Table) o;
    return engine == table.engine && name.equals(table.name) && columns.equals(table.columns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(engine, name, columns);
  }

  @Override
  public String toString() {
    return "Table{" +
      "name='" + name + '\'' +
      ", columns=" + columns +
      ", engine=" + engine +
      '}';
  }

  @Override
  public Table getTable() {
    return this;
  }

}

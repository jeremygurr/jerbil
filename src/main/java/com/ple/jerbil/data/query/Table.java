package com.ple.jerbil.data.query;

import com.ple.jerbil.data.DelayedImmutable;
import com.ple.jerbil.data.StorageEngine;
import com.ple.jerbil.data.selectExpression.*;
import com.ple.jerbil.data.selectExpression.booleanExpression.BooleanExpression;
import com.ple.util.IArrayList;
import com.ple.util.IHashMap;
import com.ple.util.IList;
import com.ple.util.IMap;


/**
 * Table is a database Object which contains columns.
 */
@DelayedImmutable
public class Table extends FromExpression {

  public final StorageEngine engine;
  public final String name;
  public final IMap<String, Column> columns;

  protected Table(String name) {
    this(StorageEngine.simple, name, IHashMap.empty);
  }

  protected Table(StorageEngine engine, String name, IMap<String, Column> columns) {
    this.engine = engine;
    this.name = name;
    this.columns = columns;
  }

  private static Table make(StorageEngine engine, String name, IMap<String, Column> columns) {
    return new Table(engine, name, columns);
  }

  public String toSql() {
    return "create table " + name + " (" + ")";
  }

  public QueryWithFrom where(BooleanExpression condition) {
    return QueryWithFrom.make(this).where(condition);
  }

  public CompleteQuery join(Table... tables) {
    return null;
  }

  public CompleteQuery create() {
    return null;
  }

  public Table set(Column column) {
    return null;
/*
    final IMap<String, Column> newColumns = columns.put(column.name, column);
    return new Table(engine, name, newColumns);
*/
  }

  public Table remove(Column column) {
    return null;
/*
    final IMap<String, Column> newColumns = columns.remove(column.name);
    return new Table(engine, name, newColumns);
*/
  }

  public PartialInsertQuery insert() {
    return PartialInsertQuery.make(this);
  }

  public CompleteQuery select(CountAgg agg) {
    return null;
  }

  public CompleteQuery select(Column column) {
    return null;
  }

  public CompleteQuery select(AliasedExpression... aliasedExpressions) {
    return null;
  }

  @Override
  protected void diffJoin() {
  }

}

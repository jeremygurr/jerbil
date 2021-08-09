package com.ple.jerbil.sql.expression;

import com.ple.jerbil.sql.DataSpec;
import com.ple.jerbil.sql.DelayedImmutable;
import com.ple.jerbil.sql.fromExpression.Table;

@DelayedImmutable
public class Column extends Expression {

  public final String name;
  public final Table table;
  public final DataSpec dataSpec;
  public final boolean indexed;
  private final boolean primary;

  protected Column(String name, Table table, DataSpec dataSpec, boolean indexed, boolean primary) {
    this.name = name;
    this.table = table;
    this.dataSpec = dataSpec;
    this.indexed = indexed;
    this.primary = primary;
  }

  public static PartialColumn make(String name, Table table) {
    return new PartialColumn(name, table, null, false, false);
  }

  public Column primary() {
    return null;
  }

  public Column varchar(int size) {
    return null;
  }

  public Column indexed() {
    return null;
  }

  public Column varchar() {
    return null;
  }

}

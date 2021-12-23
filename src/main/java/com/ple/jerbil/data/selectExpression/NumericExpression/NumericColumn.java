package com.ple.jerbil.data.selectExpression.NumericExpression;

import com.ple.jerbil.data.DataSpec;
import com.ple.jerbil.data.DataType;
import com.ple.jerbil.data.query.Table;
import com.ple.jerbil.data.selectExpression.Column;
import com.ple.jerbil.data.selectExpression.Expression;
import com.ple.jerbil.data.selectExpression.booleanExpression.Equals;
import com.ple.jerbil.data.selectExpression.booleanExpression.GreaterThan;

public class NumericColumn extends NumericExpression implements Column<NumericColumn> {

  public final String name;
  public final Table table;
  public final DataSpec dataSpec;
  public final boolean indexed;
  public final boolean primary;

  protected NumericColumn(String name, Table table, DataSpec dataSpec, boolean indexed, boolean primary) {
    this.name = name;
    this.table = table;
    this.dataSpec = dataSpec;
    this.indexed = indexed;
    this.primary = primary;
  }

  public static Column make(String name, Table table, int size) {
    final NumericColumn numericColumn = new NumericColumn(name, table, DataSpec.make(DataType.integer, size), false, false);
    table.add(numericColumn);
    return numericColumn;
  }

  public static NumericColumn make(String name, Table table) {
    final NumericColumn numericColumn = new NumericColumn(name, table, DataSpec.make(DataType.integer), false, false);
    table.add(numericColumn);
    return numericColumn;
  }

  public static NumericColumn make(String name, Table table, boolean primary) {
    final NumericColumn numericColumn = new NumericColumn(name, table, DataSpec.make(DataType.integer), false, primary);
    table.add(numericColumn);
    return numericColumn;
  }

  public static Column make(String name, Table table, Boolean indexed, Boolean primary) {
    final NumericColumn numericColumn = new NumericColumn(name, table, DataSpec.make(DataType.integer), indexed, primary);
    table.add(numericColumn);
    return numericColumn;
  }

  public static Column make(String name, Table table, DataSpec dataSpec, Boolean indexed, Boolean primary) {
    final NumericColumn numericColumn = new NumericColumn(name, table, dataSpec, indexed, primary);
    table.add(numericColumn);
    return numericColumn;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Table getTable() {
    return this.table;
  }

  @Override
  public NumericColumn primary() {
    return new NumericColumn(this.name, this.table, this.dataSpec, this.indexed, true);
  }

  @Override
  public NumericColumn indexed() {
    return new NumericColumn(this.name, this.table, this.dataSpec, true, this.primary);
  }

  public GreaterThan isGreaterThan(Expression value) {
    return GreaterThan.make(this, value);
  }

  public Equals eq(Expression value) {
    return Equals.make(this, value);
  }

}
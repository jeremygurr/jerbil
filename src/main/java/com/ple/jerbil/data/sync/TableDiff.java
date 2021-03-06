package com.ple.jerbil.data.sync;

import com.ple.util.Immutable;
import com.ple.jerbil.data.Index;
import com.ple.jerbil.data.StorageEngine;
import com.ple.jerbil.data.query.TableContainer;
import com.ple.jerbil.data.selectExpression.Column;

@Immutable
public class TableDiff implements Diff<TableContainer> {

  public static Diff<TableContainer>[] empty = new TableDiff[0];
  public final ScalarDiff<String> tableName;
  public final VectorDiff<Column, ColumnDiff> columns;
  public final VectorDiff<Index, IndexDiff> indexes;
  public final ScalarDiff<StorageEngine> storageEngine;
//  public final ScalarDiff<Expression> constraint;
//  public final ScalarDiff<String> comment;

  protected TableDiff(ScalarDiff<String> tableName, VectorDiff<Column, ColumnDiff> columns,
                      VectorDiff<Index, IndexDiff> indexes, ScalarDiff<StorageEngine> storageEngine) {
    this.tableName = tableName;
    this.columns = columns;
    this.indexes = indexes;
    this.storageEngine = storageEngine;
//    this.constraint = constraint;
//    this.comment = comment;
  }

  public static TableDiff make(ScalarDiff<String> name, VectorDiff<Column, ColumnDiff> columns,
                               VectorDiff<Index, IndexDiff> indexes, ScalarDiff<StorageEngine> storageEngine) {
    return new TableDiff(name, columns, indexes, storageEngine);
  }

  @Override
  public int getTotalDiffs() {
    return 0;
  }

  @Override
  public TableDiff filter(DdlOption ddlOption) {
    VectorDiff<Column, ColumnDiff> newColumns = null;
    ScalarDiff<StorageEngine> newEngine = null;
    VectorDiff<Index, IndexDiff> newIndexes = null;
    ScalarDiff<String> newTableName = null;
    if (columns != null) {
      newColumns = columns.filter(ddlOption);
    }
    if (storageEngine != null) {
       newEngine = storageEngine.filter(ddlOption);
    }
    if (indexes != null) {
       newIndexes = indexes.filter(ddlOption);
    }
    if (tableName != null) {
       newTableName = tableName.filter(ddlOption);
    }
    return new TableDiff(newTableName, newColumns, newIndexes, newEngine);
  }

}

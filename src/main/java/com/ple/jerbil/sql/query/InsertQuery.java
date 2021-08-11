package com.ple.jerbil.sql.query;

import com.ple.jerbil.sql.Immutable;
import com.ple.jerbil.sql.fromExpression.FromExpression;
import com.ple.jerbil.sql.expression.Column;
import com.ple.jerbil.sql.expression.Expression;
import com.ple.util.IList;
import com.ple.util.IMap;

import java.util.List;

@Immutable
public class InsertQuery extends CompleteQuery {
  public final IList<IMap<Column, Expression>> dataToInsert;

  protected InsertQuery(IList<IMap<Column, Expression>> dataToInsert, FromExpression fromExpression) {
    super(fromExpression);
    this.dataToInsert = dataToInsert;
  }

  public static InsertQuery make(IList<IMap<Column, Expression>> dataToInsert, FromExpression fromExpression) {
    return new InsertQuery(dataToInsert, fromExpression);
  }

  public InsertQuery set(Column column, Expression expression) {
//    IList<IMap<Column, Expression>> newDataToInsert = dataToInsert;
//    Map<Column, Expression> map;
//    if (newDataToInsert.size() == 0 ) {
//      map = new HashMap<>();
//      newDataToInsert.add(map);
//    } else {
//      map = newDataToInsert.get(newDataToInsert.size() - 1);
//    }
//    map.put(column, expression);
//    return InsertQuery.make(newDataToInsert, table, type);
    return null;
  }

  public InsertQuery set(List<Column> columns, List<List<String>> values) {
    return null;
  }

}

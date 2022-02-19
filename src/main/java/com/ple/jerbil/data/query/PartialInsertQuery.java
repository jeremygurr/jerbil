package com.ple.jerbil.data.query;

import com.ple.jerbil.data.selectExpression.Column;
import com.ple.jerbil.data.selectExpression.Expression;
import com.ple.jerbil.data.selectExpression.Literal;
import com.ple.jerbil.data.selectExpression.SelectExpression;
import com.ple.jerbil.data.selectExpression.booleanExpression.BooleanExpression;
import com.ple.util.IArrayList;
import com.ple.util.IHashMap;
import com.ple.util.IList;
import com.ple.util.IMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PartialInsertQuery extends PartialQueryWithValues {

  protected PartialInsertQuery(@Nullable BooleanExpression where, @Nullable FromExpression fromExpression, @Nullable QueryType queryType, @Nullable IList<SelectExpression> select, @Nullable IList<SelectExpression> groupBy, @Nullable IList<SelectExpression> orderBy, @Nullable IList<BooleanExpression> having, @Nullable Limit limit, @Nullable IList<IMap<Column, Expression>> set, @Nullable boolean mayInsert, @Nullable boolean mayReplace, @Nullable boolean triggerDeleteWhenReplacing, @Nullable boolean mayThrowOnDuplicate) {
    super(where, fromExpression, queryType, select, groupBy, orderBy, having, limit, set, mayInsert, mayReplace, triggerDeleteWhenReplacing, mayThrowOnDuplicate);
  }

  public static PartialInsertQuery make(Table table) {
    return new PartialInsertQuery(null, table, null, null, null, null, null, null, null, false, false, false, false);
  }

  public CompleteQuery set(List<Column> columns, List<List<String>> values) {
    IList<IMap<Column, Expression>> records = IArrayList.make();
    for (int i = 0; i < values.size(); i++) {
      for (int j = 0; j < columns.size(); j++) {
        if (!(i >= records.toArray().length)) {
          records.toArray()[i] = records.toArray()[i].put(columns.get(j), Literal.make(values.get(i).get(j)));
        } else {
          final IMap<Column, Expression> record = IHashMap.make(columns.get(j), Literal.make(values.get(i).get(j)));
          records = records.add(record);
        }
      }
    }
    return InsertQuery.make(records, fromExpression);
  }


}

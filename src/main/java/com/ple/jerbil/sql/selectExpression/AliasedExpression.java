package com.ple.jerbil.sql.selectExpression;

import com.ple.jerbil.sql.Immutable;
import com.ple.jerbil.sql.query.CompleteQuery;

/**
 * AliasedExpression is any expression which is given an alias. For example:
 * select 2+2 as result, col1 + col2 as total, concat(first_name," ", last_name) as full_name; etc...
 * Sometimes tables, joins, and subqueries are given aliases as well.
 */
@Immutable
public class AliasedExpression implements SelectExpression {
  public final String name;

  protected AliasedExpression(String name) {
    this.name = name;
  }

  public static AliasedExpression make(String name) {
    return new AliasedExpression(name);
  }

  @Override
  public CompleteQuery select() {
    return null;
  }

}

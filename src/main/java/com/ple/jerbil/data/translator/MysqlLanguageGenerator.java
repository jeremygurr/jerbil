package com.ple.jerbil.data.translator;

import com.ple.jerbil.data.LanguageGenerator;
import com.ple.jerbil.data.query.*;
import com.ple.jerbil.data.selectExpression.*;
import com.ple.jerbil.data.selectExpression.NumericExpression.*;
import com.ple.jerbil.data.selectExpression.booleanExpression.*;
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
      case select -> {
        sql = toSql((SelectQuery) completeQuery);
      }
      case delete -> {
        sql = toSql((DeleteQuery) completeQuery);
      }
      case update -> {
        sql = toSql((UpdateQuery) completeQuery);
      }
      default -> throw new IllegalStateException("Unexpected value: " + completeQuery.queryType);
    }
    return sql;
  }

  private String toSql(SelectQuery selectQuery) {
    final IList<Table> tableList = selectQuery.fromExpression.tableList();
    IList<SelectExpression> transformedSelect = transformColumns(selectQuery.select, tableList);
    String sql = "select " + toSqlSelect(transformedSelect) + "\n";
    if (selectQuery.fromExpression != null) {
      sql += toSql(selectQuery.fromExpression) + "\n";
    }
    if (selectQuery.where != null) {
      BooleanExpression transformedWhere = transformColumns(selectQuery.where, tableList);
      sql += toSqlWhere(transformedWhere) + "\n";
    }
    if (selectQuery.groupBy != null) {
      IList<SelectExpression> transformedGroupBy = transformColumns(selectQuery.groupBy, tableList);
      sql += "group by " + toSqlSelect(transformedGroupBy) + "\n";
    }
    return sql;
  }

  //TODO: Make transformColumns work for ArithmeticExpressions or BooleanExpressions that contain columns
  private IList<SelectExpression> transformColumns(IList<SelectExpression> select, IList<Table> tableList) {
    final SelectExpression[] selectArr = select.toArray();
    Boolean requiresTable = false;
    for (int i = 0; i < selectArr.length; i++) {
      if (selectArr[i] instanceof Column && !(selectArr[i] instanceof QueriedColumn)) {
        final Column column = (Column) selectArr[i];
        for (Table table : tableList) {
          if (table.columns.get(column.getName()) == column && column.getTable() != table) {
            requiresTable = true;
          }
        }
        selectArr[i] = new QueriedColumn((Column) selectArr[i], requiresTable);
        requiresTable = false;
      }
    }
    return select;
  }

  private BooleanExpression transformColumns(BooleanExpression be, IList<Table> tableList) {
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
      //FIXME: find out if there is a better way to make IArrayList and actually add values to it while remaining immutable.
      IList<BooleanExpression> list = IArrayList.make();
      for (BooleanExpression condition : and.conditions) {
        list = list.add(transformColumns(condition, tableList));
      }
      result = and.make(list);
    } else if (be instanceof Equals) {
      Equals eq = (Equals) be;
      final Expression e1 = transformColumns(eq.e1, tableList);
      final Expression e2 = transformColumns(eq.e2, tableList);
      result = Equals.make(e1, e2);
    } else if (be instanceof GreaterThan) {
      GreaterThan gt = (GreaterThan) be;
      final Expression e1 = transformColumns(gt.e1, tableList);
      final Expression e2 = transformColumns(gt.e2, tableList);
      result = GreaterThan.make(e1, e2);
    } else {
      result = be;
    }
    return result;
  }

  private Expression transformColumns(Expression e, IList<Table> tableList) {
    // Expression e could be a Column, And/Or, but it could also be any expression like Literal.String etc...
    // All we care about is whether it's a column or not. If it's a column we tranform into QueriedColumn after checking
    // checking if a duplicate name exists in any other tables.
    // Then we return either the unchanged expression, or in the case of columns we return a QueriedColumn.
    Boolean requiresTable = false;
    Column col;
    if (e instanceof Column) {
      col = (Column) e;
      for (Table table : tableList) {
        if (table.columns.get(col.getName()) != null && table.name != col.getTable().name) {
          requiresTable = true;
        }
      }
    } else {
      return e;
    }
    return new QueriedColumn(col, requiresTable);
  }

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

  private String toSql(Expression e) {
    final String output;
    if (e instanceof Column) {
      //TODO: Check if column name has space and if so put `backticks` around it.
      final QueriedColumn s = (QueriedColumn) e;
      if (s.requiresTableName) {
        output = s.getTable().name + "." + s.getName();
      } else {
        output = s.getName();
      }
    } else if (e instanceof LiteralString) {
      final LiteralString litStr = (LiteralString) e;
      output = "'" + litStr.value + "'";
    } else if (e instanceof LiteralNumber) {
      final LiteralNumber n = (LiteralNumber) e;
      output = n.value.toString();
    } else {
      throw new RuntimeException("Unknown Expression: " + e.getClass().getName());
    }
    return output;
  }

  private String toSql(FromExpression fromExpression) {
    String fullFromExpressionList = "from ";
    if (fromExpression instanceof Table) {
      Table table = (Table) fromExpression;
      fullFromExpressionList += table.name;
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
      Table t2 = (Table) join.fe2;
      //FIXME: using needs to choose the name of the field based on primary key of the fromexpression and if a matching field exists in compared fromexpression. If not, this needs to throw an error.
      result += "\ninner join " + t2.name + " using (" + t2.name + "Id)";
    } else {
      Table t1 = (Table) join.fe1;
      Table t2 = (Table) join.fe2;
      result += t1.name + "\ninner join " + t2.name + " using (" + t1.name + "Id)";
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
        final Column column = ((QueriedColumn<?>) selectArr[i]).column;
        if (column instanceof NumericColumn) {
          fullSelectList += ((NumericColumn) column).name;
        } else if (column instanceof StringColumn) {
          fullSelectList += ((StringColumn) column).name;
        } else if (column instanceof LiteralNumber) {
          // TODO: Handle Literal values here.
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
            fullSelectList += column.getName() + " as " + ae.alias;
          } else if (ae.expression instanceof ArithmeticExpression) {
            final ArithmeticExpression arExp = (ArithmeticExpression) ae.expression;
            fullSelectList += toSqlArithmetic(fullSelectList, arExp) + " as " + ae.alias;
          }
        }
      }
      if (selectArr.length != i + 1) {
        fullSelectList += ", ";
      }
    }
    return fullSelectList;
  }

  private String toSqlArithmetic(String fullSelectList, ArithmeticExpression arExp) {
    String operator = "";
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
        fullSelectList += toSqlArithmetic(fullSelectList, (ArithmeticExpression) arExp.e1);
        fullSelectList += operator + getNumericExpression((NumericExpression) arExp.e2);
      } else if (arExp.e2 instanceof ArithmeticExpression && !(arExp.e1 instanceof ArithmeticExpression)) {
        fullSelectList += getNumericExpression((NumericExpression) arExp.e1) + operator + "(" + toSqlArithmetic(fullSelectList, (ArithmeticExpression) arExp.e2) + ")";
      } else if (arExp.e1 instanceof ArithmeticExpression && arExp.e2 instanceof ArithmeticExpression) {
        fullSelectList += "(" + toSqlArithmetic(fullSelectList, (ArithmeticExpression) arExp.e1) + ")" + operator + "(" + toSqlArithmetic(fullSelectList, (ArithmeticExpression) arExp.e2) + ")";
      } else {
        fullSelectList += getNumericExpression((NumericExpression) arExp.e1) + operator + getNumericExpression((NumericExpression) arExp.e2);
      }
      return fullSelectList;
    } catch (Exception e) {
      //TODO: Ask if this is good practice to do these types of error messages and catching of exceptions in framework.
      System.out.println(e.getMessage());
    }
    return fullSelectList;
  }

  private String getNumericExpression(NumericExpression e) throws ClassNotFoundException {
    if (e instanceof NumericColumn) {
      return ((NumericColumn) e).name;
    } else if (e instanceof LiteralNumber) {
      return ((LiteralNumber) e).value.toString();
    } else {
      throw new ClassNotFoundException("This class " + e.getClass() + " is not supported in getNumericExpression() method. Must be added in order to support it's use.");
    }
  }

  public String toSql(UpdateQuery updateQuery) {
    return null;
  }

  public String toSql(DeleteQuery deleteQuery) {
    return null;
  }

}

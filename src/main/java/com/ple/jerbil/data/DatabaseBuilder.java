package com.ple.jerbil.data;

import com.ple.jerbil.data.query.TableContainer;
import com.ple.jerbil.data.selectExpression.Column;
import com.ple.jerbil.data.selectExpression.NumericExpression.NumericColumn;
import com.ple.util.*;

import java.lang.reflect.*;

@Immutable
public class DatabaseBuilder {

  //FIXME: Now I just need this to create the indexes and autoIncrementColumn fields for each CustomTableContainer using
  // BuildingHints from each column.
  public static <T extends DatabaseContainer> T generate(Class<T> customDbContainerClass, String dbName) {
    Constructor<?>[] customTblConstructors = null;
    Parameter[] customTblConstructorParams = null;
    IList<Class<?>> customTblConstructorParamClasses = IArrayList.empty;
    Constructor<?> customTblConstructor = null;
    AutoIncIndex tblAutoIndexes = null;
    Field[] customTblContainerFields = null;
    Object fieldValue = null;
    IList<Object> customTblArgs = IArrayList.empty;
    T t = null;
    try {
      Parameter[] parameters = null;
      final Database db = Database.make(dbName);
      final Method[] declaredMethods = customDbContainerClass.getDeclaredMethods();
      for (Method declaredMethod : declaredMethods) {
        if (declaredMethod.getName().equals("make")) {
          parameters = declaredMethod.getParameters();
        }
      }
      IList<Object> customDbContainerArgs = IArrayList.make(db);
      IList<Class<?>> customDbContainerParams = IArrayList.make(db.getClass());
      for (int i = 0; i < parameters.length; i++) {
        final Class<?> type = parameters[i].getType();
        if (type.getSuperclass() != null) {
          if (type.getSuperclass().equals(TableContainer.class)) {
            final Method method = type.getMethod("make", Database.class);
            TableContainer tc = (TableContainer) method.invoke(null, db);
            IList<Column> columns = IArrayList.empty;
            customTblContainerFields = tc.getClass().getDeclaredFields();
            customTblArgs = IArrayList.make(tc.table, tc.columns);
            for (Field field : customTblContainerFields) {
              field.setAccessible(true);
              fieldValue = field.get(tc);
              if (fieldValue instanceof Column) {
                customTblArgs = customTblArgs.add(fieldValue);
                columns = columns.add((Column) fieldValue);
              }
            }
            tblAutoIndexes = getColumnAttributes(columns);
            if (tblAutoIndexes.indexes != null || tblAutoIndexes.autoIncrementColumn != null) {
              customTblConstructors = type.getDeclaredConstructors();
              customTblConstructorParams = customTblConstructors[0].getParameters();
              customTblConstructorParamClasses = IArrayList.make();
              for (Parameter param : customTblConstructorParams) {
                customTblConstructorParamClasses = customTblConstructorParamClasses.add(param.getType());
              }
              customTblConstructor = type.getDeclaredConstructor(customTblConstructorParamClasses.toArray());
            }
            customTblArgs = customTblArgs.add(tblAutoIndexes.indexes);
            customTblArgs = customTblArgs.add(tblAutoIndexes.autoIncrementColumn);
            // replace customTableContainer with new instance which includes the indexes and autoIncrementColumn arguments.
            customTblConstructor.setAccessible(true);
            tc = (TableContainer) customTblConstructor.newInstance(customTblArgs.toArray());
            customDbContainerArgs = customDbContainerArgs.add(tc);
            customDbContainerParams = customDbContainerParams.add(type);
          }
        }
      }
      t = (T) customDbContainerClass.getMethod("make", customDbContainerParams.toArray())
          .invoke(null, customDbContainerArgs.toArray());
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
    }
    return t;
  }

  private static AutoIncIndex getColumnAttributes(IList<Column> columns) {
    IList<Index> indexes = IArrayList.make();
    NumericColumn autoIncColumn = null;
    Index primary = null;  //This only works for multi-column primary keys. All other indexes I have to make a way for
    // users to specify if it is part of another index, or if it is different index.
    for (Column column : columns) {
      if ((column.hints.flags >> 8 & 1) == 1) {
        indexes = indexes.add(Index.make(IndexType.primary, column));
        autoIncColumn = (NumericColumn) column;
      } else if ((column.hints.flags >> 15 & 1) == 1) {
        if (primary == null) {
          primary = Index.make(IndexType.primary, column);
          indexes = indexes.add(primary);
        } else {
          indexes = indexes.remove(primary);
          primary = Index.make(IndexType.primary, primary.columns.add(column).toArray());
          indexes = indexes.add(primary);
        }
      }
      if ((column.hints.flags >> 12 & 1) == 1) {
        indexes = indexes.add(Index.make(IndexType.fulltext, column));
      }
      if ((column.hints.flags >> 13 & 1) == 1) {
        indexes = indexes.add(Index.make(IndexType.foreign, column));
      }
      if ((column.hints.flags >> 14 & 1) == 1) {
        indexes = indexes.add(Index.make(IndexType.secondary, column));
      }
    }
    return new AutoIncIndex(indexes, autoIncColumn);
  }

  @Immutable
  private static class AutoIncIndex {
    public final IList<Index> indexes;
    public final NumericColumn autoIncrementColumn;

    private AutoIncIndex(IList<Index> indexes,
                         NumericColumn autoIncrementColumn) {
      this.indexes = indexes;
      this.autoIncrementColumn = autoIncrementColumn;
    }
  }
}

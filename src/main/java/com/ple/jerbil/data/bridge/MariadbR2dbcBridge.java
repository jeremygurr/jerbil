package com.ple.jerbil.data.bridge;

import com.ple.jerbil.data.*;
import com.ple.jerbil.data.reactiveUtils.*;
import com.ple.util.Immutable;
import com.ple.jerbil.data.query.TableContainer;
import com.ple.jerbil.data.translator.LanguageGenerator;
import com.ple.jerbil.data.translator.MariadbLanguageGenerator;
import com.ple.util.*;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mariadb.r2dbc.MariadbConnectionConfiguration;
import org.mariadb.r2dbc.MariadbConnectionFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Immutable
public class MariadbR2dbcBridge implements DataBridge {

  public final LanguageGenerator generator = MariadbLanguageGenerator.make();
  public final String driver;
  public final String host;
  public final int port;
  public final String username;
  public final String password;
  @Nullable public final String database;
  public final ConnectionPool pool;

  protected MariadbR2dbcBridge(String driver, String host, int port, String username, String password,
                               @Nullable String database, ConnectionPool pool) {
    this.driver = driver;
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.database = database;
    this.pool = pool;
  }

  public static MariadbR2dbcBridge make(String driver, String host, int port, String username, String password,
                                        String database, ConnectionPool pool) {
    return new MariadbR2dbcBridge(driver, host, port, username, password, database, pool);
  }

  public static DataBridge make(String driver, String host, int port, String username, String password,
                                String database) {
    return new MariadbR2dbcBridge(
        driver, host, port, username, password, database, startConnection(host, port, username, password, database));
  }

  public static DataBridge make(String driver, String host, int port, String username, String password) {
    return new MariadbR2dbcBridge(
        driver, host, port, username, password, null, startConnection(host, port, username, password, null));
  }

  public static DataBridge make(String host, int port, String username, String password) {
    return new MariadbR2dbcBridge(
        "r2dbc:mariadb", host, port, username, password, null, startConnection(host, port, username, password, null));
  }

  @Override
  public LanguageGenerator getGenerator() {
    return generator;
  }

  public ReactiveMono<MariadbR2dbcBridge> getConnectionPool() {
    if (pool == null) {
      return ReactiveMono.make(createConnectionPool());
    }
    if (pool.getMetrics().isPresent()) {
      if (pool.getMetrics().get().acquiredSize() == pool.getMetrics().get().getMaxAllocatedSize()) {
        return ReactiveMono.make(createConnectionPool());
      }
    }
    return ReactiveMono.make(Mono.just(this));
  }

  @Override
  public ReactiveFlux<DbResult> execute(String sql) {
    return this.getConnectionPool()
        .map(bridge -> bridge.pool)
//        .log()
        .flatMap(pool -> pool.create())
        .log()
        .map(conn -> conn.createStatement(sql))
        .flatMapMany(statement -> DbResult.make(statement.execute()));
  }

  @Override
  public ReactiveWrapper<DbResult> execute(ReactiveWrapper<String> toSql) {
    return null;
  }

  public ReactiveMono<DatabaseContainer> getDb(String name) {
    // example of printing out each result table from resultList.
//    execute("select * from user").unwrapFlux()
//        .flatMap(dbResult -> Flux.fromIterable(dbResult.resultList))
//        .doOnNext(resultTable -> System.out.println(resultTable));

    Database database = Database.make(name);
    return ReactiveMono.make(execute("show create database " + name).unwrapFlux()
        .flatMap(result ->
            Flux.just((String[]) result.getColumn("create database"))
                .next()
                .flatMap(dbCreateStr -> execute("use " + name + "; show tables")
                    .flatMap(result1 -> Flux.just((String[]) result.getColumn("tables_in_" + name)))
                    .unwrapFlux().collectList()
                    .map(tableNameList -> {
                      final IList<String> tableNameIList = IArrayList.make(tableNameList.toArray(new String[0]));
                      return convertToDbContainer(tableNameIList, name, database);
                    }))).next()).next();
  }

  @Override
  public <T extends DbRecord, I extends DbRecordId> I save(T record) {
    return null;
  }

  @NotNull
  private ReactiveMono<DatabaseContainer> convertToDbContainer(IList<String> tblNameList, String dbName,
                                                               Database database) {
    return ReactiveMono.make(Flux.fromIterable(tblNameList)
        .flatMap(tblName -> execute("use " + dbName + "; show create table " + tblName)
            .flatMap(dbResult -> Flux.just((String[]) dbResult.getColumn("create table")))
            .map(tblCreateStr -> generator.getTableFromSql(tblCreateStr, database))
            .unwrapFlux()
            .collectList()
            .map(tableContainers -> (IList<TableContainer>) (IList) IArrayList.make(tableContainers.toArray()))
            .map(tables -> convertListToIMap(tables))
            .map(tablesIList -> DatabaseContainer.make(database, tablesIList))).next());
  }

  private IMap<String, TableContainer> convertListToIMap(IList<TableContainer> tables) {
    IMap<String, TableContainer> map = IArrayMap.empty;
    for (TableContainer table : tables) {
      map = map.put(table.table.tableName, table);
    }
    return map;
  }

  private Mono<MariadbR2dbcBridge> createConnectionPool() {
    return Mono.just(startConnection(host, port, username, password, database))
        .map(pool -> MariadbR2dbcBridge.make(driver, host, port, username, password, database, pool))
        .doOnError(e -> {
          throw new IllegalArgumentException("Issue creating connection pool");
        })
        .doOnSuccess(bridge -> {
          if (bridge.pool != null) bridge.pool.close();
        });
  }

  private static ConnectionPool startConnection(String host, int port, String username, String password,
                                                String database) {
    final MariadbConnectionConfiguration factoryConfig = MariadbConnectionConfiguration.builder()
        .host(host)
        .port(port)
        .username(username)
        .allowMultiQueries(true)
        .password(password)
        .database(database)
        .build();
    //TODO: Add SSl configuration options above.
    final MariadbConnectionFactory connFactory = new MariadbConnectionFactory(factoryConfig);
    final ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration
        .builder(connFactory)
        .maxIdleTime(Duration.ofMillis(1000))
        .maxSize(20)
        .build();
    return new ConnectionPool(poolConfig);
  }

}

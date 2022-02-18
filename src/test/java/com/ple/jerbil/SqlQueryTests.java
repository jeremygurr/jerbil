package com.ple.jerbil;

import com.ple.jerbil.data.*;
import com.ple.jerbil.data.bridge.MariadbR2dbcBridge;
import com.ple.jerbil.data.query.CompleteQuery;
import com.ple.jerbil.data.selectExpression.Agg;
import com.ple.jerbil.data.selectExpression.Column;
import com.ple.jerbil.data.selectExpression.NumericExpression.NumericColumn;
import com.ple.jerbil.testcommon.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static com.ple.jerbil.data.selectExpression.Literal.make;
import static com.ple.jerbil.data.selectExpression.booleanExpression.And.and;
import static com.ple.jerbil.data.selectExpression.booleanExpression.Or.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlQueryTests {

  final TestDatabaseContainer testDb = DatabaseBuilder.generate(TestDatabaseContainer.class, "test");
  final UserTableContainer user = testDb.user;
  final ItemTableContainer item = testDb.item;
  final PlayerTableContainer player = testDb.player;
  final InventoryTableContainer inventory = testDb.inventory;
  final OrderTableContainer order = testDb.order;

  public SqlQueryTests() {
    final Properties props = ConfigProps.getProperties();
    DataGlobal.bridge = MariadbR2dbcBridge.make(
      props.getProperty("driver"), props.getProperty("host"), Integer.parseInt(props.getProperty("port")),
      props.getProperty("user"), props.getProperty("password")
    );
  }

  @Test
  void temporaryTest() {
    //This more verbose method makes it so that column names will never conflict with Database or Tables properties/fields.
    Column itemIdVerbose = testDb.tables.get("inventory").columns.get("itemId");
    NumericColumn itemId = testDb.inventory.itemId;
    CharSet charSet = testDb.charSet;
    NumericColumn playerId = testDb.inventory.playerId;
    StorageEngine storageEngine = testDb.item.storageEngine;
    DataSpec dataSpec = testDb.item.itemId.dataSpec;
    testDb.sync();
    testDb.item.sync();
    testDb.item.itemId.sync();
    InventoryTableContainer inventory = testDb.inventory;
    ItemTableContainer item = testDb.item;
    CompleteQuery q = testDb.inventory.select().where(inventory.itemId.eq(make(3)));
    String name = q.execute().unwrapFlux().flatMap(result -> result.map((row, rowMetadata) -> (String) row.get("name"))).blockFirst();
    Mono<String> rName = q.execute().unwrapFlux().flatMap(result -> result.map((row, rowMetadata) -> (String) row.get("name"))).next();
  }

  @Test
  void testSelect() {
    final CompleteQuery q = user.where(user.name.eq(make("john"))).select(user.userId);
    assertEquals("""
      select userId
      from user
      where name = 'john'
      """, q.toSql());
  }

  @Test
  void multipleWhereConditions() {
    final CompleteQuery q = user.where(
      and(
        user.name.eq(make("john")),
        or(
          user.userId.isGreaterThan(make(4)),
          user.name.eq(make("bob"))
        ),
        user.age.eq(make(30))
      )
    ).select(user.userId);
    assertEquals("""
      select userId
      from user
      where name = 'john'
      and (userId > 4 or name = 'bob')
      and age = 30
      """, q.toSql());
  }

  @Test
  void testReusableQueryBase() {
    final CompleteQuery base = user.select(user.userId);
    final CompleteQuery q1 = base.where(user.name.eq(make("john")));
    final CompleteQuery q2 = base.where(user.name.eq(make("james")));
    assertEquals("""
      select userId
      from user
      where name = 'john'
      """, q1.toSql());

    assertEquals("""
      select userId
      from user
      where name = 'james'
      """, q2.toSql());
  }

  @Test
  void testSelectEnum() {
    final CompleteQuery q = item.where(item.type.eq(ItemType.weapon)).selectAll();
    assertEquals("""
      select *
      from item
      where type = 'weapon'
      """, q.toSql());

  }

  //FIXME: If you switch player and inventory position, the results won't be what you expect.
  @Test
  void testSelectJoins() {
    final CompleteQuery q = player.join(inventory.table, item.table).where(
      and(
        item.name.eq("bob"),
        player.name.eq("sword")
      )
    ).select();
    assertEquals("""
      select *
      from player
      inner join inventory using (playerId)
      inner join item using (itemId)
      where item.name = 'bob'
      and player.name = 'sword'
      """, q.toSql());
  }

  @Test
  void testAggregation() {
    final CompleteQuery q = item.select(Agg.count);
    assertEquals("""
      select count(*)
      from item
      """, q.toSql());
  }

  @Test
  void testGroupBy() {
    final CompleteQuery q = item.select(item.type, Agg.count.as("total")).groupBy(item.type);
    assertEquals("""
      select type, count(*) as total
      from item
      group by type
      """, q.toSql());
  }

  @Test
  void testRollup() {
  }

  @Test
  void testHaving() {

  }

  @Test
  void testOrderBy() {
  }

  @Test
  void testComplexExpressions() {
    final CompleteQuery q = item
      .select(item.price
        .times(make(42))
        .minus(make(1))
        .times(
          make(3)
            .plus(make(1))
        )
        .as("adjustedPrice"))
      .where(item.price.dividedBy(make(4)).isGreaterThan(make(5)));
    assertEquals("""
      select (price * 42 - 1) * (3 + 1) as adjustedPrice
      from item
      where price / 4 > 5
      """, q.toSql());
  }

  @Test
  void testExpressionWithoutTable() {
    final CompleteQuery q = make(32).minus(make(15)).as("result").select();
    assertEquals("select 32 - 15 as result", q.toSql());
  }

  @Test
  void testUnion() {
    final CompleteQuery q = user.select(user.userId, user.name).union(user.select(user.userId, user.name));
    assertEquals("""
      select userId, name
      from user
      union
      select userId, name
      from player
      """, q.toSql());
  }

  @Test
  void testUnionAll() {
    final CompleteQuery q = user.select(user.userId, user.name).unionAll(user.select(user.userId, user.name));
    assertEquals("""
      select userId, name
      from user
      union all
      select userId, name
      from player
      """, q.toSql());
  }

  @Test
  void testMatchFullText() {
    final CompleteQuery q = order.select(order.phrase).whereMatch(order.phrase, make("Hello there"));
    assertEquals("""
        select phrase from order
        where match (phrase)
        against ('hello there')
        """, q.toSql());
  }

  @Test
  void testExplain() {
    final CompleteQuery q1 = order.select().explain();
    final CompleteQuery q2 = order.explain().select();
  }

  @Test
  void testAnalyze() {
    final CompleteQuery q1 = order.select().analyze();  //For mysqlbridge it would have to do explain analyze select, but for mariadbbridge it would just do analyze select.
    final CompleteQuery q2 = order.analyze().select();  //For mysqlbridge it would have to do explain analyze select, but for mariadbbridge it would just do analyze select.
  }

}

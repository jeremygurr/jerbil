package com.ple.jerbil.data.translator;

import java.util.HashSet;
import java.util.List;

public class MariadbReservedWords {

  //TODO: replace this with IHashSet once we make it.
  public static final HashSet<String> wordsHashSet = new HashSet<>(
      List.of("accessible", "add", "all", "alter", "analyze", "and", "as", "asc", "asensitive", "before", "between",
          "bigint", "binary", "blob", "both", "by", "call", "cascade", "case", "change", "char", "character", "check",
          "collate", "column", "condition", "constraint", "continue", "convert", "create", "cross", "current_date",
          "current_role", "current_time", "current_timestamp", "current_user", "cursor", "database", "databases",
          "day_hour", "day_microsecond", "day_minute", "day_second", "dec", "decimal", "declare", "default", "delayed",
          "delete", "delete_domain_id", "desc", "describe", "deterministic", "distinct", "distinctrow", "div",
          "do_domain_ids", "double", "drop", "dual", "each", "else", "elseif", "enclosed", "escaped", "except",
          "exists", "exit", "explain", "false", "fetch", "float", "float4", "float8", "for", "force", "foreign", "from",
          "fulltext", "general", "grant", "group", "having", "high_priority", "hour_microsecond", "hour_minute",
          "hour_second", "if", "ignore", "ignore_domain_ids", "ignore_server_ids", "in", "index", "infile", "inner",
          "inout", "insensitive", "insert", "int", "int1", "int2", "int3", "int4", "int8", "integer", "intersect",
          "interval", "into", "is", "iterate", "join", "key", "keys", "kill", "leading", "leave", "left", "like",
          "limit", "linear", "lines", "load", "localtime", "localtimestamp", "lock", "long", "longblob", "longtext",
          "loop", "low_priority", "master_heartbeat_period", "master_ssl_verify_server_cert", "match", "maxvalue",
          "mediumblob", "mediumint", "mediumtext", "middleint", "minute_microsecond", "minute_second", "mod",
          "modifies", "natural", "not", "no_write_to_binlog", "null", "numeric", "offset", "on", "optimize", "option",
          "optionally", "or", "order", "out", "outer", "outfile", "over", "page_checksum", "parse_vcol_expr",
          "partition", "position", "precision", "primary", "procedure", "purge", "range", "read", "reads", "read_write",
          "real", "recursive", "ref_system_id", "references", "regexp", "release", "rename", "repeat", "replace",
          "require", "resignal", "restrict", "return", "returning", "revoke", "right", "rlike", "rows", "schema",
          "schemas", "second_microsecond", "select", "sensitive", "separator", "set", "show", "signal", "slow",
          "smallint", "spatial", "specific", "sql", "sqlexception", "sqlstate", "sqlwarning", "sql_big_result",
          "sql_calc_found_rows", "sql_small_result", "ssl", "starting", "stats_auto_recalc", "stats_persistent",
          "stats_sample_pages", "straight_join", "table", "terminated", "then", "tinyblob", "tinyint", "tinytext", "to",
          "trailing", "trigger", "true", "undo", "union", "unique", "unlock", "unsigned", "update", "usage", "use",
          "using", "utc_date", "utc_time", "utc_timestamp", "values", "varbinary", "varchar", "varcharacter", "varying",
          "when", "where", "while", "window", "with", "write", "xor", "year_month", "zerofill"));


}

package io.ybr.postgresbulk.pgjdbc.copy;

import java.sql.Connection;
import java.sql.SQLException;

/** Consumes lookup rows while the temporary key table remains in scope. */
@FunctionalInterface
interface LookupQuery<R> {

  R execute(Connection connection, String selectSql, long copiedKeys) throws SQLException;
}

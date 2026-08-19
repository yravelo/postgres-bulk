package io.ybr.postgresbulk.pgjdbc.copy;

import java.sql.Connection;

/** Internal boundary between COPY coordination and the pgJDBC protocol implementation. */
@FunctionalInterface
interface CopyExecutor {

  long execute(Connection connection, String copySql, CopyDataWriter producer);
}

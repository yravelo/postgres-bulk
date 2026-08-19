package io.ybr.postgresbulk.pgjdbc.copy;

import java.io.IOException;
import java.io.Writer;

/** Produces COPY characters incrementally without owning the destination. */
@FunctionalInterface
interface CopyDataWriter {

  void writeTo(Writer destination) throws IOException;
}

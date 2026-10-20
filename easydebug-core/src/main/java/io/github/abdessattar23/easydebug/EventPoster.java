package io.github.abdessattar23.easydebug;

import java.io.IOException;

public interface EventPoster extends AutoCloseable {

    /** @return HTTP status. Throw on a transport failure. */
    int post(String json) throws IOException;

    @Override
    default void close() throws IOException {
    }
}

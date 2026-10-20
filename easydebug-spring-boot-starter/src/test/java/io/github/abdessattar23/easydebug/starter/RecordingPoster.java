package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.EventPoster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingPoster implements EventPoster {

    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

    @Override
    public int post(String json) {
        bodies.add(json);
        return 202;
    }

    public List<String> bodies() {
        synchronized (bodies) {
            return List.copyOf(bodies);
        }
    }
}

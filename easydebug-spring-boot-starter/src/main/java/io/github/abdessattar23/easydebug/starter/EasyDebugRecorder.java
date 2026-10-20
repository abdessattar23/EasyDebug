package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CaptureEvent;
import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.EventSender;

/** Submits events. Failures here never escape to the business call. */
public final class EasyDebugRecorder {

    private final EventSender sender;
    private final CapturePolicy policy;

    public EasyDebugRecorder(EventSender sender, CapturePolicy policy) {
        this.sender = sender;
        this.policy = policy;
    }

    public CapturePolicy policy() {
        return policy;
    }

    public void record(CaptureEvent event) {
        sender.submit(event);
    }
}

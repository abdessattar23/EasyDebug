package com.orange.igr.easydebug.starter;

import com.orange.igr.easydebug.CaptureEvent;
import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.EventSender;

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

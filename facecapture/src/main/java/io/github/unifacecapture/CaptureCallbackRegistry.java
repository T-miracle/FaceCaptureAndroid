package io.github.unifacecapture;

import com.alibaba.fastjson.JSONObject;

/** Holds one Uni-App callback while the native Activity is visible. */
final class CaptureCallbackRegistry {
    private static String activeSessionId;
    private static CaptureResultCallback activeCallback;

    private CaptureCallbackRegistry() {
    }

    static synchronized boolean register(String sessionId, CaptureResultCallback callback) {
        if (activeCallback != null) {
            return false;
        }
        activeSessionId = sessionId;
        activeCallback = callback;
        return true;
    }

    static void resolve(String sessionId, JSONObject result) {
        CaptureResultCallback callback;
        synchronized (CaptureCallbackRegistry.class) {
            if (activeCallback == null || activeSessionId == null || !activeSessionId.equals(sessionId)) {
                return;
            }
            callback = activeCallback;
            activeCallback = null;
            activeSessionId = null;
        }
        callback.onResult(result);
    }

    interface CaptureResultCallback {
        void onResult(JSONObject result);
    }
}

package com.fhwl.signhook;

import de.robv.android.xposed.XposedBridge;

public class StatusHolder {
    private static final StatusHolder INSTANCE = new StatusHolder();
    
    private volatile boolean ready = false;
    private volatile int encryptCalls = 0;
    private volatile int decryptCalls = 0;
    private volatile String lastError = "";
    private volatile int port = 18888;
    private volatile long startTime = 0;

    private StatusHolder() {}

    public static StatusHolder getInstance() { return INSTANCE; }

    public synchronized void markReady() {
        ready = true;
        startTime = System.currentTimeMillis();
        XposedBridge.log("[FHWL-SignHook] Status: ready");
    }

    public synchronized void recordEncrypt() { encryptCalls++; }
    public synchronized void recordDecrypt() { decryptCalls++; }
    public synchronized void recordError(String e) {
        lastError = e;
        XposedBridge.log("[FHWL-SignHook] Error: " + e);
    }

    public boolean isReady() { return ready; }
    public int getPort() { return port; }
    public long getUptime() { return ready ? System.currentTimeMillis() - startTime : 0; }

    public String toJson() {
        return "{\"ok\":true,\"hooked\":" + ready +
            ",\"encrypt_calls\":" + encryptCalls +
            ",\"decrypt_calls\":" + decryptCalls +
            ",\"port\":" + port +
            ",\"uptime_ms\":" + getUptime() +
            ",\"last_error\":\"" + jsonEsc(lastError) + "\"}";
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}

package com.fhwl.signhook;

import de.robv.android.xposed.XposedBridge;
import fi.iki.elonen.NanoHTTPD;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Random;

public class SignHttpServer extends NanoHTTPD {
    private static final int DEFAULT_PORT = 18888;
    private static volatile SignHttpServer instance;

    public static synchronized void startServer() {
        if (instance != null) return;
        instance = new SignHttpServer(DEFAULT_PORT);
        instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        StatusHolder.getInstance().port = DEFAULT_PORT;
        XposedBridge.log("[FHWL-SignHook] HTTP server on 0.0.0.0:" + DEFAULT_PORT);
    }

    private SignHttpServer(int port) { super("0.0.0.0", port); }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().name();
        try {
            if ("GET".equals(method)) {
                if ("/health".equals(uri) || "/ok".equals(uri)) {
                    return newFixedLengthResponse(Status.OK, "application/json", "{\"ok\":true}");
                }
                if ("/".equals(uri) || uri.isEmpty()) {
                    return newFixedLengthResponse(Status.OK, "application/json", StatusHolder.getInstance().toJson());
                }
            }
            if ("POST".equals(method)) {
                HashMap<String, String> map = new HashMap<>();
                session.parseBody(map);
                String body = map.get("postData");
                if (body == null) body = "";

                if ("/encrypt".equals(uri)) return handleEncrypt(body);
                if ("/decrypt".equals(uri)) return handleDecrypt(body);
                if ("/request".equals(uri)) return handleRequest(body, session.getHeaders());
            }
            return newFixedLengthResponse(Status.NOT_FOUND, "application/json",
                    "{\"ok\":false,\"error\":\"Not Found\"}");
        } catch (Throwable th) {
            XposedBridge.log("[FHWL-SignHook] serve error: " + safeMsg(th));
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",
                    "{\"ok\":false,\"error\":\"" + jsonEscape(safeMsg(th)) + "\"}");
        }
    }

    private Response handleEncrypt(String bodyJson) {
        try {
            // Parse JSON manually
            String plainBody = extractField(bodyJson, "body");
            if (plainBody == null) plainBody = "{}";
            String timestamp = extractField(bodyJson, "timestamp");
            if (timestamp == null) timestamp = String.valueOf(System.currentTimeMillis());

            XposedBridge.log("[FHWL-SignHook] Encrypt called, body=" + plainBody.substring(0, Math.min(50, plainBody.length())));
            String result = SignHook.encrypt(plainBody, timestamp);
            StatusHolder.getInstance().recordEncrypt();
            return newFixedLengthResponse(Status.OK, "application/json", result);
        } catch (Throwable th) {
            XposedBridge.log("[FHWL-SignHook] Encrypt failed: " + safeMsg(th));
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",
                    "{\"ok\":false,\"error\":\"" + jsonEscape(safeMsg(th)) + "\"}");
        }
    }

    private Response handleDecrypt(String bodyJson) {
        try {
            String encryptedKey = extractField(bodyJson, "encryptedKey");
            String encryptedData = extractField(bodyJson, "encryptedData");
            if (encryptedKey == null || encryptedData == null) {
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json",
                        "{\"ok\":false,\"error\":\"missing encryptedKey/encryptedData\"}");
            }
            XposedBridge.log("[FHWL-SignHook] Decrypt called, ek=" + encryptedKey.substring(0, Math.min(40, encryptedKey.length())));
            String result = SignHook.decrypt(encryptedKey, encryptedData);
            StatusHolder.getInstance().recordDecrypt();
            return newFixedLengthResponse(Status.OK, "application/json", result);
        } catch (Throwable th) {
            XposedBridge.log("[FHWL-SignHook] Decrypt failed: " + safeMsg(th));
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",
                    "{\"ok\":false,\"error\":\"" + jsonEscape(safeMsg(th)) + "\"}");
        }
    }

    private Response handleRequest(String bodyJson, java.util.Map<String, String> headers) {
        try {
            String url = extractField(bodyJson, "url");
            String reqBody = extractField(bodyJson, "body");
            String appToken = extractField(bodyJson, "appToken");
            if (url == null) {
                return newFixedLengthResponse(Status.BAD_REQUEST, "application/json",
                        "{\"ok\":false,\"error\":\"missing url\"}");
            }
            if (reqBody == null) reqBody = "{}";

            // Encrypt
            String timestamp = String.valueOf(System.currentTimeMillis());
            String encResult = SignHook.encrypt(reqBody, timestamp);
            String rsaSign = extractField(encResult, "rsaSign");
            String aesData = extractField(encResult, "aesData");
            StatusHolder.getInstance().recordEncrypt();

            // Send HTTP
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("X-Api-Sign", rsaSign);
            conn.setRequestProperty("X-Api-Timestamp", timestamp);
            conn.setRequestProperty("X-Api-Nonce", randStr(10));
            conn.setRequestProperty("X-Api-DeviceId", "B5961FF2AA9D16BA4E8B1FD57ADECD3B");
            conn.setRequestProperty("AppVersion", "1.8.1");
            conn.setRequestProperty("AppPlatform", "android");
            conn.setRequestProperty("Content-Type", "text/plain");
            if (appToken != null && !appToken.isEmpty()) {
                conn.setRequestProperty("AppToken", appToken);
            }

            OutputStream os = conn.getOutputStream();
            os.write(aesData.getBytes("UTF-8"));
            os.flush();
            os.close();

            int status = conn.getResponseCode();
            String respText = readStream(conn.getInputStream());

            // Decrypt if needed
            if (respText.contains("encryptedKey") && respText.contains("encryptedData")) {
                String ek = extractField(respText, "encryptedKey");
                String ed = extractField(respText, "encryptedData");
                String decResult = SignHook.decrypt(ek, ed);
                StatusHolder.getInstance().recordDecrypt();
                // Parse dataStr from decrypt result
                String dataStr = extractField(decResult, "dataStr");
                return newFixedLengthResponse(Status.OK, "application/json",
                        "{\"status\":" + status + ",\"decrypted\":" + dataStr + "}");
            } else {
                return newFixedLengthResponse(Status.OK, "application/json",
                        "{\"status\":" + status + ",\"raw\":" + new RawJson(respText) + "}");
            }
        } catch (Throwable th) {
            XposedBridge.log("[FHWL-SignHook] Request failed: " + safeMsg(th));
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",
                    "{\"ok\":false,\"error\":\"" + jsonEscape(safeMsg(th)) + "\"}");
        }
    }

    private static String readStream(java.io.InputStream is) throws java.io.IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    // Simple JSON field extractor (no dependency on fastjson/other libs)
    static String extractField(String json, String key) {
        if (json == null || key == null) return null;
        String searchKey = "\"" + key + "\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + searchKey.length());
        if (idx < 0) return null;
        idx++;
        // skip whitespace
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length()) return null;

        char c = json.charAt(idx);
        if (c == '"') {
            // String value
            idx++;
            StringBuilder sb = new StringBuilder();
            while (idx < json.length()) {
                char ch = json.charAt(idx);
                if (ch == '\\') {
                    idx++;
                    if (idx >= json.length()) break;
                    char next = json.charAt(idx);
                    switch (next) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (idx + 4 < json.length()) {
                                try {
                                    int code = Integer.parseInt(json.substring(idx + 1, idx + 5), 16);
                                    sb.append((char) code);
                                    idx += 4;
                                } catch (NumberFormatException ignored) {}
                            }
                            break;
                        default: sb.append(next); break;
                    }
                    idx++;
                } else if (ch == '"') {
                    break;
                } else {
                    sb.append(ch);
                    idx++;
                }
            }
            return sb.toString();
        } else {
            // Non-string value: find end (comma, }, ], or whitespace boundary)
            int start = idx;
            while (idx < json.length()) {
                char ch = json.charAt(idx);
                if (ch == ',' || ch == '}' || ch == ']') break;
                idx++;
            }
            return json.substring(start, idx).trim();
        }
    }

    static class RawJson {
        private final String text;
        RawJson(String t) { this.text = t; }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default: sb.append(c);
                }
            }
            sb.append("\"");
            return sb.toString();
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String randStr(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private static String safeMsg(Throwable th) {
        if (th == null) return "";
        String m = th.getMessage();
        return m == null ? th.getClass().getName() : m;
    }
}

package com.fhwl.signhook;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

public class SignHook {
    private static final String TAG = "FHWL-SignHook";
    private static final String API_SEC_CLASS = "com.plugin.apisecurity.ApiSecurityModule";
    
    private static volatile Context appContext = null;
    private static volatile Object apiSecInstance = null;
    private static volatile Class<?> apiSecClass = null;
    private static volatile Class<?> fastjsonClass = null;
    private static volatile boolean hookReady = false;

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // Hook Application.onCreate to get Context
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Application app = (Application) param.thisObject;
                    appContext = app.getApplicationContext();
                    XposedBridge.log("[" + TAG + "] Got Context from " + app.getClass().getName());
                    ensureApiSecInstance(cl);
                    hookReady = true;
                    StatusHolder.getInstance().markReady();
                    XposedBridge.log("[" + TAG + "] Hook ready");
                } catch (Throwable th) {
                    XposedBridge.log("[" + TAG + "] onCreate hook failed: " + safeMsg(th));
                    StatusHolder.getInstance().recordError("onCreate: " + safeMsg(th));
                }
            }
        });
    }

    private static synchronized void ensureApiSecInstance(ClassLoader cl) throws Throwable {
        if (apiSecInstance != null) return;
        if (appContext == null) throw new IllegalStateException("appContext null");

        try {
            apiSecClass = Class.forName(API_SEC_CLASS, true, appContext.getClassLoader());
        } catch (ClassNotFoundException e) {
            apiSecClass = Class.forName(API_SEC_CLASS, true, cl);
        }

        // Create instance via no-arg constructor
        apiSecInstance = apiSecClass.getConstructor().newInstance();
        XposedBridge.log("[" + TAG + "] ApiSecurityModule instance created");

        // Get fastjson JSONObject class for parsing
        try {
            fastjsonClass = Class.forName("com.alibaba.fastjson.JSONObject", true, appContext.getClassLoader());
            XposedBridge.log("[" + TAG + "] Fastjson JSONObject found");
        } catch (ClassNotFoundException e) {
            XposedBridge.log("[" + TAG + "] Fastjson JSONObject not found");
        }
    }

    public static String encrypt(String bodyJson, String timestamp) throws Throwable {
        ensureReady();
        
        // Parse JSON string to fastjson JSONObject
        Object fjBody = parseJson(bodyJson);
        
        XposedBridge.log("[" + TAG + "] Encrypt called, body=" + bodyJson.substring(0, Math.min(80, bodyJson.length())));
        
        Method m = findMethod("generateHybridCryptoSign");
        Object result = m.invoke(apiSecInstance, fjBody, timestamp);
        
        String rsaSign = (String) callGet(result, "rsaSign");
        String aesData = (String) callGet(result, "aesData");
        
        return "{\"rsaSign\":\"" + jsonEscape(rsaSign) + "\",\"aesData\":\"" + jsonEscape(aesData) + "\"}";
    }

    public static String decrypt(String encryptedKey, String encryptedData) throws Throwable {
        ensureReady();
        
        XposedBridge.log("[" + TAG + "] Decrypt called, ek=" + encryptedKey.substring(0, Math.min(40, encryptedKey.length())));
        
        Method m = findMethod("decryptHybridCryptoData");
        Object result = m.invoke(apiSecInstance, encryptedKey, encryptedData);
        
        String code = String.valueOf(callGet(result, "code"));
        Object dataStrObj = callGet(result, "dataStr");
        String dataStr = dataStrObj != null ? String.valueOf(dataStrObj) : "null";
        
        if ("null".equals(dataStr)) {
            return "{\"code\":\"" + jsonEscape(code) + "\",\"dataStr\":null}";
        }
        // dataStr is already a JSON string, embed directly
        return "{\"code\":\"" + jsonEscape(code) + "\",\"dataStr\":" + dataStr + "}";
    }

    private static Object parseJson(String json) throws Exception {
        Method parseMethod = fastjsonClass.getMethod("parseObject", String.class);
        return parseMethod.invoke(null, json);
    }

    private static Method findMethod(String name) throws NoSuchMethodException {
        Class<?> cls = apiSecClass;
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            cls = cls.getSuperclass();
        }
        throw new NoSuchMethodException("Method not found: " + name);
    }

    private static Object callGet(Object obj, String field) throws Exception {
        return obj.getClass().getMethod("get", Object.class).invoke(obj, field);
    }

    private static void ensureReady() throws Throwable {
        if (!hookReady || apiSecInstance == null) {
            ensureApiSecInstance(appContext.getClassLoader());
            hookReady = true;
        }
    }

    public static boolean isReady() {
        return hookReady && apiSecInstance != null;
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
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String safeMsg(Throwable th) {
        if (th == null) return "";
        String m = th.getMessage();
        return m == null ? th.getClass().getName() : m;
    }
}

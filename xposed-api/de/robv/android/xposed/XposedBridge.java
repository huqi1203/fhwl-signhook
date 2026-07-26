package de.robv.android.xposed;

public class XposedBridge {
    public static final int XPOSED_BRIDGE_VERSION = 82;
    
    public static void log(String text) {
        android.util.Log.i("Xposed", text);
    }
    
    public static void log(Throwable t) {
        android.util.Log.e("Xposed", t.getMessage(), t);
    }

    public static void hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        // Implemented by Xposed framework at runtime
    }

    public static void hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        // Implemented by Xposed framework at runtime
    }
}

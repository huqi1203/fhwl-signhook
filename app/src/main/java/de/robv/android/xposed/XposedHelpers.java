package de.robv.android.xposed;
public class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(className, true, classLoader);
    }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
}
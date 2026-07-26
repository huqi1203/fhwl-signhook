package de.robv.android.xposed;

public class XC_MethodHook extends XCallback {
    public static class Unhook {
        private XC_MethodHook hook;
        Unhook(XC_MethodHook hook) { this.hook = hook; }
    }

    public static final class MethodHookParam extends Param {
        public java.lang.reflect.Method method;
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public Throwable getThrowable() { return null; }
        public boolean hasThrowable() { return false; }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}

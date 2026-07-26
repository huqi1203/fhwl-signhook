package com.fhwl.signhook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class EntryPoint implements IXposedHookLoadPackage {
    private static final String TAG = "FHWL-SignHook";
    private static final String TARGET_PACKAGE = "com.mytek.rtlive";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (TARGET_PACKAGE.equals(lpparam.packageName)) {
            XposedBridge.log("[" + TAG + "] Attached to " + lpparam.packageName);
            try {
                SignHook.install(lpparam);
            } catch (Throwable th) {
                XposedBridge.log("[" + TAG + "] SignHook.install failed");
                XposedBridge.log(th);
                StatusHolder.getInstance().recordError("install: " + th.getMessage());
            }
            try {
                SignHttpServer.startServer();
            } catch (Throwable th2) {
                XposedBridge.log("[" + TAG + "] SignHttpServer.start failed");
                XposedBridge.log(th2);
                StatusHolder.getInstance().recordError("server: " + th2.getMessage());
            }
        }
    }
}

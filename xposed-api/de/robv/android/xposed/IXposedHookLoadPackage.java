package de.robv.android.xposed.callbacks;

public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable;
}

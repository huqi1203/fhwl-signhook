package de.robv.android.xposed.callbacks;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable;
}
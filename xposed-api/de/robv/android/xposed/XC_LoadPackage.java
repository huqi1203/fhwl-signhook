package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

public class XC_LoadPackage {
    public static class LoadPackageParam {
        public ClassLoader classLoader;
        public String packageName;
        public String processName;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }
}

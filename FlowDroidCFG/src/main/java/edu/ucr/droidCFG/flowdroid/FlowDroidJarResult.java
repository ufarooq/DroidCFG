package edu.ucr.droidCFG.flowdroid;

import java.util.Collection;
import java.util.Set;

public class FlowDroidJarResult {
    private final String jarFile;
    private final String androidClassPath;
    private final Set<String> entryPoints;

    public FlowDroidJarResult(String jarFile, String androidClassPath, Set<String> entryPoints) {
        this.jarFile = jarFile;
        this.androidClassPath = androidClassPath;
        this.entryPoints = entryPoints;
    }

    public String getJarFile() {
        return jarFile;
    }

    public String getAndroidClassPath() {
        return androidClassPath;
    }

    public Collection<String> getEntryPoints() {
        return entryPoints;
    }
}

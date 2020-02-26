package edu.ucr.droidCFG.soot;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class APK2Jar {
    public static void main(String... args) {


        String apkFile = "/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/Reactive/TestApp/generatedlib/app-debug.apk";
        String rootPath = "/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/Reactive/TestApp/generatedlib/out";
        String androidSdk = "/Users/umarfarooq/Library/Android/sdk";//System.getProperty("android.home");

        String androidPlatforms = androidSdk + File.separator + "platforms";
        System.out.println(rootPath + ":" + androidPlatforms);
        File gendir = Paths.get(rootPath, "generatedlib").toFile();
        if (!gendir.exists()) {
            gendir.mkdirs();
        }
        Path libJar = Paths.get(gendir.toPath().toString(), "out.jar");
        File jar = libJar.toFile();
        if (jar.exists()) {
            jar.delete();
        }
        // generate a big out.jar contains all dependencies from the apk file
        Utils.generateJar(
                apkFile,
                androidPlatforms,
                gendir.toPath().toString(),
                Collections.emptySet());
        if (jar.exists()) {
            System.out.println("Created a out.jar with soot");
            System.out.println(jar.getAbsolutePath().toString());

        }

    }
}

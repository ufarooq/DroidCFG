package edu.ucr.droidCFG.wala;

import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.warnings.Warnings;
import edu.ucr.droidCFG.flowdroid.FlowDroidJarResult;
import edu.ucr.droidCFG.flowdroid.MainClass;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.jar.JarFile;

public class FlowDroidBasedWalaCFG {
    public static final String flowDroidMainClass = "LdummyMainClass";

    public static void main(String[] args) throws IOException, ClassHierarchyException, IllegalArgumentException,
            CallGraphBuilderCancelException {
        long start = System.currentTimeMillis();
        Properties p = CommandLine.parse(args);
        String platforms = p.getProperty("p");
        String outputDir = p.getProperty("o");
        String apk = p.getProperty("a");
        File apkFile = new File(apk);
        if (!apkFile.exists() || !apk.endsWith(".apk"))
            return;
        if(!new File(outputDir).exists()){
            new File(outputDir).mkdirs();
        }
        FlowDroidJarResult jarOutput = MainClass.convertToJar(platforms, apkFile, outputDir);
        //String[] entries = (String[]) jarOutput.getEntryPoints().toArray();
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        //AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopeFile, null, ScopeFileCallGraph.class.getClassLoader());
        // set exclusions.  we use these exclusions as standard for handling JDK 8
        String[] stdlibs = WalaProperties.getJ2SEJarFiles();
        for (int i = 0; i < stdlibs.length; i++) {
            scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
        }
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile(jarOutput.getAndroidClassPath()));
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile(jarOutput.getJarFile()));

        ExampleUtil.addDefaultExclusions(scope);
        IClassHierarchy cha = ClassHierarchyFactory.make(scope);
        System.out.println(cha.getNumberOfClasses() + " classes");
        System.out.println(Warnings.asString());
        Warnings.clear();
        AnalysisOptions options = new AnalysisOptions();

        Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha, flowDroidMainClass);
        options.setEntrypoints(entrypoints);
        // you can dial down reflection handling if you like
//    options.setReflectionOptions(ReflectionOptions.NONE);
        AnalysisCache cache = new AnalysisCacheImpl();
        // other builders can be constructed with different Util methods
        CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);
//    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
//    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
        System.out.println("building call graph...");
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("done");
        System.out.println("took " + (end - start) + "ms");
        System.out.println(CallGraphStats.getStats(cg));
    }
}

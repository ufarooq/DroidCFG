package edu.ucr.droidCFG.wala;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.util.warnings.Warnings;
import edu.ucr.droidCFG.flowdroid.FlowDroidJarResult;
import edu.ucr.droidCFG.flowdroid.MainClass;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;

public class FlowDroidBasedWalaCFG {
  public static final String flowDroidMainClass = "LdummyMainClass";
  public static final String APK_EXTENSION = ".apk";
  public static final String PROPERTY_PLATFORMS = "p";
  public static final String PROPERTY_APK = "a";

  public static void main(String[] args)
      throws IOException, ClassHierarchyException, IllegalArgumentException,
          CallGraphBuilderCancelException {
    long start = System.currentTimeMillis();
    Properties p = CommandLine.parse(args);
    String platforms = p.getProperty(PROPERTY_PLATFORMS);
    String apk = p.getProperty(PROPERTY_APK);
    File apkFile = new File(apk);
    if (!apkFile.exists() || !apk.endsWith(APK_EXTENSION)) return;
    String outputDir = apkFile.getParent();
    if (!new File(apkFile.getParent()).exists()) {
      new File(outputDir).mkdirs();
    }
    FlowDroidJarResult jarOutput = MainClass.convertToJar(platforms, apkFile, outputDir, true);
    AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

    // set exclusions.  we use these exclusions as standard for handling JDK 8
    /*String[] stdlibs = WalaProperties.getJ2SEJarFiles();
    for (int i = 0; i < stdlibs.length; i++) {
      scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
    }*/
    scope.addToScope(ClassLoaderReference.Primordial, new JarFile(jarOutput.getAndroidClassPath()));
    scope.addToScope(ClassLoaderReference.Application, new JarFile(jarOutput.getJarFile()));
    ExampleUtil.addDefaultExclusions(scope);
    IClassHierarchy cha = ClassHierarchyFactory.make(scope);
    for (IClass iClass : cha) {
      if (iClass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        System.out.println("App Class:" + iClass.getName());
      }
    }
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println(Warnings.asString());
    Warnings.clear();
    AnalysisOptions options = new AnalysisOptions();

    Iterable<Entrypoint> entryPoints = Util.makeMainEntrypoints(scope, cha, flowDroidMainClass);
    options.setEntrypoints(entryPoints);
    // you can dial down reflection handling if you like
    //    options.setReflectionOptions(ReflectionOptions.NONE);
    AnalysisCache cache = new AnalysisCacheImpl();
    // other builders can be constructed with different Util methods
    /* TODO: makeZeroOneContainerCFABuilder misses out ALL calls out of main dummy method */
    // CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);
    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
    //        CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);
    CGNode mainMethod = CallGraphSearchUtil.findMainMethod(cg);
    Set<CGNode> reachableNodes = DFS.getReachableNodes(cg, Collections.singleton(mainMethod));
    for (CGNode node : reachableNodes) {
      System.out.println("CG Node: " + node.getMethod().getSignature());
      Iterator<CallSiteReference> callSites = node.iterateCallSites();
      /*while (callSites.hasNext()) {
        CallSiteReference next = callSites.next();
        System.out.println("Call: " + next.getDeclaredTarget().getSignature());
      }*/
    }
    long end = System.currentTimeMillis();
    System.out.println("done");
    System.out.println("took " + (end - start) + "ms");
    System.out.println(CallGraphStats.getStats(cg));
  }

  private static Iterable<Entrypoint> makePublicEntrypoints(
      IClassHierarchy cha, String entryClass) {
    Collection<Entrypoint> result = new ArrayList<Entrypoint>();
    IClass klass =
        cha.lookupClass(
            TypeReference.findOrCreate(
                ClassLoaderReference.Application,
                StringStuff.deployment2CanonicalTypeString(entryClass)));
    for (IMethod m : klass.getDeclaredMethods()) {
      if (m.isPublic() && m.isStatic()) {
        System.out.println("Dummy Entry Found: " + m.getSignature());
        result.add(new DefaultEntrypoint(m, cha));
      }
    }
    return result;
  }
}

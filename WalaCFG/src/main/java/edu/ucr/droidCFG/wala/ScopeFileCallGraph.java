package edu.ucr.droidCFG.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.util.warnings.Warnings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.jar.JarFile;

/**
 * Driver that constructs a call graph for an application specified via a scope file.
 * Useful for getting some code to copy-paste.
 */
public class ScopeFileCallGraph {

    /**
     * Usage: ScopeFileCallGraph -scopeFile file_path [-entryClass class_name |
     * -mainClass class_name]
     * <p>
     * If given -mainClass, uses main() method of class_name as entrypoint. If
     * given -entryClass, uses all public methods of class_name.
     *
     * @throws IOException
     * @throws ClassHierarchyException
     * @throws CallGraphBuilderCancelException
     * @throws IllegalArgumentException
     */
    public static void main(String[] args) throws IOException, ClassHierarchyException, IllegalArgumentException,
            CallGraphBuilderCancelException {
        long start = System.currentTimeMillis();

        String mainClass = "Lcom/example/testapp/Main";//p.getProperty("mainClass");
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

        //AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopeFile, null, ScopeFileCallGraph.class.getClassLoader());
        // set exclusions.  we use these exclusions as standard for handling JDK 8
        String[] stdlibs = WalaProperties.getJ2SEJarFiles();
        for (int i = 0; i < stdlibs.length; i++) {
            scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
        }
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/umarfarooq/Library/Android/sdk/platforms/android-28/android.jar"));
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/Reactive/TestApp/generatedlib/out/generatedlib/out.jar"));

        ExampleUtil.addDefaultExclusions(scope);
        IClassHierarchy cha = ClassHierarchyFactory.make(scope);
        System.out.println(cha.getNumberOfClasses() + " classes");
        System.out.println(Warnings.asString());
        Warnings.clear();
        AnalysisOptions options = new AnalysisOptions();
        Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha, mainClass);
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

    private static Iterable<Entrypoint> makePublicEntrypoints(AnalysisScope scope, IClassHierarchy cha, String entryClass) {
        Collection<Entrypoint> result = new ArrayList<Entrypoint>();
        IClass klass = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application,
                StringStuff.deployment2CanonicalTypeString(entryClass)));
        for (IMethod m : klass.getDeclaredMethods()) {
            if (m.isPublic()) {
                result.add(new DefaultEntrypoint(m, cha));
            }
        }
        return result;
    }
}
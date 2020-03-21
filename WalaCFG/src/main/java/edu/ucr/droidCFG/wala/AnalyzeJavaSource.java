package edu.ucr.droidCFG.wala;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroOneContainerCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.cast.java.ipa.slicer.AstJavaSlicer;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceDirectoryTreeModule;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.pruned.CallGraphPruning;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.thin.ThinSlicer;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.shrikeBT.analysis.Analyzer;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.traverse.Topological;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.ref.ReferenceCleanser;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;

import static com.ibm.wala.ipa.slicer.Statement.Kind.*;

public class AnalyzeJavaSource {

    public static void main(String[] args) throws WalaException, IllegalArgumentException, CancelException, IOException, GraphIntegrity.UnsoundGraphException {
        //Properties p = CommandLine.parse(args);
        long start = System.currentTimeMillis();
        String mainClass = "Lcom/example/plugintest/Main";
        String sourceDir = "/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/WALA/test/src";//System.getProperty("sourceDir");

        AnalysisScope scope = new JavaSourceAnalysisScope();
        // add standard libraries to scope
        System.out.println("JDK: " + WalaProperties.getJ2SEJarFiles() + ":" + System.getProperty("java.home"));
        String[] stdlibs = WalaProperties.getJ2SEJarFiles();
        for (int i = 0; i < stdlibs.length; i++) {
            scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
        }
        //scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/dolby/Android/android-sdk-macosx/extras/android/support/v7/appcompat/libs/android-support-v4.jar"));
        //scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/dolby/Android/android-sdk-macosx/extras/android/support/v7/appcompat/libs/android-support-v7-appcompat.jar"));
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/umarfarooq/Library/Android/sdk/platforms/android-28/android.jar"));
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/WALA/test/libs/R.jar"));
        scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/umarfarooq/UCR/Research/Research/ProgramAnalysis/WALA/test/libs/App.jar"));

        // add the source directory
        scope.addToScope(JavaSourceAnalysisScope.SOURCE, new SourceDirectoryTreeModule(new File(sourceDir)));
        ExampleUtil.addDefaultExclusions(scope);
        //scope.addToScope(JavaSourceAnalysisScope.SOURCE, new SourceDirectoryTreeModule(new File(generatedRDir)));
        IClassHierarchy cha = ClassHierarchyFactory.make(scope, new ECJClassLoaderFactory(scope.getExclusions()));
        ReferenceCleanser.registerClassHierarchy(cha);

        System.out.println(cha.getNumberOfClasses() + " classes");
        System.out.println(Warnings.asString());
        Warnings.clear();
        AnalysisOptions options = new AnalysisOptions();
        Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha, new String[]{mainClass});

        entrypoints.forEach(it -> {
            System.out.println("Entry: " + it.getMethod().getSignature());
        });
        options.setEntrypoints(entrypoints);
        // build the call graph
        IAnalysisCacheView cache = new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
        ReferenceCleanser.registerCache(cache);
        CallGraphBuilder<?> builder =
                new ZeroOneContainerCFABuilderFactory().make(options, cache, cha, scope);
        System.out.println("building call graph...");
        CallGraph cg = builder.makeCallGraph(options, null);
        long end = System.currentTimeMillis();
        System.out.println("done");
        System.out.println("took " + (end - start) + "ms");
        System.out.println(CallGraphStats.getStats(cg));
        PointerAnalysis pa = builder.getPointerAnalysis();
        //PointerAnalysis<InstanceKey> ptr = builder.getPointerAnalysis();
        DataDependenceOptions data = DataDependenceOptions.NO_HEAP;
        ControlDependenceOptions control = ControlDependenceOptions.FULL;
        SDG<InstanceKey> sdg = new SDG<InstanceKey>(cg, pa, new AstJavaModRef<InstanceKey>(), data, control);
        GraphIntegrity.check(sdg);
    }

    /**
     * @return a NodeDecorator that decorates statements in a slice for a dot-ted representation
     */
    public static NodeDecorator<Statement> makeNodeDecorator() {
        return s -> {
            switch (s.getKind()) {
                case HEAP_PARAM_CALLEE:
                case HEAP_PARAM_CALLER:
                case HEAP_RET_CALLEE:
                case HEAP_RET_CALLER:
                    HeapStatement h = (HeapStatement) s;
                    return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
                case NORMAL:
                    NormalStatement n = (NormalStatement) s;
                    return n.getInstruction() + "\\n" + n.getNode().getMethod().getSignature();
                case PARAM_CALLEE:
                    ParamCallee paramCallee = (ParamCallee) s;
                    return s.getKind()
                            + " "
                            + paramCallee.getValueNumber()
                            + "\\n"
                            + s.getNode().getMethod().getName();
                case PARAM_CALLER:
                    ParamCaller paramCaller = (ParamCaller) s;
                    return s.getKind()
                            + " "
                            + paramCaller.getValueNumber()
                            + "\\n"
                            + s.getNode().getMethod().getName()
                            + "\\n"
                            + paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName();
                case EXC_RET_CALLEE:
                case EXC_RET_CALLER:
                case NORMAL_RET_CALLEE:
                case NORMAL_RET_CALLER:
                case PHI:
                default:
                    return s.toString();
            }
        };
    }

    public static Statement findCallTo(CGNode n, String methodName) {
        IR ir = n.getIR();
        for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
            SSAInstruction s = it.next();
            if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction) {
                com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
                if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)) {
                    com.ibm.wala.util.intset.IntSet indices = ir.getCallInstructionIndices(call.getCallSite());
                    com.ibm.wala.util.debug.Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                    return new com.ibm.wala.ipa.slicer.NormalStatement(n, indices.intIterator().next());
                }
            }
        }
        Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
        return null;
    }

    public static void print(Collection<Statement> statements, String title) {
        for (Statement statement : statements) {
            System.out.println(title + ": " + statement);
        }
    }

    public static Collection<CGNode> partialCG(Collection<Statement> slice) {
        Collection<CGNode> partialCG = new ArrayList<>();
        for (Statement s : slice) {
            partialCG.add(s.getNode());
        }
        return partialCG;
    }

    public static void dumpSlice(Collection<Statement> slice) {
        for (Statement s : slice) {
            //s.getNode().getIR().getInstructions()[0].
            IMethod method = s.getNode().getMethod();
            switch (s.getKind()) {
                case NORMAL:
                    NormalStatement n = (NormalStatement) s;
                    SSAInstruction st = n.getInstruction();
                    if (st instanceof SSAFieldAccessInstruction) { // field
                        if (st instanceof SSAGetInstruction) {// field read
                            SSAGetInstruction ssaGetInstruction = (SSAGetInstruction) st;
                            System.out.println("Field Read: " + ssaGetInstruction.getDeclaredField().getSignature());
                        } else if (st instanceof SSAPutInstruction) {// field write
                            SSAPutInstruction ssaPutInstruction = (SSAPutInstruction) st;
                            System.out.println("Field Write: " + ssaPutInstruction.getDeclaredField().getSignature());
                        }
                    }
                    System.out.println(st + ":" + st.getNumberOfUses() + ":" + st.getNumberOfDefs());
                    for (int i = 0; i < st.getNumberOfUses(); i++) {
                        System.out.println("Use: " + i + ": " + st.getUse(i));
                    }
                    for (int j = 0; j < st.getNumberOfUses(); j++) {
                        System.out.println("Def: " + j + ": " + st.getDef(j));
                    }
                    if (method instanceof AstMethod) {
                        AstMethod walaMethod = (AstMethod) method;
                        AstMethod.DebuggingInformation debugInfo = walaMethod.debugInfo();
                        CAstSourcePositionMap.Position position = debugInfo.getInstructionPosition(st.iIndex());
                        System.out.println("Position: " + position.getFirstLine());
                    }

                    break;
                case PARAM_CALLER:
                    ParamCaller paramCaller = (ParamCaller) s;
                    SSAAbstractInvokeInstruction inst = paramCaller.getInstruction();
                    System.out.println("Param Caller; " + inst.toString());
                    break;
                case PARAM_CALLEE:
                    ParamCallee paramCallee = (ParamCallee) s;
                    System.out.println("Param Callee: " + paramCallee.getValueNumber());
                    break;
                default:
                    System.err.println(s.toString());
            }
        }
    }

    /**
     * return a view of the sdg restricted to the statements in the slice
     */
    public static Graph<Statement> pruneSDG(SDG<InstanceKey> sdg, final Collection<Statement> slice) {
        return GraphSlicer.prune(sdg, slice::contains);

    }

    /**
     * If s is a call statement, return the statement representing the normal return from s
     */
    public static Statement getReturnStatementForCall(Statement s) {
        if (s.getKind() == Statement.Kind.NORMAL) {
            NormalStatement n = (NormalStatement) s;
            SSAInstruction st = n.getInstruction();
            if (st instanceof SSAInvokeInstruction) {
                SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) st;
                if (call.getCallSite().getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
                    throw new IllegalArgumentException(
                            "this driver computes forward slices from the return value of calls.\n"
                                    + "Method "
                                    + call.getCallSite().getDeclaredTarget().getSignature()
                                    + " returns void.");
                }
                return new NormalReturnCaller(s.getNode(), n.getInstructionIndex());
            } else {
                return s;
            }
        } else {
            return s;
        }
    }

}
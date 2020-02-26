package edu.ucr.droidCFG

import com.android.tools.idea.experimental.callgraph.visitAll
import com.android.tools.lint.detector.api.interprocedural.*
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.project.Project


fun getCha(project: Project,scope: AnalysisScope): ClassHierarchy {
    val cha = ClassHierarchyVisitor()
            .apply { visitAll(project, scope) }
            .classHierarchy
    return cha
}

fun getIntraproceduralDispatch(cha: ClassHierarchy, project: Project,scope: AnalysisScope): IntraproceduralDispatchReceiverEvaluator {
    val receiverEval = IntraproceduralDispatchReceiverVisitor(cha)
            .apply { visitAll(project, scope) }
            .receiverEval
    return receiverEval
}

fun getCallgraph(receiverEval: IntraproceduralDispatchReceiverEvaluator, cha: ClassHierarchy, project: Project,scope: AnalysisScope): CallGraph {
    val callGraph = CallGraphVisitor(receiverEval, cha)
            .apply { visitAll(project, scope) }
            .callGraph
    return callGraph
}

fun getContextualCallgraph(callGraph: CallGraph, receiverEval: IntraproceduralDispatchReceiverEvaluator): ContextualCallGraph {
    val contextualGraph = callGraph.buildContextualCallGraph(receiverEval)
    return contextualGraph
}


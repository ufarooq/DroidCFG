package edu.ucr.droidCFG.harness;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.lint.detector.api.interprocedural.*;
import com.google.common.graph.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.psi.SearchUtils;
import org.jetbrains.uast.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CallGraphManager {
  private final Project mProject;

  private CallGraph callGraph;
  private Graph<SimpleNode> transitiveGraph;
  private Set<PsiClass> entryPointClasses;
  private Module module;
  private Map<PsiClass, Set<SimpleNode>> entryPointNodes;

  public CallGraphManager(Project project, Module module) {
    this.module = module;
    this.entryPointClasses = EntryPointsManager.getEntryPointClasses(project, module);
    entryPointNodes = new HashMap<>();
    mProject = project;
    buildCallGraph();
  }

  public CallGraph getCallGraph() {
    return callGraph;
  }

  public Graph<SimpleNode> getTransitiveGraph() {
    return transitiveGraph;
  }

  public Map<PsiClass, Set<SimpleNode>> getEntryPointNodes() {
    return entryPointNodes;
  }

  private void buildCallGraph() {

    // PsiDocumentManager.getInstance(mProject).commitAllDocuments(); // Prevents problems with
    // smart pointers creation.

    AnalysisScope scope = new AnalysisScope(mProject);
    ClassHierarchy cha = CGUtil.getCha(mProject, scope);
    IntraproceduralDispatchReceiverEvaluator receiverEval =
        CGUtil.getIntraproceduralDispatch(cha, mProject, scope);
    this.callGraph = CGUtil.getCallgraph(receiverEval, cha, mProject, scope);

    System.out.println("Call Graph Manager:" + CallGraphManager.this.callGraph.getNodes().size());
    MutableGraph<SimpleNode> nodesGraph =
        GraphBuilder.directed()
            .expectedNodeCount(CallGraphManager.this.callGraph.getNodes().size())
            .allowsSelfLoops(true)
            .build();
    callGraph
        .getNodes()
        .forEach(
            fromNode -> {
              CallTarget callTarget = fromNode.getTarget();
              if (callTarget instanceof CallTarget.Method) {
                CallTarget.Method tMethod = (CallTarget.Method) callTarget;
                PsiClass containingClass = tMethod.getElement().getPsi().getContainingClass();
                if (containingClass != null
                    && CallGraphManager.this.entryPointClasses.contains(containingClass)) {
                  Set<SimpleNode> existing =
                      CallGraphManager.this.entryPointNodes.getOrDefault(
                          containingClass, new HashSet<SimpleNode>());
                  existing.add(new SimpleNode(fromNode));
                  CallGraphManager.this.entryPointNodes.put(containingClass, existing);
                }
              }
              fromNode
                  .getEdges()
                  .forEach(
                      edge -> {
                        CallGraph.Node toNode = edge.getNode();
                        UCallExpression callExpression = edge.getCall();
                        nodesGraph.putEdge(new SimpleNode(fromNode), new SimpleNode(toNode));
                      });
            });
    MutableGraph<SimpleNode> localNodesGraph =
        GraphBuilder.directed()
            .expectedNodeCount(CallGraphManager.this.callGraph.getNodes().size())
            .allowsSelfLoops(true)
            .build();
    callGraph
        .getNodes()
        .forEach(
            fromNode -> {
              // CallTarget callTarget = fromNode.getTarget();
              SimpleNode sourceNode = new SimpleNode(fromNode);
              System.out.print("Node: " + sourceNode.toString());
              fromNode
                  .getEdges()
                  .forEach(
                      edge -> {
                        CallGraph.Node toNode = edge.getNode();
                        SimpleNode targetNode = new SimpleNode(toNode);
                        System.out.print(" --> " + targetNode.toString());
                        localNodesGraph.putEdge(sourceNode, targetNode);
                        System.out.println();
                      });
              System.out.println();
            });

    Set<SimpleNode> entryMethods =
        localNodesGraph.nodes().stream()
            .filter(
                node ->
                    localNodesGraph.inDegree(node) == 0
                        && !(node.getCallTarget() instanceof CallTarget.DefaultCtor))
            .collect(Collectors.toSet());

    Set<SimpleNode> resolvedEntryNodes =
        entryMethods.stream()
            .filter(isNodeOnComponent(entryPointClasses))
            .collect(Collectors.toSet());

    EntryPointsResult.getInstance().addNodesOnComponents(resolvedEntryNodes);
    entryMethods.removeAll(resolvedEntryNodes);
    buildConnectionsForUnresolved(entryMethods);
    /*for(SimpleNode node: nodesGraph.nodes()){
        System.out.println("Node: " + node.toString());
        for(SimpleNode edge:nodesGraph.adjacentNodes(node)){
            System.err.println(edge.toString());
        }
    }*/

    CallGraphManager.this.transitiveGraph = Graphs.transitiveClosure(nodesGraph);

    // System.out.println(node.getTarget().getElement().asSourceString());

  }

  public static Predicate<SimpleNode> isNodeOnComponent(Set<PsiClass> entryPointClasses) {
    return node ->
        node.getCallTarget() instanceof CallTarget.Method
            && ((CallTarget.Method) node.getCallTarget()).getElement().getContainingClass() != null
            && (entryPointClasses.contains(
                ((CallTarget.Method) node.getCallTarget()).getElement().getContainingClass()));
  }

  private Set<SimpleNode> buildConnectionsForUnresolved(Set<SimpleNode> unResolvedNodes) {
    Set<SimpleNode> newResolvedNodes = new HashSet<>();
    unResolvedNodes.forEach(
        node -> {
          if (node.getCallTarget() instanceof CallTarget.Method) {
            CallTarget.Method tMethod = (CallTarget.Method) node.getCallTarget();
            // anonymous classes
            if (tMethod.getElement().getContainingClass().getName() == null) {
              UElement anonymousDeclaration = tMethod.getElement().getUastParent();
              if (anonymousDeclaration instanceof UAnonymousClass) {
                /**
                 * anonymousDeclaration.getParent() is a newExpression and parent of parent is
                 * Field/Variable or passed as parameter
                 */
                // Java specific API here
                UAnonymousClass anonymousClass = (UAnonymousClass) anonymousDeclaration;
                PsiElement anonymousDeclarationParent =
                    anonymousDeclaration.getUastParent().getUastParent().getSourcePsi();

                if (anonymousDeclarationParent instanceof PsiMethodCallExpression) {
                  // Inline, cannot be reference anywhere else
                  System.out.println("Anonymous(P): " + anonymousDeclarationParent);
                } else {
                  // can be reference other places,
                  System.out.println("Anonymous(F): " + anonymousDeclarationParent);
                  Iterable<PsiReference> references =
                      SearchUtils.findAllReferences(anonymousDeclarationParent);
                  for (PsiReference reference : references) {
                    System.out.println("Reference: " + reference.getCanonicalText());
                    PsiElement resolved = reference.getElement();
                    PsiElement stmt = PsiUtil.getEnclosingStatement(resolved);
                    PsiMethod callerMethod = Utils.findMethod(stmt);
                    EntryPointsResult.getInstance().addNewPartialResult(callerMethod, tMethod);
                    System.out.println("CallerMethod: " + callerMethod.getName());
                    if (resolved instanceof PsiReferenceExpression) {
                      PsiReferenceExpression referenceExpression =
                          (PsiReferenceExpression) resolved;
                      System.out.println("ReferenceExp: " + referenceExpression.getCanonicalText());
                    }
                    System.out.println(resolved.getParent());
                  }
                }
              }
            } else {
              /*PsiClassType[] implementsInterfaces = tMethod.getElement().getContainingClass().getImplementsListTypes();
              for (PsiClassType type : implementsInterfaces) {
                  System.out.println("Implement: "+type.getClassName());
              }
              Iterable<PsiMethod> overridingMethods = SearchUtils.findOverridingMethods(tMethod.component1().getPsi());
              for (PsiMethod method : overridingMethods) {
                  System.out.println("Overriding: " + method.getName());
              }*/
              PsiClass contextClass = null;
              Iterable<PsiReference> references =
                  SearchUtils.findAllReferences(tMethod.getElement().getSourcePsi());
              for (PsiReference reference : references) {
                System.out.println("Reference: " + reference.getCanonicalText());
                PsiElement element = reference.getElement();
                System.out.println(element);
                // used in xml, methods are declared as click_handler(View v);
                if (element instanceof XmlAttributeValue) {
                  XmlAttributeValue value = (XmlAttributeValue) element;
                  XmlAttribute attribute = (XmlAttribute) value.getParent();
                  PsiFile containingFile = attribute.getContainingFile();
                  XmlFile xmlFile = (XmlFile) containingFile;
                  contextClass = AndroidPsiUtils.getContextClass(module, xmlFile);
                  System.out.println("Attached Class: " + contextClass.getQualifiedName());
                } else if (element instanceof PsiMethodReferenceExpression) {
                  // method pass as this::onLongClick

                }
              }
              if (contextClass == null) {
                PsiClass containingClass = tMethod.getElement().getContainingClass();
                Iterable<PsiReference> classReferences =
                    SearchUtils.findAllReferences(containingClass);
                for (PsiReference reference : classReferences) {
                  System.out.println("Class Reference: " + reference.getCanonicalText());
                }
              }
            }
          } else if (node.getCallTarget() instanceof CallTarget.Lambda) {
            CallTarget.Lambda tMethod = (CallTarget.Lambda) node.getCallTarget();
            PsiElement lambdaDeclaration = tMethod.component1().getUastParent().getJavaPsi();
            System.out.println("Parent: " + lambdaDeclaration);
            if (!(lambdaDeclaration instanceof PsiMethodCallExpression)) {
              Iterable<PsiReference> references = SearchUtils.findAllReferences(lambdaDeclaration);
              for (PsiReference reference : references) {
                System.out.println("Reference: " + reference.getCanonicalText());
                PsiElement resolved = reference.getElement();
                PsiElement stmt = PsiUtil.getEnclosingStatement(resolved);
                PsiMethod callerMethod = Utils.findMethod(stmt);
                EntryPointsResult.getInstance().addNewPartialResult(callerMethod, tMethod);
                System.out.println("CallerMethod: " + callerMethod.getName());
              }
            } else { // inline declaration
              PsiMethodCallExpression callExpression = (PsiMethodCallExpression) lambdaDeclaration;
              PsiElement stmt = PsiUtil.getEnclosingStatement(callExpression);
              PsiMethod callerMethod = Utils.findMethod(stmt);
              EntryPointsResult.getInstance().addNewPartialResult(callerMethod, tMethod);
              System.out.println("CallerMethod: " + callerMethod.getName());
            }
          }
        });
    return newResolvedNodes;
  }
}

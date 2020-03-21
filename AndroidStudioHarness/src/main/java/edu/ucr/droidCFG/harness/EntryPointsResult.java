package edu.ucr.droidCFG.harness;

import com.android.tools.lint.detector.api.interprocedural.CallTarget;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntryPointsResult {
  private final Map<PsiClass, Set<PsiMethod>> resultsMap;
  private final Map<PsiMethod, PsiClass> componentToMethodMap;
  private final Set<PartialHarnessResult> partialHarnessResults;
  private static EntryPointsResult _instance = null;

  private EntryPointsResult() {
    this.resultsMap = new HashMap<>();
    this.componentToMethodMap = new HashMap<>();
    this.partialHarnessResults = new HashSet<>();
  }

  public static EntryPointsResult getInstance() {
    if (_instance == null) {
      _instance = new EntryPointsResult();
    }
    return _instance;
  }

  public void addNewResult(PsiClass associatedClass, PsiMethod method) {
    Set<PsiMethod> existing = resultsMap.getOrDefault(associatedClass, new HashSet<PsiMethod>());
    existing.add(method);
    this.resultsMap.put(associatedClass, existing);
    this.componentToMethodMap.put(method, associatedClass);
  }

  public void addNewPartialResult(PsiMethod associatedMethod, CallTarget method) {
    if (method instanceof CallTarget.Lambda) {
      CallTarget.Lambda lambda = (CallTarget.Lambda) method;
      // lambda.component1().accept(Uast);
      System.out.println(
          "Partial Result(L): "
              + lambda.component1().getFunctionalInterfaceType().getCanonicalText());
      // PsiClass psiClass =
      // PsiTypesUtil.getPsiClass(lambda.getElement().getFunctionalInterfaceType());

    } else if (method instanceof CallTarget.Method) {
      PsiMethod psiMethod = ((CallTarget.Method) method).component1().getPsi();
      System.out.println(
          "Partial Result(M): "
              + psiMethod.getContainingClass().getName()
              + ":"
              + psiMethod.getName());
      PartialHarnessResult partialHarnessResult =
          new PartialHarnessResult(associatedMethod, psiMethod);
      this.partialHarnessResults.add(partialHarnessResult);
    }
  }

  public void addNodesOnComponents(Set<SimpleNode> nodes) {
    nodes.forEach(
        node -> {
          CallTarget.Method method = (CallTarget.Method) node.getCallTarget();
          PsiClass associatedClass = method.getElement().getContainingClass();
          addNewResult(associatedClass, method.getElement().getPsi());
        });
  }

  public Set<PsiMethod> getResolvedResults() {
    Set<PsiMethod> resolvedSet = new HashSet<>();
    resultsMap.values().parallelStream().forEach(set -> resolvedSet.addAll(set));
    return resolvedSet;
  }

  public Set<PartialHarnessResult> getPartialHarnessResults() {
    return partialHarnessResults;
  }
}

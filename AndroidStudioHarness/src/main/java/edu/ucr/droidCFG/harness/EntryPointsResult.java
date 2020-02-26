package edu.ucr.droidCFG.harness;

import com.android.tools.lint.detector.api.interprocedural.CallTarget;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.hash.HashMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntryPointsResult {
    private final Map<PsiClass, Set<PsiMethod>> resultsMap;
    private final Map<PsiMethod, PsiClass> componentToMethodMap;
    private static EntryPointsResult _instance = null;

    private EntryPointsResult() {
        this.resultsMap = new HashMap<>();
        this.componentToMethodMap = new HashMap<>();
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

    public void addNodesOnComponents(Set<SimpleNode> nodes) {
        nodes.forEach(node -> {
            CallTarget.Method method = (CallTarget.Method) node.getCallTarget();
            PsiClass associatedClass = method.getElement().getContainingClass();
            addNewResult(associatedClass, method.getElement().getPsi());
        });
    }

    public Set<PsiMethod> getResolvedResults() {
        Set<PsiMethod> resolvedSet = new HashSet<>();
        resultsMap.values().parallelStream().forEach(set -> {
            resolvedSet.addAll(set);
        });
        return resolvedSet;
    }

}

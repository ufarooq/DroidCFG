package edu.ucr.droidCFG.harness;

import com.android.tools.lint.detector.api.interprocedural.CallGraph;
import com.android.tools.lint.detector.api.interprocedural.CallTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public class SimpleNode {
  private final CallTarget callTarget;
  private CallGraph.Node node;

  public SimpleNode(@NotNull CallGraph.Node node) {
    this.node = node;
    this.callTarget = node.getTarget();
  }

  public CallGraph.Node getNode() {
    return node;
  }

  public CallTarget getCallTarget() {
    return callTarget;
  }

  @Override
  public String toString() {
    if (callTarget instanceof CallTarget.Method) {
      CallTarget.Method tMethod = (CallTarget.Method) callTarget;
      // UMethod uMethod = (UMethod) target.getElement();
      // System.out.println(tMethod.getElement().getLanguage().getID());
      return tMethod.getElement().getContainingFile().getName()
          + ":"
          + tMethod.getElement().getContainingClass().getName()
          + ":"
          + tMethod.getElement().getName();
    } else if (callTarget instanceof CallTarget.DefaultCtor) {
      CallTarget.DefaultCtor tMethod = (CallTarget.DefaultCtor) callTarget;
      // UMethod uMethod = (UMethod) target.getElement();
      // System.out.println(tMethod.getElement().getLanguage().getID());
      return tMethod.getElement().getJavaPsi().getQualifiedName() + ": Constructor";
    }
    if (callTarget instanceof CallTarget.Lambda) {
      CallTarget.Lambda tMethod = (CallTarget.Lambda) callTarget;
      // UMethod uMethod = (UMethod) target.getElement();
      PsiClass psiClass =
          PsiTypesUtil.getPsiClass(tMethod.getElement().getFunctionalInterfaceType());

      // System.out.println(psiClass.getLanguage().getID());
      PsiClassType.ClassResolveResult generics =
          PsiUtil.resolveGenericsClassInType(tMethod.getElement().getFunctionalInterfaceType());

      Map<PsiTypeParameter, PsiType> map = generics.getSubstitutor().getSubstitutionMap();
      map.forEach(
          (k, v) -> {
            System.out.println(k.getIndex() + ":" + v.getCanonicalText());
          });
      return tMethod.getElement().getFunctionalInterfaceType().getCanonicalText() + ": Lambda";
    }
    return "NULL";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SimpleNode that = (SimpleNode) o;
    return Objects.equals(callTarget, that.callTarget);
  }

  @Override
  public int hashCode() {
    return Objects.hash(callTarget);
  }
}

package edu.ucr.droidCFG.harness;

import com.android.tools.lint.detector.api.interprocedural.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.visitor.UastVisitor;

import java.util.ArrayList;
import java.util.List;

public class CGUtil {

  public static ClassHierarchy getCha(Project project, AnalysisScope scope) {
    ClassHierarchyVisitor cha = new ClassHierarchyVisitor();
    visitAll(cha, project, scope);
    return cha.getClassHierarchy();
  }

  public static IntraproceduralDispatchReceiverEvaluator getIntraproceduralDispatch(
      ClassHierarchy cha, Project project, AnalysisScope scope) {
    IntraproceduralDispatchReceiverVisitor receiverEval =
        new IntraproceduralDispatchReceiverVisitor(cha);
    visitAll(receiverEval, project, scope);
    return receiverEval.getReceiverEval();
  }

  public static CallGraph getCallgraph(
      IntraproceduralDispatchReceiverEvaluator receiverEval,
      ClassHierarchy cha,
      Project project,
      AnalysisScope scope) {
    CallGraphVisitor callGraph = new CallGraphVisitor(receiverEval, cha, false);
    visitAll(callGraph, project, scope);
    return callGraph.getCallGraph();
  }

  public static void visitAll(UastVisitor uastVisitor, Project project, AnalysisScope scope) {
    List<UFile> res = new ArrayList<UFile>();
    UastContext uastContext = ServiceManager.getService(project, UastContext.class);
    scope.accept(
        virtualFile -> {
          if (!uastContext.isFileSupported(virtualFile.getName())) {
            return true;
          } else {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile != null) {
              UElement uFile = uastContext.convertElementWithParent(psiFile, UFile.class);
              uFile.accept(uastVisitor);
              return true;
            } else {
              return true;
            }
          }
        });
  }

  /*public static final Collection visitAll(
          @NotNull final UastVisitor $this$visitAll,
          @NotNull final Project project,
          @NotNull AnalysisScope scope) {
      ArrayList res = new ArrayList();
      final UastContext uastContext =
              (UastContext) ServiceManager.getService(project, UastContext.class);
      scope.accept(
              (Processor)
                      (new Processor() {
                          public boolean process(Object var1) {
                              return this.process((VirtualFile) var1);
                          }

                          public final boolean process(VirtualFile virtualFile) {
                              if (!uastContext.isFileSupported(virtualFile.getName())) {
                                  return true;
                              } else {
                                  PsiFile var10000 = PsiManager.getInstance(project).findFile(virtualFile);
                                  if (var10000 != null) {
                                      PsiFile psiFile = var10000;
                                      UastLanguagePlugin $this$convertWithParent$iv =
                                              (UastLanguagePlugin) uastContext;
                                      boolean $i$f$convertWithParent = false;
                                      UFile var6;
                                      if ((PsiElement) psiFile == null) {
                                          var6 = null;
                                      } else {
                                          UElement var7 =
                                                  $this$convertWithParent$iv.convertElementWithParent(
                                                          (PsiElement) psiFile, UFile.class);
                                          if (!(var7 instanceof UFile)) {
                                              var7 = null;
                                          }

                                          var6 = (UFile) var7;
                                      }

                                      if (var6 != null) {
                                          UFile file = var6;
                                          file.accept($this$visitAll);
                                          return true;
                                      } else {
                                          return true;
                                      }
                                  } else {
                                      return true;
                                  }
                              }
                          }
                      }));
      return (Collection) res;
  }*/
}

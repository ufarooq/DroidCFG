package edu.ucr.droidCFG.harness;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.uast.UMethod;
import java.util.Set;

public class Utils {

    public static Module getModuleForMethod(UMethod method, Project mProject) {
        VirtualFile virtualFile = method.getPsi().getContainingClass().getContainingFile().getVirtualFile();
        Module module = ModuleUtil.findModuleForFile(virtualFile, mProject);
        return module;
    }

    public static PsiMethod findMethod(PsiElement element) {
        PsiMethod method = (element instanceof PsiMethod) ? (PsiMethod) element :
                PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null && method.getContainingClass() instanceof PsiAnonymousClass) {
            return findMethod(method.getParent());
        }
        return method;
    }

}


package edu.ucr.droidCFG.harness;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class HarnessAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
        final Module module = anActionEvent.getData(LangDataKeys.MODULE);
        PsiManager psiManager = PsiManager.getInstance(project);
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
            Messages.showMessageDialog("Facet is NULL", "Facet", Messages.getInformationIcon());
        } else {
            CallGraphManager callGraphManager = new CallGraphManager(project, module);

        }
    }
}

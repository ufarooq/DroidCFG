package edu.ucr.droidCFG.harness;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.compiler.AndroidDexCompiler;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EntryPointsManager {
  private EntryPointsManager() {}

  public static Set<PsiClass> getEntryPointClasses(
      @NotNull Project project, @NotNull Module module) {
    PsiManager psiManager = PsiManager.getInstance(project);
    Set<PsiClass> appComponents = new HashSet<>();
    ApkFacet apkFacet = ApkFacet.getInstance(module);

    AndroidFacet facet = AndroidFacet.getInstance(module);
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    File classesFolder = androidModuleModel.getMainArtifact().getClassesFolder();
    System.out.println("Classes: " + classesFolder.getAbsolutePath());
    if (classesFolder.exists()) {
      for (File file : classesFolder.listFiles()) {
        System.out.println(file.getName());
      }
    }
    // TODO: androidModuleModel.getMainArtifact().getClassesFolder()
    if (facet == null) {
      System.out.println("Facet is NULL");
    } else {
      // PsiCFGScene mCFGScene = PsiCFGScene.createFreshInstance(project);
      // Set<PsiClass> layoutResults = processLayouts(project, module, facet);
      Set<PsiClass> manifestResults = processManifest(facet);
      // ImmutableSet<PsiClass> moduleEntries = Sets.union(layoutResults,
      // manifestResults).immutableCopy();
      appComponents.addAll(manifestResults);
    }
    return appComponents;
  }

  public static Set<PsiClass> processManifest(AndroidFacet facet) {
    Set<PsiClass> manifestClasses = new HashSet<>();
    Manifest manifest = facet.getManifest();
    //Manifest manifest = Manifest.getMainManifest(facet);

    List<Activity> activities = manifest.getApplication().getActivities();
    List<ActivityAlias> activityAliases = manifest.getApplication().getActivityAliases();
    List<Service> services = manifest.getApplication().getServices();
    List<Provider> providers = manifest.getApplication().getProviders();
    List<Receiver> receivers = manifest.getApplication().getReceivers();

    activities
        .parallelStream()
        .forEach(
            activity -> {
              PsiClass activityClass = activity.getActivityClass().getValue();
              manifestClasses.add(activityClass);
            });

    activityAliases
        .parallelStream()
        .forEach(
            alias -> {
              PsiClass activityClass = alias.getTargetActivity().getValue();
              manifestClasses.add(activityClass);
            });

    services
        .parallelStream()
        .forEach(
            service -> {
              PsiClass serviceClass = service.getServiceClass().getValue();
              manifestClasses.add(serviceClass);
            });

    providers
        .parallelStream()
        .forEach(
            provider -> {
              PsiClass providerClass = provider.getProviderClass().getValue();
              manifestClasses.add(providerClass);
            });

    receivers
        .parallelStream()
        .forEach(
            receiver -> {
              PsiClass receiverClass = receiver.getReceiverClass().getValue();
              manifestClasses.add(receiverClass);
            });

    return manifestClasses;
  }

  /*public static Set<PsiClass> processLayouts(Project project, Module module, AndroidFacet facet) {
    Set<PsiClass> layoutActivitiesAndFragments = new HashSet<>();
    for (VirtualFile resourceDir : ResourceFolderManager.getInstance(facet).getFolders()) {
      for (VirtualFile folder : resourceDir.getChildren()) {
        if (folder.getName().startsWith(FD_RES_LAYOUT) && folder.isDirectory()) {
          for (VirtualFile file : folder.getChildren()) {
            if (file.getName().endsWith(DOT_XML)
                && file.getParent() != null
                && file.getParent().getName().startsWith(FD_RES_LAYOUT)) {
              System.out.println(file.getName() + ":" + file.getPath());
              PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
              XmlFile xmlFile = (XmlFile) psiFile;
              PsiClass contextClass = AndroidPsiUtils.getContextClass(module, xmlFile);
              // Pair<Module, PsiClass> pair = new ImmutablePair(module, contextClass);
              layoutActivitiesAndFragments.add(contextClass);
              System.out.println(contextClass != null ? contextClass.getQualifiedName() : "NULL");
            }
          }
        }
      }
    }
    return layoutActivitiesAndFragments;
  }*/
}

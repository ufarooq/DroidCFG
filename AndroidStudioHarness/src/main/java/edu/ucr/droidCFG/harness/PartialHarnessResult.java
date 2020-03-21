package edu.ucr.droidCFG.harness;

import com.android.tools.layoutlib.annotations.NotNull;
import com.intellij.psi.PsiMethod;

import java.util.Objects;

public class PartialHarnessResult {
    // this method is where a callback was registered, need to find which component calls it
    private final PsiMethod srcMethod;
    private final PsiMethod entryMethod;

    public PartialHarnessResult(@NotNull PsiMethod srcMethod, @NotNull PsiMethod entryMethod) {
        this.srcMethod = srcMethod;
        this.entryMethod = entryMethod;
    }

    public PsiMethod getSrcMethod() {
        return srcMethod;
    }

    public PsiMethod getEntryMethod() {
        return entryMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialHarnessResult that = (PartialHarnessResult) o;
        return srcMethod.equals(that.srcMethod) &&
                entryMethod.equals(that.entryMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcMethod, entryMethod);
    }
}

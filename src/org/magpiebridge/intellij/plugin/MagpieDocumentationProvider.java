package org.magpiebridge.intellij.plugin;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MagpieDocumentationProvider extends AbstractDocumentationProvider {

    protected abstract String generateInternal(@NotNull PsiElement element, @Nullable PsiElement originalElement) throws Exception;

    protected Service service;
    protected DocumentationProvider base;

    MagpieDocumentationProvider setServer(Service s) {
        service = s;
        return this;
    }

    MagpieDocumentationProvider setBase(com.intellij.lang.documentation.DocumentationProvider base) {
        this.base = base;
        return this;
    }

    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    private String combine(String first, String second) {
        if (first != null) {
            if (second != null) {
                return first + "<HR>" + second;
            } else {
                return first;
            }
        } else {
            return second;
        }
    }

    @Nullable
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        try {
            return combine(
                    ReadAction.compute(() -> generateInternal(element, originalElement)),
                    base.generateDoc(element, originalElement));
        } catch (Exception e) {
            e.printStackTrace();
            return base.generateDoc(element, originalElement);
        }
    }
}

package org.magpiebridge.intellij.plugin;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magpiebridge.intellij.client.LanguageClient;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class Inlays implements InlayHintsProvider {
    private interface CodeLensInlay {
        CodeLens codeLens();
         LanguageServer endpoint();
    }

    private static Actions<CodeLensInlay> actions = new Actions<>();

    public void addLens(Document doc, CodeLens lens, LanguageServer endpoint) {
        Logger.getInstance(getClass()).info("recording " + lens);

        int startOffset = doc.getLineStartOffset(lens.getRange().getStart().getLine()) + lens.getRange().getStart().getCharacter();
        int endOffset = doc.getLineStartOffset(lens.getRange().getEnd().getLine()) + lens.getRange().getEnd().getCharacter();
        actions.recordAction(doc, startOffset, endOffset, new CodeLensInlay() {
            @Override
            public CodeLens codeLens() {
                return lens;
            }

            @Override
            public LanguageServer endpoint() {
                return endpoint;
            }
        });
    }

    @Override
    public boolean isVisibleInSettings() {
        return true;
    }

    SettingsKey magpieInlaySettings = new SettingsKey("MagpieSettingsKey");

    @NotNull
    @Override
    public SettingsKey getKey() {
        return magpieInlaySettings;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return "MagpieBridge";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return null;
    }

    private boolean showLenses = true;

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull Object o) {
        return new ImmediateConfigurable() {
            @Override
            public void reset() {

            }

            @NotNull
            @Override
            public String getMainCheckboxText() {
                return "MagpieBridge";
            }

            @NotNull
            @Override
            public List<Case> getCases() {
                return Arrays.asList(
                        new Case("show lenses", "showLenses",
                                new Function0<Boolean>() {
                                    @Override
                                    public Boolean invoke() {
                                        return showLenses;
                                    }
                                },
                                new Function1<Boolean, Unit>() {
                                    @Override
                                    public Unit invoke(Boolean aBoolean) {
                                        showLenses = aBoolean;
                                        return null;
                                    }
                                },
                                "desciption"));
            }

            @NotNull
            @Override
            public JComponent createComponent(@NotNull ChangeListener changeListener) {
                return new JPanel();
            }
        };
    }

    public static final Topic<Consumer<Settings>> MAGPIE_SETTINGS_CHANGED =
            new Topic<>("MAGPIE_SETTINGS_CHANGED", (Class<Consumer<Settings>>)(Class<?>)Consumer.class);

    private static class Settings implements Serializable {

        private boolean showDetails = true;

        public boolean isShowDetails() {
             return showDetails;
        }

        public void setShowDetails(boolean showDetails) {
            this.showDetails = showDetails;
            settingsChanged();
        }

        private void settingsChanged() {
            ApplicationManager.getApplication().getMessageBus().syncPublisher(MAGPIE_SETTINGS_CHANGED).accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Settings settings = (Settings) o;
            return isShowDetails() == settings.isShowDetails();
        }

        @Override
        public int hashCode() {
            return Objects.hash(isShowDetails());
        }
    }

    @NotNull
    @Override
    public Object createSettings() {
        return new Settings();
    }

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull Object o, @NotNull InlayHintsSink inlayHintsSink) {
        PresentationFactory factory = new PresentationFactory((EditorImpl)editor);
        return new InlayHintsCollector() {
            @Override
            public boolean collect(@NotNull PsiElement element, @NotNull Editor editor1, @NotNull InlayHintsSink inlayHintsSink) {
                int startOffset = element.getTextRange().getStartOffset();
                int startLine = editor1.getDocument().getLineNumber(startOffset);
                int startLineStart = editor1.getDocument().getLineStartOffset(startLine);
                int startColumn = startOffset - startLineStart;

                int endOffset = element.getTextRange().getEndOffset();
                int endLine = editor1.getDocument().getLineNumber(endOffset);
                int endLineStart = editor1.getDocument().getLineStartOffset(endLine);
                int endColumn = endOffset - endLineStart;

                Map.Entry<Integer, CodeLensInlay>[] lenses = actions.getAction(editor, (startOffset + endOffset) / 2);

                if (lenses != null) {
                    CodeLens lens = lenses[0].getValue().codeLens();
                    String lensText = lens.getCommand().getTitle();
                    InlayPresentation inlay = factory.smallText(lensText);
                    inlay = factory.onClick(inlay, MouseButton.Left, (event, point) -> {
                        ExecuteCommandParams ecp = new ExecuteCommandParams();
                        ecp.setCommand(lens.getCommand().getCommand());
                        ecp.setArguments(lens.getCommand().getArguments());
                        lenses[0].getValue().endpoint().getWorkspaceService().executeCommand(ecp);
                        return null;
                    });

                    int lensLine = lens.getRange().getEnd().getLine();
                    int lensCol = lens.getRange().getEnd().getCharacter();
                    int lensOffset = editor1.getDocument().getLineStartOffset(lensLine) + lensCol;

                    if (lensText.length() > 15) {
                        inlayHintsSink.addBlockElement(lensOffset, true, false, 1, inlay);
                    } else {
                        inlayHintsSink.addInlineElement(lensOffset, true, inlay);
                    }
                }

                return true;
            }
        };
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return true;
    }
}

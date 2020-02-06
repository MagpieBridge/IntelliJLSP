package org.magpiebridge.intellij.plugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class Actions<T> {
    private final Map<Document, NavigableMap<Integer, T>> lowerBound = new LinkedHashMap<>();
    private final Map<Document, NavigableMap<Integer, T>> upperBound = new LinkedHashMap<>();

    public void clear() {
        upperBound.clear();
        lowerBound.clear();
    }

    @NotNull
    public Collection<T> getAction(@NotNull Editor editor) {
        if (lowerBound.containsKey(editor.getDocument())) {
            return lowerBound.get(editor.getDocument()).values();
        } else {
            return Collections.emptyList();
        }
    }

    @Nullable
    public Map.Entry<Integer,T>[] getAction(@NotNull Editor editor, int offset) {
        Document doc = editor.getDocument();

        Logger.getInstance(getClass()).info("getPopup: looking at " + offset + " of " + doc);

        if (lowerBound.containsKey(doc) && lowerBound.get(doc).floorEntry(offset) != null) {
            if (upperBound.get(doc).ceilingEntry(offset) != null) {
                Map.Entry<Integer,T> l = lowerBound.get(doc).floorEntry(offset);
                Map.Entry<Integer,T> u = upperBound.get(doc).ceilingEntry(offset);
                if (l.getValue() == u.getValue()) {
                    String nm = "getPopup: ";
                    Logger.getInstance(getClass()).info(nm + "found popup at " + u.getKey() + " " + l.getKey());
                    logStackTrace(nm);
                    return new Map.Entry[]{l, u};
                }
            }
        }

        return null;
    }
    public void recordAction(Document doc, int startOffset, int endOffset, T fixes) {
        if (!lowerBound.containsKey(doc)) {
            lowerBound.put(doc, new TreeMap<>());
        }
        lowerBound.get(doc).put(startOffset, fixes);
        if (!upperBound.containsKey(doc)) {
            upperBound.put(doc, new TreeMap<>());
        }
        upperBound.get(doc).put(endOffset, fixes);

        Logger.getInstance(getClass()).info("recordAction: fix from " + startOffset + " to " + endOffset + ": " + fixes);
        logStackTrace("recordAction: ");
    }

    private void logStackTrace(String nm) {
        Throwable x = new Throwable();
        x.fillInStackTrace();
        StringWriter w = new StringWriter();
        x.printStackTrace(new PrintWriter(w));
        Logger.getInstance(getClass()).info( nm + w.toString());
    }

}

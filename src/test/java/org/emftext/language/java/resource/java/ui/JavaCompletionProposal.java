package org.emftext.language.java.resource.java.ui;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.emftext.language.java.resource.java.mopp.JavaExpectedTerminal;

/**
 * Kopiert aus org.emftext.language.java.resource.java.ui_1.4.0.v201207310007
 */
public class JavaCompletionProposal implements Comparable<JavaCompletionProposal> {
    private EObject root;
    private JavaExpectedTerminal expectedTerminal;
    private String insertString;
    private String displayString;
    private String prefix;
    private boolean matchesPrefix;
    private EStructuralFeature structuralFeature;
    private EObject container;

    public JavaCompletionProposal(JavaExpectedTerminal expectedTerminal, String insertString, String prefix, boolean matchesPrefix,
            EStructuralFeature structuralFeature, EObject container) {
        this.expectedTerminal = expectedTerminal;
        this.insertString = insertString;
        this.prefix = prefix;
        this.matchesPrefix = matchesPrefix;
        this.structuralFeature = structuralFeature;
        this.container = container;
    }

    public JavaCompletionProposal(JavaExpectedTerminal expectedTerminal, String insertString, String prefix, boolean matchesPrefix,
            EStructuralFeature structuralFeature, EObject container, String displayString) {
        this(expectedTerminal, insertString, prefix, matchesPrefix, structuralFeature, container);
        this.displayString = displayString;
    }

    public EObject getRoot() {
        return this.root;
    }

    public void setRoot(EObject root) {
        this.root = root;
    }

    public String getInsertString() {
        return this.insertString;
    }

    public String getDisplayString() {
        return this.displayString;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public boolean getMatchesPrefix() {
        return this.matchesPrefix;
    }

    public boolean isStructuralFeature() {
        return this.structuralFeature != null;
    }

    public EStructuralFeature getStructuralFeature() {
        return this.structuralFeature;
    }

    public EObject getContainer() {
        return this.container;
    }

    public JavaExpectedTerminal getExpectedTerminal() {
        return this.expectedTerminal;
    }

    public boolean equals(Object object) {
        if ((object instanceof JavaCompletionProposal)) {
            JavaCompletionProposal other = (JavaCompletionProposal) object;
            return other.getInsertString().equals(getInsertString());
        }
        return false;
    }

    public int hashCode() {
        return getInsertString().hashCode();
    }

    public int compareTo(JavaCompletionProposal object) {
        if ((object instanceof JavaCompletionProposal)) {
            JavaCompletionProposal other = object;

            int startCompare = (this.matchesPrefix ? 1 : 0) - (other.getMatchesPrefix() ? 1 : 0);

            return startCompare == 0 ? getInsertString().compareTo(other.getInsertString()) : -startCompare;
        }
        return -1;
    }

    public String toString() {
        String result = (this.container == null ? "null" : this.container.eClass().getName()) + ".";
        result = result + (this.structuralFeature == null ? "null" : this.structuralFeature.getName());
        result = result + ": " + this.insertString;
        return result;
    }

    public void materialize(Runnable code) {
        this.expectedTerminal.materialize(code);
    }
}
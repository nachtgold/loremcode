package org.emftext.language.java.resource.java.ui;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emftext.language.java.resource.java.IJavaExpectedElement;
import org.emftext.language.java.resource.java.IJavaMetaInformation;
import org.emftext.language.java.resource.java.IJavaReferenceMapping;
import org.emftext.language.java.resource.java.IJavaReferenceResolveResult;
import org.emftext.language.java.resource.java.IJavaReferenceResolverSwitch;
import org.emftext.language.java.resource.java.IJavaTextParser;
import org.emftext.language.java.resource.java.IJavaTextResource;
import org.emftext.language.java.resource.java.IJavaTokenResolver;
import org.emftext.language.java.resource.java.IJavaTokenResolverFactory;
import org.emftext.language.java.resource.java.grammar.JavaBooleanTerminal;
import org.emftext.language.java.resource.java.grammar.JavaContainmentTrace;
import org.emftext.language.java.resource.java.grammar.JavaEnumerationTerminal;
import org.emftext.language.java.resource.java.grammar.JavaSyntaxElement;
import org.emftext.language.java.resource.java.mopp.JavaAttributeValueProvider;
import org.emftext.language.java.resource.java.mopp.JavaContainedFeature;
import org.emftext.language.java.resource.java.mopp.JavaElementMapping;
import org.emftext.language.java.resource.java.mopp.JavaExpectedBooleanTerminal;
import org.emftext.language.java.resource.java.mopp.JavaExpectedCsString;
import org.emftext.language.java.resource.java.mopp.JavaExpectedEnumerationTerminal;
import org.emftext.language.java.resource.java.mopp.JavaExpectedStructuralFeature;
import org.emftext.language.java.resource.java.mopp.JavaExpectedTerminal;
import org.emftext.language.java.resource.java.mopp.JavaMetaInformation;
import org.emftext.language.java.resource.java.mopp.JavaReferenceResolveResult;
import org.emftext.language.java.resource.java.util.JavaEObjectUtil;
import org.emftext.language.java.resource.java.util.JavaStringUtil;

public class JavaCodeCompletionHelper {
    private JavaAttributeValueProvider attributeValueProvider = new JavaAttributeValueProvider();

    private IJavaMetaInformation metaInformation = new JavaMetaInformation();

    public JavaCompletionProposal[] computeCompletionProposals(IJavaTextResource originalResource, String content, int cursorOffset) {
        ResourceSet resourceSet = new ResourceSetImpl();

        IJavaTextResource resource = (IJavaTextResource) resourceSet.createResource(originalResource.getURI());
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        IJavaMetaInformation metaInformation = resource.getMetaInformation();
        IJavaTextParser parser = metaInformation.createParser(inputStream, null);
        JavaExpectedTerminal[] expectedElements = parseToExpectedElements(parser, resource, cursorOffset);
        if (expectedElements == null) {
            return new JavaCompletionProposal[0];
        }
        if (expectedElements.length == 0) {
            return new JavaCompletionProposal[0];
        }
        List<JavaExpectedTerminal> expectedAfterCursor = Arrays.asList(getElementsExpectedAt(expectedElements, cursorOffset));
        List<JavaExpectedTerminal> expectedBeforeCursor = Arrays.asList(getElementsExpectedAt(expectedElements, cursorOffset - 1));
        setPrefixes(expectedAfterCursor, content, cursorOffset);
        setPrefixes(expectedBeforeCursor, content, cursorOffset);

        Collection<JavaCompletionProposal> allProposals = new LinkedHashSet<JavaCompletionProposal>();
        Collection<JavaCompletionProposal> rightProposals = deriveProposals(expectedAfterCursor, content, resource, cursorOffset);
        Collection<JavaCompletionProposal> leftProposals = deriveProposals(expectedBeforeCursor, content, resource, cursorOffset - 1);
        removeKeywordsEndingBeforeIndex(leftProposals, cursorOffset);

        allProposals.addAll(leftProposals);

        int leftMatchingProposals = 0;
        for (JavaCompletionProposal leftProposal : leftProposals) {
            if (leftProposal.getMatchesPrefix()) {
                leftMatchingProposals++;
            }
        }
        if (leftMatchingProposals == 0) {
            allProposals.addAll(rightProposals);
        }

        List<JavaCompletionProposal> sortedProposals = new ArrayList<JavaCompletionProposal>(allProposals);
        Collections.sort(sortedProposals);
        EObject root = null;
        if (!resource.getContents().isEmpty()) {
            root = (EObject) resource.getContents().get(0);
        }
        for (JavaCompletionProposal proposal : sortedProposals) {
            proposal.setRoot(root);
        }
        return (JavaCompletionProposal[]) sortedProposals.toArray(new JavaCompletionProposal[sortedProposals.size()]);
    }

    public JavaExpectedTerminal[] parseToExpectedElements(IJavaTextParser parser, IJavaTextResource resource, int cursorOffset) {
        List<JavaExpectedTerminal> expectedElements = parser.parseToExpectedElements(null, resource, cursorOffset);
        if (expectedElements == null) {
            return new JavaExpectedTerminal[0];
        }
        removeDuplicateEntries(expectedElements);
        removeInvalidEntriesAtEnd(expectedElements);
        return (JavaExpectedTerminal[]) expectedElements.toArray(new JavaExpectedTerminal[expectedElements.size()]);
    }

    protected void removeDuplicateEntries(List<JavaExpectedTerminal> expectedElements) {
        int size = expectedElements.size();

        Map<Integer, List<JavaExpectedTerminal>> map = new LinkedHashMap<Integer, List<JavaExpectedTerminal>>();
        for (int i = 0; i < size; i++) {
            JavaExpectedTerminal elementAtIndex = (JavaExpectedTerminal) expectedElements.get(i);
            int start1 = elementAtIndex.getStartExcludingHiddenTokens();
            List<JavaExpectedTerminal> list = map.get(Integer.valueOf(start1));
            if (list == null) {
                list = new ArrayList<JavaExpectedTerminal>();
                map.put(Integer.valueOf(start1), list);
            }
            list.add(elementAtIndex);
        }

        for (Iterator<Integer> i$ = map.keySet().iterator(); i$.hasNext();) {
            int position = ((Integer) i$.next()).intValue();
            List<JavaExpectedTerminal> list = map.get(Integer.valueOf(position));
            removeDuplicateEntriesFromBucket(list);
        }

        expectedElements.clear();
        for (Iterator<Integer> i$ = map.keySet().iterator(); i$.hasNext();) {
            int position = i$.next().intValue();
            List<JavaExpectedTerminal> list = map.get(Integer.valueOf(position));
            expectedElements.addAll(list);
        }
    }

    protected void removeDuplicateEntriesFromBucket(List<JavaExpectedTerminal> expectedElements) {
        int size = expectedElements.size();
        IJavaExpectedElement terminal;
        int j;
        for (int i = 0; i < size - 1; i++) {
            JavaExpectedTerminal elementAtIndex = (JavaExpectedTerminal) expectedElements.get(i);
            terminal = elementAtIndex.getTerminal();
            for (j = i + 1; j < size;) {
                JavaExpectedTerminal elementAtNext = (JavaExpectedTerminal) expectedElements.get(j);
                if (terminal.equals(elementAtNext.getTerminal())) {
                    expectedElements.remove(j);
                    size--;
                } else {
                    j++;
                }
            }
        }
    }

    protected void removeInvalidEntriesAtEnd(List<JavaExpectedTerminal> expectedElements) {
        for (int i = 0; i < expectedElements.size() - 1;) {
            JavaExpectedTerminal elementAtIndex = (JavaExpectedTerminal) expectedElements.get(i);
            JavaExpectedTerminal elementAtNext = (JavaExpectedTerminal) expectedElements.get(i + 1);

            JavaSyntaxElement symtaxElementOfThis = elementAtIndex.getTerminal().getSymtaxElement();
            JavaSyntaxElement symtaxElementOfNext = elementAtNext.getTerminal().getSymtaxElement();
            boolean differentParent = symtaxElementOfNext.getParent() != symtaxElementOfThis.getParent();

            boolean sameStartExcludingHiddenTokens = elementAtIndex.getStartExcludingHiddenTokens() == elementAtNext
                    .getStartExcludingHiddenTokens();
            boolean differentFollowSet = elementAtIndex.getFollowSetID() != elementAtNext.getFollowSetID();
            if ((sameStartExcludingHiddenTokens) && (differentFollowSet) && (!differentParent))
                expectedElements.remove(i + 1);
            else
                i++;
        }
    }

    protected void removeKeywordsEndingBeforeIndex(Collection<JavaCompletionProposal> proposals, int index) {
        List<JavaCompletionProposal> toRemove = new ArrayList<JavaCompletionProposal>();
        for (JavaCompletionProposal proposal : proposals) {
            JavaExpectedTerminal expectedTerminal = proposal.getExpectedTerminal();
            IJavaExpectedElement terminal = expectedTerminal.getTerminal();
            if ((terminal instanceof JavaExpectedCsString)) {
                JavaExpectedCsString csString = (JavaExpectedCsString) terminal;
                int startExcludingHiddenTokens = expectedTerminal.getStartExcludingHiddenTokens();
                if (startExcludingHiddenTokens + csString.getValue().length() - 1 < index) {
                    toRemove.add(proposal);
                }
            }
        }
        proposals.removeAll(toRemove);
    }

    protected String findPrefix(List<JavaExpectedTerminal> expectedElements, JavaExpectedTerminal expectedAtCursor, String content,
            int cursorOffset) {
        if (cursorOffset < 0) {
            return "";
        }
        int end = 0;
        for (JavaExpectedTerminal expectedElement : expectedElements) {
            if (expectedElement == expectedAtCursor) {
                int start = expectedElement.getStartExcludingHiddenTokens();
                if ((start < 0) || (start >= 2147483647))
                    break;
                end = start;
                break;
            }

        }

        end = Math.min(end, cursorOffset);
        String prefix = content.substring(end, Math.min(content.length(), cursorOffset));
        return prefix;
    }

    protected Collection<JavaCompletionProposal> deriveProposals(List<JavaExpectedTerminal> expectedElements, String content,
            IJavaTextResource resource, int cursorOffset) {
        Collection<JavaCompletionProposal> resultSet = new LinkedHashSet<JavaCompletionProposal>();
        for (JavaExpectedTerminal expectedElement : expectedElements) {
            resultSet.addAll(deriveProposals(expectedElement, content, resource, cursorOffset));
        }
        return resultSet;
    }

    protected Collection<JavaCompletionProposal> deriveProposals(final JavaExpectedTerminal expectedTerminal, String content,
            IJavaTextResource resource, int cursorOffset) {
        IJavaExpectedElement expectedElement = expectedTerminal.getTerminal();
        if ((expectedElement instanceof JavaExpectedCsString)) {
            JavaExpectedCsString csString = (JavaExpectedCsString) expectedElement;
            return handleKeyword(expectedTerminal, csString, expectedTerminal.getPrefix());
        }
        if ((expectedElement instanceof JavaExpectedBooleanTerminal)) {
            JavaExpectedBooleanTerminal expectedBooleanTerminal = (JavaExpectedBooleanTerminal) expectedElement;
            return handleBooleanTerminal(expectedTerminal, expectedBooleanTerminal, expectedTerminal.getPrefix());
        }
        if ((expectedElement instanceof JavaExpectedEnumerationTerminal)) {
            JavaExpectedEnumerationTerminal expectedEnumerationTerminal = (JavaExpectedEnumerationTerminal) expectedElement;
            return handleEnumerationTerminal(expectedTerminal, expectedEnumerationTerminal, expectedTerminal.getPrefix());
        }
        if ((expectedElement instanceof JavaExpectedStructuralFeature)) {
            final JavaExpectedStructuralFeature expectedFeature = (JavaExpectedStructuralFeature) expectedElement;
            final EStructuralFeature feature = expectedFeature.getFeature();
            final EClassifier featureType = feature.getEType();
            final EObject container = findCorrectContainer(expectedTerminal);

            final Collection<JavaCompletionProposal> proposals = new ArrayList<JavaCompletionProposal>();
            expectedTerminal.materialize(new Runnable() {
                public void run() {
                    if ((feature instanceof EReference)) {
                        EReference reference = (EReference) feature;
                        if ((featureType instanceof EClass)) {
                            if (!reference.isContainment()) {
                                proposals.addAll(JavaCodeCompletionHelper.this.handleNCReference(expectedTerminal, container, reference,
                                        expectedTerminal.getPrefix(), expectedFeature.getTokenName()));
                            }
                        }
                    } else if ((feature instanceof EAttribute)) {
                        EAttribute attribute = (EAttribute) feature;
                        if ((featureType instanceof EEnum)) {
                            EEnum enumType = (EEnum) featureType;
                            proposals.addAll(JavaCodeCompletionHelper.this.handleEnumAttribute(expectedTerminal, expectedFeature, enumType,
                                    expectedTerminal.getPrefix(), container));
                        } else {
                            proposals.addAll(JavaCodeCompletionHelper.this.handleAttribute(expectedTerminal, expectedFeature, container,
                                    attribute, expectedTerminal.getPrefix()));
                        }

                    }

                }
            });
            return proposals;
        }

        return Collections.emptyList();
    }

    protected Collection<JavaCompletionProposal> handleEnumAttribute(JavaExpectedTerminal expectedTerminal,
            JavaExpectedStructuralFeature expectedFeature, EEnum enumType, String prefix, EObject container) {
        Collection<EEnumLiteral> enumLiterals = enumType.getELiterals();
        Collection<JavaCompletionProposal> result = new LinkedHashSet<JavaCompletionProposal>();
        for (EEnumLiteral literal : enumLiterals) {
            String unResolvedLiteral = literal.getLiteral();

            IJavaTokenResolverFactory tokenResolverFactory = this.metaInformation.getTokenResolverFactory();
            IJavaTokenResolver tokenResolver = tokenResolverFactory.createTokenResolver(expectedFeature.getTokenName());
            String resolvedLiteral = tokenResolver.deResolve(unResolvedLiteral, expectedFeature.getFeature(), container);
            boolean matchesPrefix = matches(resolvedLiteral, prefix);
            result.add(new JavaCompletionProposal(expectedTerminal, resolvedLiteral, prefix, matchesPrefix, expectedFeature.getFeature(),
                    container));
        }
        return result;
    }

    protected Collection<JavaCompletionProposal> handleNCReference(JavaExpectedTerminal expectedTerminal, EObject container,
            EReference reference, String prefix, String tokenName) {
        IJavaReferenceResolverSwitch resolverSwitch = this.metaInformation.getReferenceResolverSwitch();
        IJavaTokenResolverFactory tokenResolverFactory = this.metaInformation.getTokenResolverFactory();
        IJavaReferenceResolveResult<EObject> result = new JavaReferenceResolveResult<EObject>(true);
        resolverSwitch.resolveFuzzy(prefix, container, reference, 0, result);
        Collection<IJavaReferenceMapping<EObject>> mappings = result.getMappings();
        if (mappings != null) {
            Collection<JavaCompletionProposal> resultSet = new LinkedHashSet<JavaCompletionProposal>();
            for (IJavaReferenceMapping<EObject> mapping : mappings) {
                if ((mapping instanceof JavaElementMapping)) {
                    JavaElementMapping<EObject> elementMapping = (JavaElementMapping<EObject>) mapping;
                    IJavaTokenResolver tokenResolver = tokenResolverFactory.createTokenResolver(tokenName);
                    String identifier = tokenResolver.deResolve(elementMapping.getIdentifier(), reference, container);
                    boolean matchesPrefix = matches(identifier, prefix);
                    resultSet.add(new JavaCompletionProposal(expectedTerminal, identifier, prefix, matchesPrefix, reference, container));
                }
            }
            return resultSet;
        }
        return Collections.emptyList();
    }

    protected Collection<JavaCompletionProposal> handleAttribute(JavaExpectedTerminal expectedTerminal,
            JavaExpectedStructuralFeature expectedFeature, EObject container, EAttribute attribute, String prefix) {
        Collection<JavaCompletionProposal> resultSet = new LinkedHashSet<JavaCompletionProposal>();
        Object[] defaultValues = this.attributeValueProvider.getDefaultValues(attribute);
        if (defaultValues != null) {
            for (Object defaultValue : defaultValues) {
                if (defaultValue != null) {
                    IJavaTokenResolverFactory tokenResolverFactory = this.metaInformation.getTokenResolverFactory();
                    String tokenName = expectedFeature.getTokenName();
                    if (tokenName != null) {
                        IJavaTokenResolver tokenResolver = tokenResolverFactory.createTokenResolver(tokenName);
                        if (tokenResolver != null) {
                            String defaultValueAsString = tokenResolver.deResolve(defaultValue, attribute, container);
                            boolean matchesPrefix = matches(defaultValueAsString, prefix);
                            resultSet.add(new JavaCompletionProposal(expectedTerminal, defaultValueAsString, prefix, matchesPrefix,
                                    expectedFeature.getFeature(), container));
                        }
                    }
                }
            }
        }
        return resultSet;
    }

    protected Collection<JavaCompletionProposal> handleKeyword(JavaExpectedTerminal expectedTerminal, JavaExpectedCsString csString,
            String prefix) {
        String proposal = csString.getValue();
        boolean matchesPrefix = matches(proposal, prefix);
        return Collections.singleton(new JavaCompletionProposal(expectedTerminal, proposal, prefix, matchesPrefix, null, null));
    }

    protected Collection<JavaCompletionProposal> handleBooleanTerminal(JavaExpectedTerminal expectedTerminal,
            JavaExpectedBooleanTerminal expectedBooleanTerminal, String prefix) {
        Collection<JavaCompletionProposal> result = new LinkedHashSet<JavaCompletionProposal>(2);
        JavaBooleanTerminal booleanTerminal = expectedBooleanTerminal.getBooleanTerminal();
        result.addAll(handleLiteral(expectedTerminal, booleanTerminal.getAttribute(), prefix, booleanTerminal.getTrueLiteral()));
        result.addAll(handleLiteral(expectedTerminal, booleanTerminal.getAttribute(), prefix, booleanTerminal.getFalseLiteral()));
        return result;
    }

    protected Collection<JavaCompletionProposal> handleEnumerationTerminal(JavaExpectedTerminal expectedTerminal,
            JavaExpectedEnumerationTerminal expectedEnumerationTerminal, String prefix) {
        Collection<JavaCompletionProposal> result = new LinkedHashSet<JavaCompletionProposal>(2);
        JavaEnumerationTerminal enumerationTerminal = expectedEnumerationTerminal.getEnumerationTerminal();
        Map<String, String> literalMapping = enumerationTerminal.getLiteralMapping();
        for (String literalName : literalMapping.keySet()) {
            result.addAll(handleLiteral(expectedTerminal, enumerationTerminal.getAttribute(), prefix,
                    (String) literalMapping.get(literalName)));
        }
        return result;
    }

    protected Collection<JavaCompletionProposal> handleLiteral(JavaExpectedTerminal expectedTerminal, EAttribute attribute, String prefix,
            String literal) {
        if ("".equals(literal)) {
            return Collections.emptySet();
        }
        boolean matchesPrefix = matches(literal, prefix);
        return Collections.singleton(new JavaCompletionProposal(expectedTerminal, literal, prefix, matchesPrefix, null, null));
    }

    protected void setPrefixes(List<JavaExpectedTerminal> expectedElements, String content, int cursorOffset) {
        if (cursorOffset < 0) {
            return;
        }
        for (JavaExpectedTerminal expectedElement : expectedElements) {
            String prefix = findPrefix(expectedElements, expectedElement, content, cursorOffset);
            expectedElement.setPrefix(prefix);
        }
    }

    public JavaExpectedTerminal[] getElementsExpectedAt(JavaExpectedTerminal[] allExpectedElements, int cursorOffset) {
        List<JavaExpectedTerminal> expectedAtCursor = new ArrayList<JavaExpectedTerminal>();
        for (int i = 0; i < allExpectedElements.length; i++) {
            JavaExpectedTerminal expectedElement = allExpectedElements[i];
            int startIncludingHidden = expectedElement.getStartIncludingHiddenTokens();
            int end = getEnd(allExpectedElements, i);
            if ((cursorOffset >= startIncludingHidden) && (cursorOffset <= end)) {
                expectedAtCursor.add(expectedElement);
            }
        }
        return (JavaExpectedTerminal[]) expectedAtCursor.toArray(new JavaExpectedTerminal[expectedAtCursor.size()]);
    }

    protected int getEnd(JavaExpectedTerminal[] allExpectedElements, int indexInList) {
        JavaExpectedTerminal elementAtIndex = allExpectedElements[indexInList];
        int startIncludingHidden = elementAtIndex.getStartIncludingHiddenTokens();
        int startExcludingHidden = elementAtIndex.getStartExcludingHiddenTokens();
        for (int i = indexInList + 1; i < allExpectedElements.length; i++) {
            JavaExpectedTerminal elementAtI = allExpectedElements[i];
            int startIncludingHiddenForI = elementAtI.getStartIncludingHiddenTokens();
            int startExcludingHiddenForI = elementAtI.getStartExcludingHiddenTokens();
            if ((startIncludingHidden != startIncludingHiddenForI) || (startExcludingHidden != startExcludingHiddenForI)) {
                return startIncludingHiddenForI - 1;
            }
        }
        return 2147483647;
    }

    protected boolean matches(String proposal, String prefix) {
        if ((proposal == null) || (prefix == null)) {
            return false;
        }
        return ((proposal.toLowerCase().startsWith(prefix.toLowerCase())) || (JavaStringUtil.matchCamelCase(prefix, proposal) != null))
                && (!proposal.equals(prefix));
    }

    protected EObject findCorrectContainer(JavaExpectedTerminal expectedTerminal) {
        EObject container = expectedTerminal.getContainer();
        EClass ruleMetaclass = expectedTerminal.getTerminal().getRuleMetaclass();
        if (ruleMetaclass.isInstance(container)) {
            return container;
        }

        EObject parent = null;
        EObject previousParent = null;
        EObject correctContainer = null;
        EObject hookableParent = null;
        JavaContainmentTrace containmentTrace = expectedTerminal.getContainmentTrace();
        EClass startClass = containmentTrace.getStartClass();
        JavaContainedFeature currentLink = null;
        JavaContainedFeature previousLink = null;
        JavaContainedFeature[] containedFeatures = containmentTrace.getPath();
        for (int i = 0; i < containedFeatures.length; i++) {
            currentLink = containedFeatures[i];
            if (i > 0) {
                previousLink = containedFeatures[(i - 1)];
            }
            EClass containerClass = currentLink.getContainerClass();
            hookableParent = findHookParent(container, startClass, currentLink, parent);
            if (hookableParent != null) {
                break;
            }
            previousParent = parent;
            parent = containerClass.getEPackage().getEFactoryInstance().create(containerClass);
            if (parent != null) {
                if (previousParent == null) {
                    correctContainer = parent;
                } else {
                    JavaContainedFeature link = previousLink;
                    JavaEObjectUtil.setFeature(parent, link.getFeature(), previousParent, false);
                }
            }

        }

        if (correctContainer == null) {
            correctContainer = container;
        }

        if (currentLink == null) {
            return correctContainer;
        }

        hookableParent = findHookParent(container, startClass, currentLink, parent);

        final EObject finalHookableParent = hookableParent;
        final EStructuralFeature finalFeature = currentLink.getFeature();
        final EObject finalParent = parent;
        if ((parent != null) && (hookableParent != null)) {
            expectedTerminal.setAttachmentCode(new Runnable() {
                public void run() {
                    JavaEObjectUtil.setFeature(finalHookableParent, finalFeature, finalParent, false);
                }
            });
        }
        return correctContainer;
    }

    protected EObject findHookParent(EObject container, EClass startClass, JavaContainedFeature currentLink, EObject object) {
        EClass containerClass = currentLink.getContainerClass();
        while (container != null) {
            if ((containerClass.isInstance(object)) && (startClass.equals(container.eClass()))) {
                return container;
            }

            container = container.eContainer();
        }
        return null;
    }
}
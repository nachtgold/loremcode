package javaproposals;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.net.URL;

import org.eclipse.emf.common.util.URI;
import org.emftext.language.java.resource.java.mopp.JavaResource;
import org.emftext.language.java.resource.java.ui.JavaCodeCompletionHelper;
import org.emftext.language.java.resource.java.ui.JavaCompletionProposal;
import org.emftext.language.java.resource.java.util.JavaResourceUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProposalTest {

    private static final String WHITESPACE = " ";
    private static JavaCodeCompletionHelper helper;

    @BeforeClass
    public static void init() {
        helper = new JavaCodeCompletionHelper();
    }

    @Test
    public void testJavaPackageProposal() throws Exception {
        URL url = getClass().getResource("Test.java");
        URI uri = URI.createURI("file://" + url.getFile());

        JavaResource resource = JavaResourceUtil.getResource(uri);
        JavaCompletionProposal[] computeCompletionProposals = helper.computeCompletionProposals(resource, "", 0);
        assertThat(computeCompletionProposals.length, is(1));
        assertThat(computeCompletionProposals[0].getInsertString(), is("package"));
    }

    @Test
    public void testWriteFile() throws Exception {
        File folder = new File(getClass().getResource(".").getFile());
        assertThat(folder.exists(), is(true));

        File codeFile = new File(folder, "tmp.java");
        assertThat(codeFile.createNewFile() || codeFile.canWrite(), is(true));

        URI uri = URI.createURI("file:///" + codeFile.getAbsolutePath());
        JavaResource resource = JavaResourceUtil.getResource(uri);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; ++i) {
            String currentCode = builder.toString();
            JavaCompletionProposal[] proposals = helper.computeCompletionProposals(resource, currentCode, currentCode.length());

            int random = random(proposals.length);
            String insertString = proposals[random].getInsertString();

            builder.append(insertString);
            builder.append(WHITESPACE);
        }
        System.out.println(builder.toString());
    }

    private static int random(int high) {
        return (int) (Math.random() * high);
    }
}

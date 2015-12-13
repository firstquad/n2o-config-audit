package net.n2oapp.framework.config.audit.git.core;

import net.n2oapp.framework.config.audit.git.util.N2oGitConstant;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.util.FileSystemUtil;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;

import static net.n2oapp.framework.config.audit.git.util.N2oGitTestUtil.*;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.CREATED_PREFIX;

/**
 * @author dfirstov
 * @since 15.09.2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/config-audit-git-context.xml")
public class N2oGitCoreTest {
    private static N2oGitTestEnv env;
    private static final String REGEX_LINE_END = "(\\r\\n|\\r|\\n|\\n\\r)";
    private static final String RESOURCE_PATH = N2oGitConstant.RESOURCE_PATH + "core/";

    @Before
    public void init() throws IOException {
        env = iniTestEnv();
    }

    @Test
    public void testCore() throws IOException, GitAPIException, URISyntaxException {
        testGetInstance();
        testAdd();
        testCommit("test/mock.object.xml");
        testAddUpdated();
        testCheckout();
    }

    private void testGetInstance() throws IOException, GitAPIException, URISyntaxException {
        assert N2oGitCore.isInit();
        N2oGitCore gitCore = env.getGitCore();
        assert gitCore.isCurrentBranch(env.getServerBranchName());
        assert gitCore.isClean();
        Iterable<RevCommit> gitIgnoreRev = gitCore.getGit().log().addPath(".gitignore").call();
        assert gitIgnoreRev.iterator().hasNext();
        Iterable<RevCommit> gitConfigRev = gitCore.getGit().log().addPath("/.git/config").call();
        assert !gitConfigRev.iterator().hasNext();
        URL resource = this.getClass().getClassLoader().getResource(RESOURCE_PATH + "template/gitconfig-template");
        assert resource != null;
        String gitConfigTemplate = IOUtils.toString(resource.toURI()).replaceAll(REGEX_LINE_END, "");
        assert gitConfigTemplate.equals(IOUtils.toString(generateStorageFile(".git/config").toURI()).replaceAll(REGEX_LINE_END, ""));
    }

    private void testAdd() throws GitAPIException {
        N2oGitCore gitCore = env.getGitCore();
        assert gitCore.isClean();
        String localPath = "test/mock.object.xml";
        File mock = generateStorageFile(localPath);
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + "mock/mock.object.xml");
        FileSystemUtil.saveContentToFile(inputStream, mock);
        assert mock.exists();
        assert !gitCore.isClean();
        Status status = gitCore.getGit().status().call();
        assert status.getUntracked().size() == 1 && status.getUntracked().toArray()[0].equals(localPath);
        gitCore.add(localPath);
        status = gitCore.getGit().status().call();
        assert status.getAdded().size() == 1 && status.getAdded().toArray()[0].equals(localPath);
    }

    private void testCommit(String mockLocalPath) throws GitAPIException {
        N2oGitCore gitCore = env.getGitCore();
        gitCore.commit(mockLocalPath, "test_author");
        Iterator<RevCommit> commitIterator = gitCore.getGit().log().addPath(mockLocalPath).call().iterator();
        RevCommit commit = commitIterator.next();
        assert commit.getShortMessage().contains(CREATED_PREFIX.value);
        assert !commitIterator.hasNext();
    }

    private String testAddUpdated() throws GitAPIException {
        N2oGitCore gitCore = env.getGitCore();
        assert gitCore.isClean();
        String mockLocalPath = "test/mock1.object.xml";
        File mock = generateStorageFile(mockLocalPath);
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + "mock/mock.object.xml");
        FileSystemUtil.saveContentToFile(inputStream, mock);
        assert mock.exists();
        assert !gitCore.isClean();
        Status status = gitCore.getGit().status().call();
        assert status.getUntracked().size() == 1 && status.getUntracked().toArray()[0].equals(mockLocalPath);
        gitCore.addUpdated(mockLocalPath);
        status = gitCore.getGit().status().call();
        assert status.getUntracked().size() == 1 && status.getUntracked().toArray()[0].equals(mockLocalPath);
        assert mock.delete();
        return mockLocalPath;
    }

    private void testCheckout() throws IOException, GitAPIException {
        String serverBranchName = env.getServerBranchName();
        N2oGitCore gitCore = env.getGitCore();
        assert gitCore.isCurrentBranch(serverBranchName);
        String systemBranchName = env.getSystemBranchName();
        gitCore.doCheckout(systemBranchName);
        assert gitCore.isCurrentBranch(systemBranchName);
        String testBranch = "test_branch";
        gitCore.doCheckout(testBranch, true);
        assert gitCore.isCurrentBranch(testBranch);
    }

    @After
    public void after() {
        clearEnv(env);
    }

}

package net.n2oapp.framework.config.audit.git.service;

import net.n2oapp.framework.api.N2oConfigStarterEvent;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.audit.model.N2oConfigHistory;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static net.n2oapp.framework.config.audit.git.util.N2oGitTestUtil.*;
import static net.n2oapp.framework.config.audit.git.util.N2oGitUtil.resolveMergeMessage;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.*;

/**
 * @author dfirstov
 * @since 25.09.2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/config-audit-git-context.xml")
public class N2oConfigAuditGitTest {
    private static N2oGitTestEnv env;

    @Before
    public void init() throws IOException {
        env = iniTestEnv();
    }

    @Test
    public void test() throws IOException, GitAPIException {
        testCRUD();
        testRetrieveHistory();
    }

    private void testCRUD() throws GitAPIException, IOException {
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        N2oGitCore gitCore = env.getGitCore();
        Iterator<RevCommit> iterator = gitCore.getGit().log().call().iterator();
        assert iterator.hasNext();
        assert (INIT_COMMIT_PREFIX.value).equals(iterator.next().getShortMessage());
        //добавил системный файл
        String localPath = "page/page1.page.xml";
        Info info = addToConfReg(localPath, false);
        assertSystemInfo(info);
        //рестарт, проверка мержа
        configAuditGit.handle(new N2oConfigStarterEvent());
        iterator = gitCore.getGit().log().call().iterator();
        assert iterator.hasNext();
        assert resolveMergeMessage(env.getSystemBranchName(), env.getServerBranchName()).equals(iterator.next().getShortMessage());
        //изменил системный файл, проверка статуса "измененный"
        N2oGitFileUtil.createFile(resolveURI("page/page2.page.xml"), generateStorageFile(localPath));
        info = addToConfReg(localPath, true, false);
        assertModifyInfo(info);
        iterator = gitCore.getGit().log().call().iterator();
        assert iterator.hasNext();
        assert (UPDATED_PREFIX.value + localPath).equals(iterator.next().getShortMessage());
        assert info.getFile().delete();
        env.getConfReg().remove(info.getConfigId());
        iterator = gitCore.getGit().log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        assert (RESTORED_PREFIX.value + localPath).equals(iterator.next().getShortMessage());
        //создал серверный файл
        localPath = "page/page3.page.xml";
        info = addToConfReg(localPath, true);
        assertServerInfo(info);
        iterator = gitCore.getGit().log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        assert (CREATED_PREFIX.value + localPath).equals(iterator.next().getShortMessage());
        //удалил серверный
        assert info.getFile().delete();
        env.getConfReg().remove(info.getConfigId());
        iterator = gitCore.getGit().log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        assert (DELETED_PREFIX.value + localPath).equals(iterator.next().getShortMessage());
    }

    private void testRetrieveHistory() {
        String localPath = "page/page1.page.xml";
        List<N2oConfigHistory> n2oConfigHistories = env.getConfigAuditGit().retrieveHistory(localPath);
        N2oConfigHistory history = n2oConfigHistories.get(0);
        assert (RESTORED_PREFIX + localPath).equals(history.getMessage());
        assert compareContent(localPath, history.getContent());
        assert compareContent("page/page2.page.xml", history.getPreviousContent());
        history = n2oConfigHistories.get(1);
        assert (UPDATED_PREFIX.value + localPath).equals(history.getMessage());
        assert compareContent("page/page2.page.xml", history.getContent());
        assert compareContent(localPath, history.getPreviousContent());
        history = n2oConfigHistories.get(2);
        assert (N2O_VERSION_PREFIX + env.getTestProperties().getProperty("n2o.version")).equals(history.getMessage());
        assert compareContent(localPath, history.getContent());
        assert "".equals(history.getPreviousContent());
        localPath = "page/page3.page.xml";
        n2oConfigHistories = env.getConfigAuditGit().retrieveHistory(localPath);
        history = n2oConfigHistories.get(0);
        assert (DELETED_PREFIX + localPath).equals(history.getMessage());
        assert "".equals(history.getContent());
        history = n2oConfigHistories.get(1);
        assert (CREATED_PREFIX + localPath).equals(history.getMessage());
        assert "".equals(history.getPreviousContent());
    }

    @After
    public void after() {
        clearEnv(env);
    }
}

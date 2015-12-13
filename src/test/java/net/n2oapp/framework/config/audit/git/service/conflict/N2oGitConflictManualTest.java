package net.n2oapp.framework.config.audit.git.service.conflict;

import net.n2oapp.framework.api.N2oConfigStarterEvent;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.audit.model.N2oConfigConflict;
import net.n2oapp.framework.config.register.audit.model.N2oConfigMessage;
import org.eclipse.jgit.api.Git;
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
import java.util.Iterator;

import static net.n2oapp.framework.config.audit.git.util.N2oGitTestUtil.*;

/**
 * Тест режимов решения конфликтов при мерже.
 *
 * @author dfirstov
 * @since 22.09.2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/config-audit-git-context.xml")
public class N2oGitConflictManualTest {
    private static N2oGitTestEnv env;

    @Before
    public void init() throws IOException {
        env = iniTestEnv();
    }

    @Test
    public void test() throws IOException, GitAPIException {
        env.getTestProperties().setProperty("n2o.config.audit.conflict.mode", "manual");
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        String serverBranchName = env.getServerBranchName();
        N2oGitCore gitCore = env.getGitCore();
        Git git = gitCore.getGit();
        //создается системный файл
        String localPath = "page/page1.page.xml";
        Info info = addToConfReg(localPath, false);
        assertSystemInfo(info);
        assert gitCore.isCurrentBranch(serverBranchName);
        //рестарт
        configAuditGit.handle(new N2oConfigStarterEvent());
        Iterator<RevCommit> iterator = git.log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        assert git.status().call().getMissing().size() == 1;
        //переопределяем файл, делаем статус "измененный"
        File file = addAncestor(localPath, info);
        N2oGitFileUtil.createFile(resolveURI("page/page2.page.xml"), file);
        configAuditGit.getN2oGit().commit(info.getLocalPath(), "test", InfoStatus.Status.MODIFY);
        assert InfoStatus.Status.MODIFY.equals(InfoStatus.calculateStatus(info, true));
        //изменяем системный файл(jar), создаем конфликт
        modifyAncestor(info, "page/page3.page.xml");
        //рестарт
        configAuditGit.stopAudit();
        configAuditGit.handle(new N2oConfigStarterEvent());
        iterator = git.log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        assert (N2oConfigMessage.CONFLICT_MERGE_PREFIX.value + "1").equals(iterator.next().getShortMessage());
        assert gitCore.isCurrentBranch(serverBranchName);
        assert isIdenticalContent("page/pageConflict.page.xml", localPath);
        N2oConfigConflict configConflict = configAuditGit.retrieveConflict(localPath);
        assert compareContent("page/page1.page.xml", configConflict.getParentContent());
        assert compareContent("page/page2.page.xml", configConflict.getContent());
        assert compareContent("page/page3.page.xml", configConflict.getMergeContent());
        assert compareContent("page/pageConflict.page.xml", configConflict.getConflictContent());
    }

    @After
    public void after() {
        clearEnv(env);
    }
}

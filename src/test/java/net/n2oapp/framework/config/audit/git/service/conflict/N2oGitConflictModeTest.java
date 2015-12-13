package net.n2oapp.framework.config.audit.git.service.conflict;

import net.n2oapp.framework.api.N2oConfigStarterEvent;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.register.ConfigRegister;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.InfoStatus;
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
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMergeMode.OURS;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMergeMode.THEIRS;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.RESOLVED_AUTO_PREFIX;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.TEMPLATE_PREFIX;

/**
 * Тест режимов решения конфликтов при мерже.
 *
 * @author dfirstov
 * @since 22.09.2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/config-audit-git-context.xml")
public class N2oGitConflictModeTest {
    private static N2oGitTestEnv env;

    @Before
    public void init() throws IOException {
        env = iniTestEnv();
    }

    @Test
    public void test() throws IOException, GitAPIException {
        testConflictMode("ours");
        testConflictMode("theirs");
    }

    private void testConflictMode(String mode) throws IOException, GitAPIException {
        env.getTestProperties().setProperty("n2o.config.audit.conflict.mode", mode);
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        ConfigRegister confReg = env.getConfReg();
        String serverBranchName = env.getServerBranchName();
        N2oGitCore gitCore = env.getGitCore();
        Git git = gitCore.getGit();
        //создается системный файл
        String localPath = "";
        Info info = null;
        if ("ours".equals(mode)) {
            localPath = "page/page1.page.xml";
            info = addToConfReg(localPath, false);
        } else if ("theirs".equals(mode)) {
            localPath = "page/page2.page.xml";
            info = addToConfReg(localPath, false);
        }
        assertSystemInfo(info);
        assert gitCore.isCurrentBranch(serverBranchName);
        //рестарт
        configAuditGit.stopAudit();
        configAuditGit.handle(new N2oConfigStarterEvent());
        Iterator<RevCommit> iterator = git.log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        if ("ours".equals(mode)) {
            assert git.status().call().getMissing().size() == 1;
        } else if ("theirs".equals(mode)) {
            assert git.status().call().getMissing().size() == 2;
        }
        //переопределяем файл, делаем статус "измененный"
        File file = addAncestor(localPath, info);
        if ("ours".equals(mode))
            N2oGitFileUtil.createFile(resolveURI("page/page2.page.xml"), file);
        else if ("theirs".equals(mode))
            N2oGitFileUtil.createFile(resolveURI("page/page1.page.xml"), file);
        configAuditGit.getN2oGit().commit(info.getLocalPath(), "test", InfoStatus.Status.MODIFY);
        assert InfoStatus.Status.MODIFY.equals(InfoStatus.calculateStatus(info, true));
        //изменяем системный файл(jar), создаем конфликт
        modifyAncestor(info, "page/page3.page.xml");
        //рестарт
        configAuditGit.stopAudit();
        configAuditGit.handle(new N2oConfigStarterEvent());
        iterator = git.log().addPath(localPath).call().iterator();
        assert iterator.hasNext();
        String modePrefix = ("ours".equals(mode) ? OURS : THEIRS).getValue().toUpperCase();
        assert (String.format(TEMPLATE_PREFIX.value, RESOLVED_AUTO_PREFIX.value + modePrefix)).equals(iterator.next().getShortMessage());
        String resultContent = "ours".equals(mode) ? "page/page2.page.xml" : "page/page3.page.xml";
        assert isIdenticalContent(resultContent, localPath);
        assert gitCore.isCurrentBranch(serverBranchName);
        confReg.clearRegister();
    }

    @After
    public void after() {
        clearEnv(env);
    }
}

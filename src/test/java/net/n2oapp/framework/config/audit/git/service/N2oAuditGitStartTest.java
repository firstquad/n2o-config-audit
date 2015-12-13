package net.n2oapp.framework.config.audit.git.service;

import net.n2oapp.framework.api.N2oConfigStarterEvent;
import net.n2oapp.framework.api.metadata.global.view.N2oPage;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.register.ConfigRegister;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.audit.model.N2oConfigMessage;
import org.eclipse.jgit.api.Git;
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
import java.util.Iterator;

import static net.n2oapp.framework.config.audit.git.util.N2oGitTestUtil.*;

/**
 * Тест старта аудита конфигураций
 *
 * @author dfirstov
 * @since 18.09.2015
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/config-audit-git-context.xml")
public class N2oAuditGitStartTest {
    private static N2oGitTestEnv env;

    @Before
    public void init() throws IOException {
        env = iniTestEnv();
    }

    @Test
    public void testHandle() throws IOException, GitAPIException {
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        //настройки по умолчанию
        testHandle("modify");
        configAuditGit.stopAudit();
        //режим отоборажения всех метаданных (n2o.config.audit.storage.mode=all)
        testHandle("all");
        configAuditGit.stopAudit();
        //тест отключения режима "all" и возвращения в исходное состояние
        testHandle("modify");
    }

    public void testHandle(String storageMode) throws IOException, GitAPIException {
        env.getTestProperties().setProperty("n2o.config.audit.storage.mode", storageMode);
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        ConfigRegister confReg = env.getConfReg();
        String serverBranchName = env.getServerBranchName();
        N2oGitCore gitCore = env.getGitCore();
        //тест системных файлов
        String localPath1 = "page/page1.page.xml";
        Info info1 = addToConfReg(localPath1, false);
        assert InfoStatus.Status.SYSTEM.equals(InfoStatus.calculateStatus(info1, true));
        String localPath2 = "page/page2.page.xml";
        Info info2 = addToConfReg(localPath2, false);
        assertSystemInfo(info2);
        assert gitCore.isCurrentBranch(serverBranchName);
        configAuditGit.handle(new N2oConfigStarterEvent());
        Git git = gitCore.getGit();
        Iterator<RevCommit> iterator1 = git.log().addPath(localPath1).call().iterator();
        assert iterator1.hasNext();
        Iterator<RevCommit> iterator2 = git.log().addPath(localPath2).call().iterator();
        assert iterator2.hasNext();
        Status status = git.status().call();
        if ("modify".equals(storageMode)) {
            assert status.getMissing().size() == 2;
        } else if ("all".equals(storageMode)) {
            assert status.getMissing().size() == 0;
        } else {
            throw new RuntimeException("Error key n2o.config.audit.storage.mode");
        }
        assert gitCore.isCurrentBranch(serverBranchName);
        //тест измененного файла
        File file2 = addAncestor(localPath2, info2);
        N2oGitFileUtil.createFile(resolveURI(localPath1), file2);
        configAuditGit.getN2oGit().commit(info2.getLocalPath(), "test", InfoStatus.Status.MODIFY);
        configAuditGit.stopAudit();
        configAuditGit.handle(new N2oConfigStarterEvent());
        status = git.status().call();
        if ("modify".equals(storageMode))
            assert status.getMissing().size() == 1;
        else if ("all".equals(storageMode))
            assert status.getMissing().size() == 0;
        else
            throw new RuntimeException("Error key n2o.config.audit.storage.mode");
        iterator2 = git.log().addPath(localPath2).call().iterator();
        assert iterator2.hasNext();
        assert (N2oConfigMessage.UPDATED_PREFIX.value + localPath2).equals(iterator2.next().getShortMessage());
        assert gitCore.isCurrentBranch(serverBranchName);
        //тест серверного файла
        String localPath3 = "page/page3.page.xml";
        if (!confReg.contains("page3", N2oPage.class)) {
            addToConfReg(localPath3, true);
            File file3 = generateStorageFile(localPath3);
            N2oGitFileUtil.createFile(resolveURI(localPath3), file3);
        }
        Info info3 = confReg.get("page3", N2oPage.class);
        assert InfoStatus.Status.SERVER.equals(InfoStatus.calculateStatus(info3, true));
        configAuditGit.getN2oGit().commit(info3.getLocalPath(), "test", InfoStatus.Status.SERVER);
        configAuditGit.stopAudit();
        configAuditGit.handle(new N2oConfigStarterEvent());
        status = git.status().call();
        if ("modify".equals(storageMode))
            assert status.getMissing().size() == 1;
        else if ("all".equals(storageMode))
            assert status.getMissing().size() == 0;
        else
            throw new RuntimeException("Error key n2o.config.audit.storage.mode");
        Iterator<RevCommit> iterator3 = git.log().addPath(localPath3).call().iterator();
        assert iterator3.hasNext();
        assert (N2oConfigMessage.CREATED_PREFIX.value + localPath3).equals(iterator3.next().getShortMessage());
        assert InfoStatus.Status.SERVER.equals(InfoStatus.calculateStatus(info3, true));
        assert gitCore.isCurrentBranch(serverBranchName);
    }

    @After
    public void after() {
        clearEnv(env);
    }
}

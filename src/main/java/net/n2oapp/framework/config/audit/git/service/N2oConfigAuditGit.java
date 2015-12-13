package net.n2oapp.framework.config.audit.git.service;

import net.n2oapp.context.StaticSpringContext;
import net.n2oapp.framework.api.N2oConfigStarterEvent;
import net.n2oapp.framework.api.UsersUtil;
import net.n2oapp.framework.api.event.N2oEventListener;
import net.n2oapp.framework.config.ConfigStarter;
import net.n2oapp.framework.config.register.ConfigRegister;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.audit.N2oConfigAudit;
import net.n2oapp.framework.config.register.audit.model.N2oConfigConflict;
import net.n2oapp.framework.config.register.audit.model.N2oConfigHistory;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Author.SYSTEM_AUTHOR_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SERVER_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SYSTEM_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.StorageMode.MODIFY;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.appVersion;
import static net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil.*;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.N2O_VERSION_PREFIX;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.SERVER_PREFIX;

/**
 * @author dfirstov
 * @since 02.09.2015
 */
public class N2oConfigAuditGit implements N2oConfigAudit, N2oEventListener<N2oConfigStarterEvent> {
    private static Logger logger = LoggerFactory.getLogger(N2oConfigAuditGit.class);
    protected ConfigRegister confReg = ConfigRegister.getInstance();
    private static boolean isStarted;
    private N2oGit n2oGit;
    private Properties properties = (Properties) StaticSpringContext.getBean("n2oProperties");

    @Override
    public synchronized void handle(N2oConfigStarterEvent event) {
        if (!isStarted && n2oGit.isInit()) {
            startAudit();
            isStarted = true;
        }
    }

    @Override
    public synchronized Info add(Info info) {
        if (!isStarted)
            return null;
        return auditAdd(info);
    }

    @Override
    public synchronized Info remove(Info info) {
        if (!isStarted)
            return null;
        return auditRemove(info);
    }

    @Override
    public List<N2oConfigHistory> retrieveHistory(String localPath) {
        if (localPath == null || !isStarted)
            return null;
        return n2oGit.auditHistory(localPath);

    }

    @Override
    public N2oConfigConflict retrieveConflict(String localPath) {
        if (!isStarted)
            return null;
        return n2oGit.retrieveConflictFromLog(localPath);
    }

    @Override
    public String retrieveGraph() {
        if (!isStarted)
            return null;
        return n2oGit.retrieveGraph();
    }

    @Override
    public Class<N2oConfigStarterEvent> getType() {
        return N2oConfigStarterEvent.class;
    }

    private void startAudit() {
        Set<File> modifyFiles = retrieveModifyFiles();
        try {
            n2oGit.doCheckout(SERVER_BRANCH_NAME);
            n2oGit.commitAll(SERVER_PREFIX.value, getAuthor());
            processBranchSystem();
            processBranchServer();
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException("Start config audit error.", e);
        } finally {
            if (MODIFY.equals(properties.getProperty("n2o.config.audit.storage.mode")))
                cleanStorage(modifyFiles);
        }
    }

    private Set<File> retrieveModifyFiles() {
        Set<File> modifyFiles = new HashSet<>();
        confReg.getAllInfoList().forEach(info -> {
            if (info.getFile() != null
                    && ((info.getAncestor() != null && !InfoStatus.isIdenticalAncestor(info)) || info.getAncestor() == null))
                modifyFiles.add(info.getFile());
        });
        return modifyFiles;
    }

    private void cleanStorage(Set<File> modifyFiles) {
        try {
            deleteAllFiles(modifyFiles);
        } catch (IOException e) {
            throw new RuntimeException("Config audit clean storage error.", e);
        }
        reRegistering();
    }

    public void reRegistering() {
        ConfigStarter.doRegisterInfo();
    }

    private void processBranchSystem() throws GitAPIException, IOException {
        n2oGit.doCheckout(SYSTEM_BRANCH_NAME);
        for (Info info : confReg.getAllInfoList()) {
            createInfoFile(info);
        }
        n2oGit.commitAll(N2O_VERSION_PREFIX.value + appVersion, getAuthor());
    }

    private void processBranchServer() throws GitAPIException, IOException {
        n2oGit.doCheckout(SERVER_BRANCH_NAME);
        n2oGit.doMerge(SYSTEM_BRANCH_NAME, properties.getProperty("n2o.config.audit.conflict.mode"));
    }

    private String getAuthor() {
        try {
            return UsersUtil.getUser().getUsername();
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
            return SYSTEM_AUTHOR_NAME;
        }
    }

    private Info auditAdd(Info info) {
        if (isStarted && info.getFile() != null) {
            n2oGit.commit(info.getLocalPath(), getAuthor(), InfoStatus.calculateStatus(info, true));
        }
        return info;
    }

    private Info auditRemove(Info info) {
        File file = info.getFile();
        if (!isStarted || file == null)
            return info;
        boolean hasAncestor = info.getAncestor() != null;
        InfoStatus.Status status = InfoStatus.Status.SERVER;
        if (hasAncestor) {
            info.setInternalModified(true);
            file = createFile(info.getAncestor());
            status = InfoStatus.calculateStatus(info, true);
        }
        n2oGit.commitRemoved(info.getLocalPath(), getAuthor(), status);
        if (info.getAncestor() != null && file != null) {
            try {
                info.setInternalModified(true);
                deleteFile(file);
            } catch (IOException e) {
                throw new RuntimeException("Config audit remove error.", e);
            }
        }
        return info;
    }

    public void setN2oGit(N2oGit n2oGit) {
        this.n2oGit = n2oGit;
    }

    public N2oGit getN2oGit() {
        return n2oGit;
    }

    public void stopAudit() {
        isStarted = false;
    }
}

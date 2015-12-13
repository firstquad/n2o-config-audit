package net.n2oapp.framework.config.audit.git.util.model;

import net.n2oapp.context.StaticSpringContext;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.mock.N2oGitTestProperties;
import net.n2oapp.framework.config.register.ConfigRegister;

/**
 * @author dfirstov
 * @since 25.09.2015
 */
public class N2oGitTestEnv {
    private String serverBranchName;
    private String systemBranchName;
    private String configPath;
    private N2oGitCore gitCore;
    private N2oConfigAuditGitMock configAuditGit;
    private ConfigRegister confReg;
    private N2oGitTestProperties testProperties;

    public N2oGitTestEnv(N2oGitCore gitCore, N2oConfigAuditGitMock configAuditGit, ConfigRegister confReg) {
        this.gitCore = gitCore;
        this.configAuditGit = configAuditGit;
        this.confReg = confReg;
        testProperties = (N2oGitTestProperties) StaticSpringContext.getBean("n2oGitProperties");
        serverBranchName = testProperties.getProperty("n2o.config.audit.server.branch.name");
        systemBranchName = testProperties.getProperty("n2o.config.audit.system.branch.name");
        configPath = testProperties.getProperty("n2o.config.audit.system.branch.name");
    }

    public String getServerBranchName() {
        return serverBranchName;
    }

    public void setServerBranchName(String serverBranchName) {
        this.serverBranchName = serverBranchName;
    }

    public N2oGitCore getGitCore() {
        return gitCore;
    }

    public void setGitCore(N2oGitCore gitCore) {
        this.gitCore = gitCore;
    }

    public N2oConfigAuditGitMock getConfigAuditGit() {
        return configAuditGit;
    }

    public void setConfigAuditGit(N2oConfigAuditGitMock configAuditGit) {
        this.configAuditGit = configAuditGit;
    }

    public ConfigRegister getConfReg() {
        return confReg;
    }

    public void setConfReg(ConfigRegister confReg) {
        this.confReg = confReg;
    }

    public N2oGitTestProperties getTestProperties() {
        return testProperties;
    }

    public void setTestProperties(N2oGitTestProperties testProperties) {
        this.testProperties = testProperties;
    }

    public String getSystemBranchName() {
        return systemBranchName;
    }

    public void setSystemBranchName(String systemBranchName) {
        this.systemBranchName = systemBranchName;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }
}

package net.n2oapp.framework.config.audit.git.util.mock;

import net.n2oapp.framework.config.audit.git.service.N2oConfigAuditGit;
import net.n2oapp.framework.config.register.ConfigRegister;

/**
 * @author dfirstov
 * @since 18.09.2015
 */
public class N2oConfigAuditGitMock extends N2oConfigAuditGit {

    @Override
    public void reRegistering() {
    }

    public void setConfReg(ConfigRegister confReg) {
        this.confReg = confReg;
    }
}

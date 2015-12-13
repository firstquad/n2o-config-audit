package net.n2oapp.framework.config.audit.git.util;

import net.n2oapp.context.StaticSpringContext;

import java.util.Properties;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SERVER_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SYSTEM_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.StorageMode.currentMode;

/**
 * @author dfirstov
 * @since 10.09.2015
 */
public class N2oGitConstant {
    public static final String DEFAULT_FILE_ENCODING = "UTF-8";
    public static final String DEFAULT_LINE_END = "\n";
    public static final String RESOURCE_PATH = "net/n2oapp/framework/config/audit/git/";
    public static String appVersion;
    public static String currentConflictMode;
    public static boolean auditEnabled;
    public static String repositoryPath;

    public static void initConstant() {
        Properties properties =(Properties) StaticSpringContext.getBean("n2oProperties");
        auditEnabled = Boolean.valueOf(properties.getProperty("n2o.config.audit.enabled"));
        repositoryPath = properties.getProperty("n2o.config.path");
        SERVER_BRANCH_NAME = properties.getProperty("n2o.config.audit.server.branch.name");
        SYSTEM_BRANCH_NAME = properties.getProperty("n2o.config.audit.system.branch.name");
        currentMode = properties.getProperty("n2o.config.audit.storage.mode");
        currentConflictMode = properties.getProperty("n2o.config.audit.conflict.mode");
        appVersion = properties.getProperty(properties.getProperty("n2o.config.audit.app.version"));
    }

    public static final class StorageMode {
        public static final String MODIFY = "modify";
        public static final String ALL = "all";
        public static String currentMode;
    }

    public static final class Branch {
        public static String SERVER_BRANCH_NAME;
        public static String SYSTEM_BRANCH_NAME;
    }

    public static final class Author {
        public static final String SYSTEM_AUTHOR_NAME = "system";
        public static final String AUTHOR_EMAIL = "i-novus.ru";
    }
}

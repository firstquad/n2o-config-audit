package net.n2oapp.framework.config.audit.git.util.mock;


import net.n2oapp.context.StaticSpringContext;
import net.n2oapp.framework.config.audit.git.util.N2oGitTestUtil;
import net.n2oapp.properties.web.MockN2oProperties;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Properties;

/**
 * @author dfirstov
 * @since 22.09.2015
 */
public class N2oGitTestProperties extends MockN2oProperties {
    private Properties properties = new Properties();

    public TemporaryFolder tempFolder = new TemporaryFolder();

    public N2oGitTestProperties() throws IOException {
        properties = (MockN2oProperties) StaticSpringContext.getBean("n2oProperties");
        properties.setProperty("n2o.config.audit.storage.mode", "modify");
        properties.setProperty("n2o.config.audit.conflict.mode", "manual");
        properties.setProperty("n2o.config.audit.enabled", "true");
        properties.setProperty("n2o.config.ignores", ".git,.n2o_git_stash");
        properties.setProperty("n2o.config.audit.system.branch.name", "system");
        properties.setProperty("n2o.config.audit.server.branch.name", "server");
        properties.setProperty("n2o.config.class.packages", "net.n2oapp.framework");
        properties.setProperty("n2o.config.audit.app.version", "n2o.version");
        properties.setProperty("n2o.version", "test_version");
        properties.setProperty("n2o.config.path", initRepoDir());
    }

    public String initRepoDir() throws IOException {
        String rootDir = "/test_conf/";
        tempFolder.create();
        tempFolder.newFolder(rootDir);
        return N2oGitTestUtil.replaceSlash(tempFolder.getRoot().getAbsolutePath()) + rootDir;
    }

    @Override
    public Object setProperty(String key, String value) {
        return properties.setProperty(key, value);
    }

    @Override
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void deleteRepo() {
        tempFolder.delete();
    }
}

package net.n2oapp.framework.config.audit.git.util;

import net.n2oapp.context.StaticSpringContext;
import net.n2oapp.framework.api.metadata.global.view.N2oPage;
import net.n2oapp.framework.api.metadata.local.Compilable;
import net.n2oapp.framework.api.metadata.local.view.page.CompiledPage;
import net.n2oapp.framework.api.util.ToListConsumer;
import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.mock.ConfigRegisterMock;
import net.n2oapp.framework.config.audit.git.util.mock.N2oConfigAuditGitMock;
import net.n2oapp.framework.config.audit.git.util.model.N2oGitTestEnv;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.RegisterUtil;
import net.n2oapp.framework.config.register.meta.BaseMetaModelRegister;
import net.n2oapp.framework.config.register.storage.Node;
import net.n2oapp.framework.config.util.FileSystemUtil;
import org.springframework.core.io.UrlResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dfirstov
 * @since 21.09.2015
 */
public class N2oGitTestUtil {
    private static String configPath;
    private static ConfigRegisterMock confReg;

    public static N2oGitTestEnv iniTestEnv() {
        initContext();
        ConfigRegisterMock confReg = initConfigRegister();
        N2oConfigAuditGitMock configAuditGit = (N2oConfigAuditGitMock) StaticSpringContext.getBean("n2oConfigAuditGitMock");
        configAuditGit.setConfReg(confReg);
        N2oGitTestEnv env = new N2oGitTestEnv(N2oGitCore.getInstance(), configAuditGit, confReg);
        configPath = env.getTestProperties().getProperty("n2o.config.path");
        return env;
    }

    public static void modifyAncestor(Info info, String localPath) {
        Info infoAncestor = copyInfo(info);
        infoAncestor.setFile(null);
        infoAncestor.setURI(resolveURI(localPath));
        info.setAncestor(infoAncestor);
    }

    public static File addAncestor(String localPath, Info info) {
        info.setURI(addUriPrefix(configPath) + localPath);
        File file = new File(configPath + localPath);
        info.setFile(file);
        Info infoAncestor = copyInfo(info);
        infoAncestor.setFile(null);
        infoAncestor.setURI(resolveURI(localPath));
        info.setAncestor(infoAncestor);
        return file;
    }

    public static void assertSystemInfo(Info info) throws IOException {
        assert info != null;
        assert InfoStatus.Status.SYSTEM.equals(InfoStatus.calculateStatus(info, true));
    }

    public static void assertModifyInfo(Info info) throws IOException {
        assert info != null;
        assert InfoStatus.Status.MODIFY.equals(InfoStatus.calculateStatus(info, true));
    }

    public static void assertServerInfo(Info info) throws IOException {
        assert info != null;
        assert InfoStatus.Status.SERVER.equals(InfoStatus.calculateStatus(info, true));
    }

    public static void initContext() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("test"));
        User user = new User("admin", "password", authorities);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static ConfigRegisterMock initConfigRegister() {
        List<Class<? extends Compilable>> classes = new ArrayList<>();
        classes.add(CompiledPage.class);
        BaseMetaModelRegister.getInstance().refresh(classes);
        confReg = new ConfigRegisterMock(new ToListConsumer<>());
        return confReg;
    }

    public static File generateStorageFile(String localPath) {
        return new File(configPath + localPath);
    }

    public static Info addToConfReg(String localPath, boolean isFile) throws IOException {
        return addToConfReg(localPath, isFile, true);
    }

    public static Info addToConfReg(String localPath, boolean isFile, boolean createFile) throws IOException {
        URL url = new URL(resolveURI(localPath));
        Info info = RegisterUtil.createXmlInfo(Node.byLocationPattern(new UrlResource(url), "classpath*:net/n2oapp/framework/config/audit/git/service/test_conf/**/*.xml"));
        if (!isFile)
            info.setFile(null);
        else {
            if (createFile) {
                N2oGitFileUtil.createFile(info);
            }
            info.setURI(addUriPrefix(configPath) + localPath);
            info.setFile(new File(configPath + localPath));
        }
        confReg.add(info);
        return confReg.get(info.getId(), N2oPage.class);
    }

    public static String addUriPrefix(String path) {
        return (path.startsWith("/") ? "file:" : "file:/") + path;
    }

    public static String resolveURI(String localPath) {
        URL resource = N2oGitTestUtil.class.getClassLoader().getResource("net/n2oapp/framework/config/audit/git/service/test_conf/" + localPath);
        assert resource != null;
        return resource.toString();
    }

    public static boolean isIdenticalContent(String resourceLocalPath, String fileLocalPath) {
        String c1 = FileSystemUtil.getContentByUri(resolveURI(resourceLocalPath));
        String c2 = FileSystemUtil.getContentByUri(new File(configPath + fileLocalPath).toURI().toString());
        return normalizeContent(c1).equals(normalizeContent(c2));
    }

    public static void clearEnv(N2oGitTestEnv env) {
        N2oConfigAuditGitMock configAuditGit = env.getConfigAuditGit();
        System.out.println(configAuditGit.retrieveGraph());
        env.getGitCore().closeRepo();
        env.getTestProperties().deleteRepo();
        configAuditGit.stopAudit();
    }


    public static boolean compareContent(String localPath, String content) {
        String contentByUri = FileSystemUtil.getContentByUri(resolveURI(localPath));
        return normalizeContent(contentByUri).equals(normalizeContent(content));
    }


    public static Info copyInfo(Info info) {
        Info infoCopy = new Info(info.getId(), info.getBaseMetaModel());
        infoCopy.setFile(info.getFile());
        infoCopy.setContext(info.getContext());
        infoCopy.setDependencies(info.getDependencies());
        infoCopy.setInternalModified(info.isInternalModified());
        infoCopy.setLocalPath(info.getLocalPath());
        infoCopy.setReaderClass(info.getReaderClass());
        infoCopy.setOrigin(info.getOrigin());
        infoCopy.setName(info.getName());
        infoCopy.setURI(info.getURI());
        infoCopy.setAncestor(info.getAncestor());
        return infoCopy;
    }

    public static String normalizeContent(String c1) {
        return c1.replaceAll("\\r\\n|\\r|\\n|\\n\\r|\\t", "");
    }

    public static String getConfigPath() {
        return configPath;
    }

    public static void setConfigPath(String configPath) {
        N2oGitTestUtil.configPath = configPath;
    }

    public static String replaceSlash(String path) {
        return path.replace("\\", "/");
    }
}

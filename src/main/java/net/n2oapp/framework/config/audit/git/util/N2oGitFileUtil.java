package net.n2oapp.framework.config.audit.git.util;

import net.n2oapp.context.StaticSpringContext;
import net.n2oapp.framework.config.register.Info;
import net.n2oapp.framework.config.util.FileSystemUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.DEFAULT_FILE_ENCODING;

/**
 * @author dfirstov
 * @since 04.08.2015
 */
public class N2oGitFileUtil {
    private static Properties properties = (Properties) StaticSpringContext.getBean("n2oProperties");
    private static Logger logger = LoggerFactory.getLogger(N2oGitFileUtil.class);

    public static File createInfoFile(Info info) {
        if (info.getAncestor() != null)
            return createFile(info.getAncestor());
        else if (info.getFile() == null) {
            return createFile(info);
        }
        return null;
    }

    public static File createFile(Info info) {
        if (info == null || info.getURI() == null)
            return null;
        String uri = info.getURI();
        File file = new File(getConfigPath() + info.getLocalPath());
        return createFile(uri, file);
    }

    public static File createFile(String uri, File file) {
        try (InputStream inputStream = FileSystemUtil.getContentAsStream(uri)) {
            String content = IOUtils.toString(inputStream, DEFAULT_FILE_ENCODING);
            FileUtils.writeStringToFile(file, content, DEFAULT_FILE_ENCODING);
            return file;
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
            return null;
        }
    }

    public static void deleteFile(File file) throws IOException {
        FileUtils.forceDelete(file);
    }

    public static void deleteAllFiles(Set<File> excludingFiles) throws IOException {
        File configPathDir = new File(properties.getProperty("n2o.config.path"));
        String ignoresString = properties.getProperty("n2o.config.ignores");
        List<String> ignores = new ArrayList<>();
        if (ignoresString.contains(","))
            ignores.addAll(Arrays.asList(ignoresString.split(",")));
        else
            ignores.add(ignoresString);
        List<Path> stashingFiles = Files.walk(configPathDir.toPath())
                .filter(path -> !ignores.stream().anyMatch(path.toString()::contains) && !path.equals(configPathDir.toPath()))
                .collect(Collectors.toList());
        if (stashingFiles == null || stashingFiles.size() == 0) {
            return;
        }
        for (Path path : new ArrayList<>(stashingFiles)) {
            if (excludingFiles.stream().anyMatch(file -> file.toPath().toString().equals(path.toString())))
                continue;
            File file = path.toFile();
            if (file.exists() && (file.isFile() || isEmptyDir(file))) {
                FileUtils.forceDelete(file);
                stashingFiles.remove(path);
            }
        }
        deleteEmptyDirs(stashingFiles);
    }

    private static boolean isEmptyDir(File file) {
        return file.isDirectory() && (file.listFiles() == null || file.listFiles().length == 0);
    }

    private static void deleteEmptyDirs(List<Path> dirs) {
        for (Path path : dirs) {
            File file = path.toFile();
            if (file.isFile() || !file.exists())
                continue;
            List<File> emptyDirs = new ArrayList<>();
            do {
                emptyDirs.forEach(File::delete);
                emptyDirs = findEmptyDirs(file, new ArrayList<>());
            } while (emptyDirs.size() != 0);
        }
    }

    private static ArrayList<File> findEmptyDirs(File rootDir, ArrayList<File> emptyDirs) {
        File[] childs = rootDir.listFiles();
        if (childs == null || childs.length == 0) {
            if (rootDir.isDirectory())
                emptyDirs.add(rootDir);
            return emptyDirs;
        }
        for (File child : childs) {
            if (child.isDirectory() && (child.listFiles() == null || child.listFiles().length == 0)) {
                emptyDirs.add(child);
            } else {
                findEmptyDirs(child, emptyDirs);
            }
        }
        return emptyDirs;
    }

    private static String getConfigPath() {
        String confPath = properties.getProperty("n2o.config.path");
        return confPath.endsWith("/") ? confPath : confPath + "/";
    }
}

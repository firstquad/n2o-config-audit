package net.n2oapp.framework.config.audit.git.core;

import net.n2oapp.framework.api.exception.N2oException;
import net.n2oapp.framework.config.audit.git.util.N2oGitConstant;
import net.n2oapp.framework.config.audit.git.util.N2oGitFileUtil;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Author.AUTHOR_EMAIL;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Author.SYSTEM_AUTHOR_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SERVER_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Branch.SYSTEM_BRANCH_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.*;
import static net.n2oapp.framework.config.audit.git.util.N2oGitUtil.buildMessage;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.INIT_COMMIT_PREFIX;

/**
 * <p>Точка подключения к git репозиторию.</p>
 * <p>Содержит необходимый минимум git команд.</p>
 * <p>Синглтон.</p>
 *
 * @author dfirstov
 * @since 30.06.2015
 */
public class N2oGitCore {
    private static N2oGitCore instance;
    private static Git git;
    private static boolean init;
    private static Logger logger = LoggerFactory.getLogger(N2oGitCore.class);

    private N2oGitCore(String repositoryPath) {
        try {
            init(createRepoDirectory(repositoryPath));
        } catch (Exception e) {
            throw new N2oException(e);
        }
    }

    /**
     * <p>Создает репозиторий при выполнении условий:</p>
     * <p>1) в каталоге отсутствует репозиторий.</p>
     * <p>2) каталог пуст.</p>
     * <p>Если репозиторий создан ранее, инициализирует его.</p>
     * <p>аналог команды ({@code git init})</p>
     * <p>Фабричный метод синглтона </p>
     *
     * @return экземпляр {@link N2oGitCore}
     */
    public static N2oGitCore getInstance() {
        if (instance == null) {
            initConstant();
            if (!N2oGitConstant.auditEnabled || N2oGitConstant.repositoryPath == null)
                return null;
            instance = new N2oGitCore(repositoryPath);
        }
        return instance;
    }

    private void init(File repoPath) {
        try {
            git = Git.init().setDirectory(repoPath).call();
            if (initRepo(repoPath))
                doCheckout(SERVER_BRANCH_NAME);
        } catch (GitAPIException | IOException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    private File createRepoDirectory(String repositoryPath) throws IOException {
        File repoPath = new File(repositoryPath);
        if (!repoPath.exists()) {
            repoPath.mkdirs();
            repoPath.createNewFile();
        }
        return repoPath;
    }

    private boolean initRepo(File repoPath) {
        try {
            if (getRepository().resolve(Constants.HEAD) != null) {
                return doInit();
            }
            if (!isClean()) {
                getRepository().close();
                FileUtils.forceDelete(getRepository().getDirectory());
                return false;
            }
            createMasterBranch(repoPath);
            if (!SYSTEM_BRANCH_NAME.equals("master")) {
                doCheckout(SYSTEM_BRANCH_NAME, true);
            }
            doCheckout(SERVER_BRANCH_NAME, true);
            if (!SYSTEM_BRANCH_NAME.equals("master"))
                git.branchDelete().setBranchNames("master").call();
        } catch (IOException | GitAPIException | URISyntaxException e) {
            logger.warn(e.getMessage(), e);
            return false;
        }
        return doInit();
    }

    private boolean doInit() {
        init = true;
        return true;
    }

    private void createMasterBranch(File repoPath) throws IOException, GitAPIException, URISyntaxException {
        createFiles(repoPath);
        git.add().addFilepattern(".gitignore").call();
        git.commit().setAll(true).setMessage(INIT_COMMIT_PREFIX.value).setAuthor(SYSTEM_AUTHOR_NAME, AUTHOR_EMAIL).call();
    }

    /**
     * Возвращает текущий инициализированный репозиторий
     *
     * @return текущий репозиторий
     */
    public Repository getRepository() {
        return git.getRepository();
    }

    /**
     * Переключает текущую ветку на существующую ветку ({@code git checkout branchName})
     *
     * @param branchName название ветки на которую необходимо переключится
     * @throws IOException     не найден git репозиторий
     * @throws GitAPIException ошибка при выполнении git команды(не найдена ветка)     *
     */
    public synchronized void doCheckout(String branchName) throws IOException, GitAPIException {
        doCheckout(branchName, false);
    }

    /**
     * Переключает текущую ветку ({@code git checkout branchName})
     *
     * @param branchName название ветки на которую необходимо переключится
     * @param isCreate   true - создать ветку если она не существует, false - переключить только на существующую
     * @throws IOException     не найден git репозиторий
     * @throws GitAPIException ошибка при выполнении git команды(не найдена ветка)
     */
    public synchronized void doCheckout(String branchName, boolean isCreate) throws IOException, GitAPIException {
        boolean createBranch = isCreate && !git.getRepository().getAllRefs().containsKey("refs/heads/" + branchName);
        if (!isCurrentBranch(branchName)) {
            git.checkout().
                    setCreateBranch(createBranch).
                    setName(branchName).
                    setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.NOTRACK).
                    call();
        }
    }

    /**
     * Определяет текущую ветку.
     *
     * @param branchName название ветки
     * @return true - branchName текущая ветка, false - branchName не текущая ветка
     * throws IOException не найден git репозиторий
     */
    public synchronized boolean isCurrentBranch(String branchName) throws IOException {
        return branchName.equals(git.getRepository().getBranch());
    }

    private void createFiles(File repoPath) throws IOException, URISyntaxException {
        createFile(repoPath.getAbsolutePath(), "/.gitignore", RESOURCE_PATH + "core/template/gitignore-template");
        createFile(repoPath.getAbsolutePath(), "/.git/config", RESOURCE_PATH + "core/template/gitconfig-template");
    }

    private void createFile(String repoPath, String path, String resourceName) throws IOException, URISyntaxException {
        File file = new File(repoPath + path);
        URL resource = this.getClass().getClassLoader().getResource(resourceName);
        if (resource != null) {
            N2oGitFileUtil.createFile(resource.toURI().toString(), file);
        }
    }

    /**
     * Добавляет файл в git индекс по локальному пути {@code git add --path @param path}
     *
     * @param path локальный путь к файлу
     */
    public synchronized void add(String path) {
        try {
            git.add().addFilepattern(path).call();
        } catch (GitAPIException e) {
            throw new N2oException(e);
        }
    }

    /**
     * Добавляет только измененный файл в git индекс по локальному пути {@code git add -u --path @param path}
     *
     * @param path название ветки
     */
    public synchronized void addUpdated(String path) {
        try {
            git.add().setUpdate(true).addFilepattern(path).call();
        } catch (GitAPIException e) {
            throw new N2oException(e);
        }
    }

    /**
     * Делает коммит вне зависимости от изменений.
     *
     * @param message сообщение коммита
     * @param author  автор коммита
     */
    public synchronized void commit(String message, String author) {
        try {
            message = buildMessage(message);
            PersonIdent personIdent = new PersonIdent(author == null ? "undefined" : author, AUTHOR_EMAIL);
            git.commit().setMessage(message).setAuthor(personIdent).setCommitter(personIdent).call();
        } catch (GitAPIException e) {
            throw new N2oException(e);
        }
    }

    /**
     * Определяет наличие изменений в репозитории.
     *
     * @return true - в репозитории нет новых, измененных, удаленных, непроиндексированных файлов, false - обратное
     */
    public synchronized Boolean isClean() {
        try {
            Status statusCall = git.status().call();
            return statusCall.getAdded().isEmpty()
                    && statusCall.getChanged().isEmpty()
                    && statusCall.getModified().isEmpty()
                    && statusCall.getRemoved().isEmpty()
                    && statusCall.getUntracked().isEmpty();
        } catch (GitAPIException e) {
            throw new N2oException(e);
        }
    }

    /**
     * Возвращает инициализированный объект {@link Git}
     *
     * @return объект {@link Git}
     */
    public Git getGit() {
        return git;
    }

    /**
     * Определяет состояние инициализации git репозитория.
     *
     * @return true - репозиторий инициализирован,  false - репозиторий не инициализирован
     */
    public static boolean isInit() {
        return init;
    }

    /**
     * Финализирует git репозиторий.
     */
    public void closeRepo() {
        instance = null;
        init = false;
        git.close();
    }
}

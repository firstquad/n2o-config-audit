package net.n2oapp.framework.config.audit.git.service;

import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.audit.git.util.N2oGitConstant;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.audit.model.N2oConfigConflict;
import net.n2oapp.framework.config.register.audit.model.N2oConfigHistory;
import net.n2oapp.framework.config.register.audit.util.N2oConfigConflictParser;
import net.n2oapp.framework.config.util.FileSystemUtil;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.Author.SYSTEM_AUTHOR_NAME;
import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.DEFAULT_FILE_ENCODING;
import static net.n2oapp.framework.config.audit.git.util.N2oGitUtil.*;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMergeMode.*;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.*;

/**
 * @author dfirstov
 * @since 10.07.2015
 */
public class N2oGit {
    private N2oGitCore gitCore = N2oGitCore.getInstance();

    public void commit(String localPath, String author, InfoStatus.Status status) {
        if (localPath == null || localPath.equals(""))
            return;
        gitCore.add(localPath);
        if (isClean())
            return;
        String message = "";
        if (wasConflict(status, countLastCommitConflict(localPath)))
            message = conflictMessage(status);
        gitCore.commit(message + localPath, author);
    }

    public void commitRemoved(String localPath, String author, InfoStatus.Status status) {
        if (localPath == null || localPath.equals(""))
            return;
        gitCore.addUpdated(localPath);
        if (isClean())
            return;
        String message = buildMessageForRemove(localPath, status);
        gitCore.commit(message + localPath, author);
    }


    public void commitAll(String message, String author) {
        commitAll(message, author, false);
    }

    public void commitAll(String message, String author, Boolean forceCommit) {
        gitCore.add(".");
        if (isClean() && !forceCommit)
            return;
        gitCore.commit(message, author);
    }

    public void add(String localPath) {
        if (localPath == null || localPath.equals(""))
            return;
        gitCore.add(localPath);
    }

    public void doCheckout(String branchName) throws IOException, GitAPIException {
        gitCore.doCheckout(branchName);
    }

    public synchronized void doMerge(String branchRef, String mode) throws GitAPIException, IOException {
        Repository repo = gitCore.getGit().getRepository();
        MergeResult mergeResult = gitCore.getGit()
                .merge()
                .include(repo.getRef(branchRef))
                .setCommit(false)
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .call();
        if (MergeResult.MergeStatus.ALREADY_UP_TO_DATE.equals(mergeResult.getMergeStatus()))
            return;
        Map<String, int[][]> conflicts = mergeResult.getConflicts();
        commitAll(resolveMessage(branchRef, conflicts), SYSTEM_AUTHOR_NAME, true);
        if (conflicts != null && !MANUAL.getValue().equals(mode) && isAutomaticMode(mode)) {
            autoResolveConflicts(mode, conflicts.keySet());
        }
    }

    private void autoResolveConflicts(String mode, Set<String> conflicts) throws IOException, GitAPIException {
        Iterable<RevCommit> commits = gitCore.getGit().log().setMaxCount(1).call();
        Iterator<RevCommit> commitIterator = commits.iterator();
        if (!commitIterator.hasNext())
            return;
        RevCommit commit = commitIterator.next();
        for (String localPath : conflicts) {
            String content = resolveContentByMode(mode, commit, localPath);
            if ("".equals(content))
                continue;
            FileSystemUtil.saveContentToFile(content, resolveAbsolutePath(localPath));
            gitCore.add(localPath);
        }
        gitCore.commit(String.format(TEMPLATE_PREFIX.value, RESOLVED_AUTO_PREFIX + mode.toUpperCase()), SYSTEM_AUTHOR_NAME);
    }

    private String resolveContentByMode(String mode, RevCommit commit, String localPath) throws IOException {
        if (OURS.getValue().equals(mode) || THEIRS.getValue().equals(mode)) {
            return retrieveContent(commit.getParent(THEIRS.getValue().equals(mode) ? 1 : 0), localPath);
        } else if (MERGE_OURS.getValue().equals(mode) || MERGE_THEIRS.getValue().equals(mode)) {
            N2oConfigConflict configConflict = new N2oConfigConflict();
            configConflict.setConflictContent(retrieveContent(commit, localPath));
            N2oConfigConflictParser.restoreContentsByConflict(configConflict);
            if (MERGE_OURS.getValue().equals(mode))
                return configConflict.getContent();
            else if (MERGE_THEIRS.getValue().equals(mode))
                return configConflict.getMergeContent();
        }
        return "";
    }

    private boolean isAutomaticMode(String mode) {
        return (OURS.getValue().equals(mode)
                || THEIRS.getValue().equals(mode)
                || MERGE_OURS.getValue().equals(mode)
                || MERGE_THEIRS.getValue().equals(mode));
    }

    private String resolveMessage(String branchRef, Map<String, int[][]> conflicts) throws IOException {
        if (conflicts != null)
            return CONFLICT_MERGE_PREFIX.value + conflicts.size();
        else
            return resolveMergeMessage(branchRef, gitCore.getGit().getRepository().getBranch());
    }

    public File resolveAbsolutePath(String localPath) {
        return new File(gitCore.getGit().getRepository().getDirectory().getPath().replace(".git", "") + localPath);
    }

    public List<N2oConfigHistory> auditHistory(String localPath) {
        List<N2oConfigHistory> histories = new ArrayList<>();
        try {
            Iterable<RevCommit> revCommits = gitCore.getGit().log().addPath(localPath).call();
            List<RevCommit> commits = new ArrayList<>();
            revCommits.forEach(commits::add);
            Repository repo = gitCore.getRepository();
            addInitCommit(commits);
            for (int i = 0; i < commits.size() - 1; i++) {
                RevCommit newCommit = commits.get(i);
                RevCommit oldCommit;
                if (i == commits.size() - 2) {
                    oldCommit = commits.get(i + 1);
                } else {
                    oldCommit = newCommit.getParents()[0];
                }
                AbstractTreeIterator oldTreeParser = prepareTreeParser(repo, oldCommit.getName());
                AbstractTreeIterator newTreeParser = prepareTreeParser(repo, newCommit.getName());
                List<DiffEntry> diff = gitCore.getGit().diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setPathFilter(PathFilter.create(localPath)).call();
                for (DiffEntry entry : diff) {
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        DiffFormatter formatter = new DiffFormatter(outputStream);
                        formatter.setRepository(repo);
                        formatter.format(entry);
                        fillHistory(histories, entry, localPath, newCommit, oldCommit, outputStream);
                    } catch (IOException e) {
                        throw new RuntimeException("Config audit history error.", e);
                    }
                }
            }
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException("Config audit history error.", e);
        }
        return histories;
    }

    private void addInitCommit(List<RevCommit> commits) throws GitAPIException, IOException {
        List<RevCommit> initCommits = new ArrayList<>();
        Iterable<RevCommit> initCommitIter = gitCore.getGit().log().addPath(".gitignore").call();
        initCommitIter.forEach(initCommits::add);
        if (initCommits.size() != 0)
            commits.add(initCommits.get(initCommits.size() - 1));
    }

    private void fillHistory(List<N2oConfigHistory> histories, DiffEntry entry, String localPath, RevCommit newCommit, RevCommit oldCommit, ByteArrayOutputStream outputStream) throws IOException {
        N2oConfigHistory history = new N2oConfigHistory();
        history.setId(newCommit.getName() + "_" + entry.toString());
        history.setCode(newCommit.getName());
        history.setLocalPath(localPath);
        history.setDiff(newCommit.toString() + "\n" + new String(outputStream.toByteArray(), DEFAULT_FILE_ENCODING));
        PersonIdent authorIdent = newCommit.getAuthorIdent();
        history.setAuthor(authorIdent.getName());
        history.setMessage(newCommit.getFullMessage());
        history.setDate(new SimpleDateFormat().format(authorIdent.getWhen()));
        history.setContent(retrieveContent(newCommit, localPath));
        history.setPreviousContent(retrieveContent(oldCommit, localPath));
        histories.add(history);
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repo, String ref) throws IOException {
        RevWalk walk = new RevWalk(repo);
        ObjectId objectId = repo.resolve(ref);
        RevCommit commit = walk.parseCommit(objectId);
        RevTree tree = walk.parseTree(commit.getTree().getId());
        CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
        try (ObjectReader oldReader = repo.newObjectReader()) {
            oldTreeParser.reset(oldReader, tree.getId());
        }
        walk.dispose();
        return oldTreeParser;
    }

    public N2oConfigConflict retrieveConflictFromLog(String localPath) {
        N2oConfigConflict configConflict = new N2oConfigConflict();
        try {
            Iterable<RevCommit> revCommits = gitCore.getGit().log().all().addPath(localPath).call();
            if (revCommits == null)
                return configConflict;
            List<RevCommit> commits = new ArrayList<>();
            revCommits.forEach(commits::add);
            RevCommit lastCommit = commits.get(0);
            ObjectId commitId = gitCore.getRepository().resolve(lastCommit.getName());
            RevWalk revWalk = new RevWalk(gitCore.getRepository());
            RevCommit conflictCommit = revWalk.parseCommit(commitId);
            RevCommit[] parents = conflictCommit.getParents();
            RevCommit origin;
            RevCommit merge;
            if (parents.length > 1) {
                origin = parents[0];
                merge = parents[1];
            } else {
                origin = parents[0];
                merge = conflictCommit;
            }
            RevCommit commitBeforeConflict = revWalk.parseCommit(gitCore.getRepository().resolve(merge.getName())).getParent(0);
            configConflict.setId(conflictCommit.getName());
            configConflict.setParentContent(retrieveContent(commitBeforeConflict, localPath));
            configConflict.setConflictContent(retrieveContent(conflictCommit, localPath));
            configConflict.setContent(retrieveContent(origin, localPath));
            configConflict.setMergeContent(retrieveContent(merge, localPath));
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Config audit retrieve conflict from log error.", e);
        }
        return configConflict;
    }

    public String retrieveGraph() {
        return drawGraph()
                .stream()
                .reduce("", (s1, s2) -> s1 + N2oGitConstant.DEFAULT_LINE_END + s2);
    }

    public boolean isInit() {
        return N2oGitCore.isInit();
    }

    private Boolean isClean() {
        return gitCore.isClean();
    }

    public void setGitCore(N2oGitCore gitCore) {
        this.gitCore = gitCore;
    }

    public N2oGitCore getGitCore() {
        return gitCore;
    }

    public void closeRepo() {
        gitCore.closeRepo();
    }
}

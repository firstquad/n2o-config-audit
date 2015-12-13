package net.n2oapp.framework.config.audit.git.util;

import net.n2oapp.framework.config.audit.git.core.N2oGitCore;
import net.n2oapp.framework.config.register.InfoStatus;
import net.n2oapp.framework.config.register.audit.util.N2oConfigConflictParser;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.n2oapp.framework.config.audit.git.util.N2oGitConstant.DEFAULT_FILE_ENCODING;
import static net.n2oapp.framework.config.register.audit.model.N2oConfigMessage.*;

/**
 * @author dfirstov
 * @since 16.09.2015
 */
public class N2oGitUtil {
    private static N2oGitCore gitCore = N2oGitCore.getInstance();
    private static final String COMMIT = "commit ";
    private static final String AUTHOR = "Author: ";
    private static final String DATE = "Date: ";
    private static final String MESSAGE = "Message: ";
    private static final String MERGE = "Merge: ";


    public static String buildMessage(String message) throws GitAPIException {
        if (hasPrefix(message))
            return message;
        Status status = gitCore.getGit().status().call();
        if (status.getAdded().size() == 1)
            return CREATED_PREFIX + message;
        else if (status.getChanged().size() == 1)
            return UPDATED_PREFIX + message;
        else if (status.getRemoved().size() == 1)
            return DELETED_PREFIX + message;
        return message;
    }

    public static String conflictMessage(InfoStatus.Status status) {
        return (InfoStatus.Status.SYSTEM.equals(status) ? RESOLVED_TO_SYSTEM_PREFIX : RESOLVED_PREFIX).value;
    }

    public static boolean wasConflict(InfoStatus.Status status, int countConflict) {
        return countConflict > 0 && !InfoStatus.Status.CONFLICT.equals(status);
    }


    public static String buildMessageForRemove(String localPath, InfoStatus.Status status) {
        if (InfoStatus.Status.SERVER.equals(status))
            return DELETED_PREFIX.value;
        else {
            int countConflict = countLastCommitConflict(localPath);
            if (wasConflict(status, countConflict)) {
                return conflictMessage(status);
            } else
                return RESTORED_PREFIX.value;
        }
    }


    public static int countLastCommitConflict(String localPath) {
        int countConflict = 0;
        try {
            Iterator<RevCommit> revCommits = gitCore.getGit().log().addPath(localPath).call().iterator();
            if (revCommits.hasNext()) {
                String content = retrieveContent(revCommits.next(), localPath);
                countConflict = N2oConfigConflictParser.countConflict(content);
            }
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException("Config audit calculate last conflict error.", e);
        }
        return countConflict;
    }

    public static String retrieveContent(RevCommit commitObject, String localPath) throws IOException {
        RevCommit commit = new RevWalk(gitCore.getRepository()).parseCommit(commitObject);
        RevTree tree = commit.getTree();
        Repository repo = gitCore.getRepository();
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        treeWalk.setFilter(PathFilter.create(localPath));
        if (!treeWalk.next()) {
            return "";
        }
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        loader.copyTo(out);
        return new String(out.toByteArray(), DEFAULT_FILE_ENCODING);
    }

    public static String resolveMergeMessage(String systemBranch, String serverBranch) {
        return MERGE_PREFIX + String.format(MERGE_BRANCH_INFO.value, systemBranch, serverBranch);
    }

    public static List<String> drawGraph() {
        List<String> graph = new ArrayList<>();
        try {
            Iterator<RevCommit> commits = gitCore.getGit().log().all().call().iterator();
            RevCommit developCommit = commits.next();
            boolean isForked = false;
            while (developCommit != null) {
                if (developCommit.getParentCount() > 1) {
                    developCommit = createFork(graph, developCommit, isForked);
                    isForked = developCommit != null;
                    while (developCommit != null && developCommit.getParentCount() == 1) {
                        graph.add("* | " + COMMIT + developCommit.getName());
                        graph.add("| | " + AUTHOR + developCommit.getAuthorIdent().getName());
                        addSimpleForkedBlock(graph, developCommit);
                        developCommit = getParentRevCommit(developCommit, 0);
                    }
                } else if (!isForked) {
                    graph.add("*   " + COMMIT + developCommit.getName());
                    graph.add("|   " + AUTHOR + developCommit.getAuthorIdent().getName());
                    graph.add("|   " + DATE + developCommit.getAuthorIdent().getWhen());
                    graph.add("|   " + MESSAGE + developCommit.getFullMessage());
                    graph.add("|");
                    developCommit = getParentRevCommit(developCommit, 0);
                }
                if (developCommit != null && developCommit.getParentCount() == 0) {
                    graph.add("*   " + COMMIT + developCommit.getName());
                    graph.add("    " + AUTHOR + developCommit.getAuthorIdent().getName());
                    graph.add("    " + DATE + developCommit.getAuthorIdent().getWhen());
                    graph.add("    " + MESSAGE + developCommit.getFullMessage());
                    developCommit = getParentRevCommit(developCommit, 0);
                }
            }
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Config audit draw graph error.", e);
        }
        return graph;
    }

    private static RevCommit createFork(List<String> graph, RevCommit developCommit, boolean isForked) throws IOException {
        graph.add((isForked ? "* |  " : "*   ") + COMMIT + developCommit.getName());
        graph.add((isForked ? "|\\ \\ " : "|\\  ") + MERGE + getParentRevCommit(developCommit, 0).getName().substring(0, 6) + " " + getParentRevCommit(developCommit, 1).getName().substring(0, 6));
        graph.add((isForked ? "| |/" : "| |") + " " + AUTHOR + developCommit.getAuthorIdent().getName());
        addSimpleForkedBlock(graph, developCommit);
        RevCommit masterCommit = getParentRevCommit(developCommit, 1);
        RevCommit parentMasterCommit = getParentRevCommit(developCommit, 0);
        if (parentMasterCommit.getParentCount() > 0) {
            graph.add("| * " + COMMIT + masterCommit.getName());
            graph.add("| | " + AUTHOR + masterCommit.getAuthorIdent().getName());
            addSimpleForkedBlock(graph, masterCommit);
        } else {
            graph.add("| * " + COMMIT + masterCommit.getName());
            graph.add("|/  " + AUTHOR + masterCommit.getAuthorIdent().getName());
            graph.add("|   " + DATE + masterCommit.getAuthorIdent().getWhen());
            graph.add("|   " + MESSAGE + masterCommit.getFullMessage());
            graph.add("|");
        }
        developCommit = getParentRevCommit(developCommit, 0);
        return developCommit;
    }

    private static void addSimpleForkedBlock(List<String> graph, RevCommit masterCommit) {
        graph.add("| | " + DATE + masterCommit.getAuthorIdent().getWhen());
        graph.add("| | " + MESSAGE + masterCommit.getFullMessage());
        graph.add("| |");
    }

    private static RevCommit getParentRevCommit(RevCommit commit, int parentId) throws IOException {
        if (commit.getParents() == null || commit.getParentCount() == 0)
            return null;
        ObjectId commitId = gitCore.getRepository().resolve(commit.getParent(parentId).getName());
        RevWalk revWalk = new RevWalk(gitCore.getRepository());
        commit = revWalk.parseCommit(commitId);
        return commit;
    }
}

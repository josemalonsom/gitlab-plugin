package argelbargel.jenkins.plugins.gitlab_branch_source;


import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.SCMHeadMixin;

import javax.annotation.Nonnull;


public abstract class GitLabSCMHead extends SCMHead implements SCMHeadMixin {
    public static final String REVISION_HEAD = "HEAD";

    public static GitLabSCMBranchHead createBranch(String name, String hash) {
        return createBranch(name, hash, false);
    }

    public static GitLabSCMTagHead createTag(String name, String hash, long timestamp) {
        return new GitLabSCMTagHead(name, hash, timestamp);
    }

    public static GitLabSCMMergeRequestHead createMergeRequest(int id, String name, int sourceProjectId, GitLabSCMHead source, GitLabSCMBranchHead target) {
        return new GitLabSCMMergeRequestHead(id, name, sourceProjectId, source, target, false);
    }

    static GitLabSCMBranchHead createBranch(String name, String hash, boolean hasMergeRequest) {
        return new GitLabSCMBranchHead(name, hash, hasMergeRequest);
    }


    GitLabSCMHead(String name) {
        super(name);
    }

    @Nonnull
    public abstract SCMRevisionImpl getRevision();

    @Nonnull
    abstract GitLabSCMRefSpec getRefSpec();

    @Nonnull
    abstract GitSCM createSCM(GitLabSCMSource source);
}

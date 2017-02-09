package argelbargel.jenkins.plugins.gitlab_branch_source;


import javax.annotation.Nonnull;

import static argelbargel.jenkins.plugins.gitlab_branch_source.GitLabSCMRefSpec.BRANCHES;


public final class GitLabSCMBranchHead extends GitLabSCMHeadImpl {
    private final boolean hasMergeRequest;

    GitLabSCMBranchHead(@Nonnull String name, String hash, boolean hasMergeRequest) {
        super(name, hash, Messages.GitLabSCMBranch_Pronoun(), BRANCHES);
        this.hasMergeRequest = hasMergeRequest;
    }

    boolean hasMergeRequest() {
        return hasMergeRequest;
    }
}

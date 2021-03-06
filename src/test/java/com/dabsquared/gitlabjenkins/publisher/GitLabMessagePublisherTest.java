package com.dabsquared.gitlabjenkins.publisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author Nikolay Ustinov
 */
public class GitLabMessagePublisherTest {

    private static final String GIT_LAB_CONNECTION = "GitLab";
    private static final String API_TOKEN = "secret";

    @ClassRule
    public static MockServerRule mockServer = new MockServerRule(new Object());

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private MockServerClient mockServerClient;
    private BuildListener listener;

    @BeforeClass
    public static void setupConnection() throws IOException {
        GitLabConnectionConfig connectionConfig = jenkins.get(GitLabConnectionConfig.class);
        String apiTokenId = "apiTokenId";
        for (CredentialsStore credentialsStore : CredentialsProvider.lookupStores(Jenkins.getInstance())) {
            if (credentialsStore instanceof SystemCredentialsProvider.StoreImpl) {
                List<Domain> domains = credentialsStore.getDomains();
                credentialsStore.addCredentials(domains.get(0),
                                                new StringCredentialsImpl(CredentialsScope.SYSTEM, apiTokenId, "GitLab API Token", Secret.fromString(API_TOKEN)));
            }
        }
        connectionConfig.addConnection(new GitLabConnection(GIT_LAB_CONNECTION, "http://localhost:" + mockServer.getPort() + "/gitlab", apiTokenId, false, 10, 10));
    }

    @Before
    public void setup() {
        listener = new StreamBuildListener(jenkins.createTaskListener().getLogger(), Charset.defaultCharset());
        mockServerClient = new MockServerClient("localhost", mockServer.getPort());
    }

    @After
    public void cleanup() {
        mockServerClient.reset();
    }

    @Test
    public void canceled() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.ABORTED, buildNumber);
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String defaultNote = MessageFormat.format(":point_up: Jenkins Build {0}\n\nResults available at: [Jenkins [{1} #{2}]]({3})",
            Result.ABORTED, build.getParent().getDisplayName(), buildNumber, buildUrl);

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, false, false, false, null, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void success() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.SUCCESS, buildNumber);
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String defaultNote = MessageFormat.format(":white_check_mark: Jenkins Build {0}\n\nResults available at: [Jenkins [{1} #{2}]]({3})",
            Result.SUCCESS, build.getParent().getDisplayName(), buildNumber, buildUrl);

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, false, false, false, null, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void success_withOnlyForFailure() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.SUCCESS, buildNumber);

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(true, false, false, false, null, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verifyZeroInteractions();
    }

    @Test
    public void failed() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.FAILURE, buildNumber);
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String defaultNote = MessageFormat.format(":negative_squared_cross_mark: Jenkins Build {0}\n\nResults available at: [Jenkins [{1} #{2}]]({3})",
            Result.FAILURE, build.getParent().getDisplayName(), buildNumber, buildUrl);

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, false, false, false, null, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void failed_withOnlyForFailed() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.FAILURE, buildNumber);
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String defaultNote = MessageFormat.format(":negative_squared_cross_mark: Jenkins Build {0}\n\nResults available at: [Jenkins [{1} #{2}]]({3})",
                                                  Result.FAILURE, build.getParent().getDisplayName(), buildNumber, buildUrl);

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(true, false, false, false, null, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void canceledWithCustomNote() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.ABORTED, buildNumber);
        String defaultNote = "abort";

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, false, false, true, null, null, defaultNote));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void successWithCustomNote() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.SUCCESS, buildNumber);
        String defaultNote = "success";

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, true, false, false, defaultNote, null, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    @Test
    public void failedWithCustomNote() throws IOException, InterruptedException {
        Integer buildNumber = 1;
        Integer projectId = 3;
        Integer mergeRequestId = 1;
        AbstractBuild build = mockBuild("/build/123", GIT_LAB_CONNECTION, Result.FAILURE, buildNumber);
        String defaultNote = "failure";

        HttpRequest[] requests = new HttpRequest[] {
            prepareSendMessageWithSuccessResponse(projectId, mergeRequestId, defaultNote)
        };

        GitLabMessagePublisher publisher = spy(new GitLabMessagePublisher(false, false, true, false, null, defaultNote, null));
        doReturn(projectId).when(publisher).getProjectId(build);
        doReturn(mergeRequestId).when(publisher).getMergeRequestId(build);
        publisher.perform(build, null, listener);

        mockServerClient.verify(requests);
    }

    private HttpRequest prepareSendMessageWithSuccessResponse(Integer projectId, Integer mergeRequestId, String body) throws UnsupportedEncodingException {
        HttpRequest updateCommitStatus = prepareSendMessageStatus(projectId, mergeRequestId, body);
        mockServerClient.when(updateCommitStatus).respond(response().withStatusCode(200));
        return updateCommitStatus;
    }

    private HttpRequest prepareSendMessageStatus(Integer projectId, Integer mergeRequestId, String body) throws UnsupportedEncodingException {
        return request()
                .withPath("/gitlab/api/v3/projects/" + projectId + "/merge_requests/" + mergeRequestId + "/notes")
                .withMethod("POST")
                .withHeader("PRIVATE-TOKEN", "secret")
                .withBody("body=" + URLEncoder.encode(body, "UTF-8"));
    }

    private AbstractBuild mockBuild(String buildUrl, String gitLabConnection, Result result, Integer buildNumber, String... remoteUrls) {
        AbstractBuild build = mock(AbstractBuild.class);
        BuildData buildData = mock(BuildData.class);
        when(buildData.getRemoteUrls()).thenReturn(new HashSet<>(Arrays.asList(remoteUrls)));
        when(build.getAction(BuildData.class)).thenReturn(buildData);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(buildUrl);
        when(build.getResult()).thenReturn(result);
        when(build.getUrl()).thenReturn(buildUrl);
        when(build.getNumber()).thenReturn(buildNumber);

        AbstractProject<?, ?> project = mock(AbstractProject.class);
        when(project.getProperty(GitLabConnectionProperty.class)).thenReturn(new GitLabConnectionProperty(gitLabConnection));
        when(build.getProject()).thenReturn(project);
        EnvVars environment = mock(EnvVars.class);
        when(environment.expand(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return (String) invocation.getArguments()[0];
            }
        });
        try {
            when(build.getEnvironment(any(TaskListener.class))).thenReturn(environment);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return build;
    }
}

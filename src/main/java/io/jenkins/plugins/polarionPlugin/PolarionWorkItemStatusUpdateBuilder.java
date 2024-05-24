package io.jenkins.plugins.polarionPlugin;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.jenkins.plugins.polarionPlugin.PolarionNotifier.DescriptorImpl;
import java.io.IOException;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class PolarionWorkItemStatusUpdateBuilder extends Builder implements SimpleBuildStep {

    public String projectId;
    public String workItemId;
    public String workflowAction;
    public static final String DISPLAY_NAME = "Polarion WorkItem Status Updater";

    @DataBoundConstructor
    public PolarionWorkItemStatusUpdateBuilder(String projectId, String workItemId, String workflowAction) {
        this.projectId = projectId;
        this.workItemId = workItemId;
        this.workflowAction = workflowAction;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public String getWworkflowAction() {
        return workflowAction;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        DescriptorImpl globalConfig = getGlobalConfig();
        PolarionConnector polarionConnector = new PolarionConnector(
                globalConfig.getUrl(), globalConfig.getToken().getPlainText());
        String actualID =
                workItemId.startsWith("$") ? getParameter(run, listener, workItemId.substring(1)) : workItemId;
        listener.getLogger().println(DISPLAY_NAME +" - attempting to trigger workflow action(" +workflowAction+") for workitem -"+actualID);
        polarionConnector.updateWorkItemWithWorkFlow(projectId, actualID, workflowAction);
        listener.getLogger().println(DISPLAY_NAME +" - Trigger workflow action(" +workflowAction+") for workitem -"+actualID+" successful.");
    }

    private String getParameter(Run<?, ?> run, TaskListener listener, String paramKey)
            throws IOException, InterruptedException {
        return run.getEnvironment(listener).get(paramKey);
    }

    public static DescriptorImpl getGlobalConfig() {
        @SuppressWarnings("deprecation")
        DescriptorImpl descriptor = (DescriptorImpl) Jenkins.getInstance().getDescriptor(PolarionNotifier.class);
        descriptor.load();
        return descriptor;
    }

    @Extension
    public static final class BuilderDescriptor extends BuildStepDescriptor<Builder> {
        public static final String NO_CONNECTION =
                "Please fill in connection details in Manage Jenkins -> Configure System";
        
        public static final String DISPLAY_NAME = "Polarion WorkItem Status Updater";
        public FormValidation doCheckWorkItem(
                @QueryParameter("projectId") String projectId, @QueryParameter("workItemId") String workItemId)
                throws IOException, InterruptedException {
            DescriptorImpl globalConfig = getGlobalConfig();
            String url = globalConfig.getUrl();
            String token = globalConfig.getToken().getPlainText();
            if (StringUtils.isBlank(url) || StringUtils.isBlank(token)) {
                return FormValidation.error(NO_CONNECTION);
            }
            PolarionConnector polarionConnector = new PolarionConnector(url, token);
            try {
                polarionConnector.checkWorkItem(projectId, workItemId);
            } catch (JSONException | IOException | HttpException | InterruptedException e) {
                return FormValidation.error("Connection error : " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return PolarionWorkItemStatusUpdateBuilder.DISPLAY_NAME;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}

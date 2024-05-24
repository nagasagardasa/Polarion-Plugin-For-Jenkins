package io.jenkins.plugins.polarionPlugin;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.MasterToSlaveFileCallable;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.json.JSONException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class PolarionNotifier extends Notifier {
    public static final String PLUGIN_SHORTNAME = "polarion-testResultReporter";

    private String project;
    private String testRunIdPrefix;
    private String testRunTitle;
    private String testRunType;
    private String groupId;
    private String testResultsXml;

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @DataBoundConstructor
    public PolarionNotifier(
            String project,
            String testRunIdPrefix,
            String testRunTitle,
            String testRunType,
            String groupId,
            String testResultsXml) {
        this.project = project;
        this.testRunIdPrefix = testRunIdPrefix;
        this.testRunTitle = testRunTitle;
        this.testRunType = testRunType;
        this.groupId = groupId;
        this.testResultsXml = testResultsXml;
    }

    public String getProject() {
        return project;
    }

    public String getTestRunIdPrefix() {
        return testRunIdPrefix;
    }

    public String getTestResultsXml() {
        return testResultsXml;
    }

    public String getTestRunTitle() {
        return testRunTitle;
    }

    public String getTestRunType() {
        return testRunType;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws IOException, AbortException {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new AbortException("no workspace for " + build);
        }
        try {
            final String expandTestResults = build.getEnvironment(listener).expand(this.testResultsXml);
            final long timeOnMaster = System.currentTimeMillis();

            listener.getLogger().println("Starting test results  upload to Polarion project - " + this.project);
            String restToken = this.getDescriptor().getToken().getPlainText();
            String mixedID = workspace.act(new ParseResultCallable(
                    listener,
                    expandTestResults,
                    this.getDescriptor().getUrl(),
                    restToken,
                    project,
                    testRunIdPrefix,
                    testRunTitle,
                    testRunType,
                    groupId));
            long time = System.currentTimeMillis() - timeOnMaster;
            String fullTestRunID = mixedID.split("###")[0];
            String JobID = mixedID.split("###")[1];
            String testRunID = fullTestRunID.replace(this.project + "/", "");
            String jobSubmitted =
                    String.format("TestResults Upload job subimtted with JobID %s. Took %sms", JobID, time);
            listener.getLogger().println(jobSubmitted);
            build.setDescription(jobSubmitted + "\n\n" + "Job log - "
                    + this.getDescriptor().getUrl() + "/job-report?jobId=" + JobID + "\n\n" + "TestRun link - "
                    + this.getDescriptor().getUrl() + "/redirect/project/" + this.project + "/testrun?id=" + testRunID);
            build.save();
        } catch (InterruptedException e) {
            listener.getLogger().println("Interupted, " + e.getMessage());
            return false;
        } catch (JSONException | IOException | HttpException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        }
        return true;
    }

    private static final class ParseResultCallable extends MasterToSlaveFileCallable<String> {

        private static final long serialVersionUID = 1L;
        private BuildListener listener;
        private final String testResults;
        private final String url;
        private final String token;
        private final String project;
        private String testRunIdPrefix;
        private String testRunTitle;
        private String testRunType;
        private String groupId;

        private ParseResultCallable(
                BuildListener listener,
                String testResults,
                String url,
                String token,
                String project,
                String testRunIdPrefix,
                String testRunTitle,
                String testRunType,
                String groupId) {
            this.listener = listener;
            this.testResults = testResults;
            this.url = url;
            this.token = token;
            this.project = project;
            this.testRunIdPrefix = testRunIdPrefix;
            this.testRunTitle = testRunTitle;
            this.testRunType = testRunType;
            this.groupId = groupId;
        }

        @Override
        public String invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
            List<File> listFiles = new ArrayList<>();

            FileSet fs = Util.createFileSet(ws, testResults);
            DirectoryScanner ds = fs.getDirectoryScanner();

            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {

                File baseDir = ds.getBasedir();
                for (String value : files) {
                    File reportFile = new File(baseDir, value);
                    listFiles.add(reportFile);
                }
                PolarionConnector polarionConnector = new PolarionConnector(url, token);
                String testRunId = polarionConnector.createNewTestRun(
                        this.project, this.testRunIdPrefix, this.testRunTitle, this.testRunType, this.groupId);
                listener.getLogger()
                        .println(String.format("TestResults are being uploaded to testRun with ID %s.", testRunId));
                String jobId = polarionConnector.publishResults(
                        listFiles.get(0), this.project, testRunId.replace(this.project + "/", ""));
                return testRunId + "###" + jobId;
            }
            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public static final String NO_CONNECTION =
                "Please fill in connection details in Manage Jenkins -> Configure System";
        public static final String DISPLAY_NAME = "Polarion Test Result Reporter";
        private String url;
        private Secret token;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return DescriptorImpl.DISPLAY_NAME;
        }


        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doTestConnection(
                @QueryParameter("url") String url, @QueryParameter("token") String token) {
            try {
                PolarionConnector polarionConnector = new PolarionConnector(url, token);
                polarionConnector.connect();
                return FormValidation.ok("Successful Connection");
            } catch (JSONException | IOException | HttpException | InterruptedException e) {
                return FormValidation.error("Connection error : " + e.getMessage());
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.url = formData.getString("url");
            this.token = Secret.fromString(formData.getString("token"));
            save();
            return super.configure(req, formData);
        }

        public String getUrl() {
            return url;
        }

        public Secret getToken() {
            return token;
        }

        public FormValidation doCheckProject(@QueryParameter("project") String project)
                throws IOException, InterruptedException {
            String restToken = token.getPlainText();
            if (StringUtils.isBlank(url) || StringUtils.isBlank(restToken)) {
                return FormValidation.error(NO_CONNECTION);
            }
            PolarionConnector polarionConnector = new PolarionConnector(url, restToken);
            try {
                polarionConnector.checkProject(project);
            } catch (JSONException | IOException | HttpException | InterruptedException e) {
                return FormValidation.error("Connection error : " + e.getMessage());
            }
            return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         * @param project Project.
         * @param value File mask to validate.
         *
         * @return the validation result.
         * @throws IOException if an error occurs.
         */
        @SuppressWarnings("rawtypes")
        public FormValidation doCheckTestResults(@AncestorInPath AbstractProject project, @QueryParameter String value)
                throws IOException {
            if (project == null) {
                return FormValidation.ok();
            }
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }
    }
}

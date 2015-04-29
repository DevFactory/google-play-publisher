package de.hamm.googleplaypublisher;

import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

@RequiresDomain(value = AndroidPublisherScopeRequirement.class)
public class GooglePlayPublisher extends Recorder {
	private static final Log LOG = LogFactory.getLog(GooglePlayPublisher.class);
	private final String credentialId;
	private final String apkFile;
	private final Track track;
	private final List<ReleaseNotes> releaseNotes;

	@DataBoundConstructor
	public GooglePlayPublisher(String credentialId, String apkFile, Track track, List<ReleaseNotes> releaseNotes) {
		this.credentialId = credentialId;
		this.apkFile = apkFile;
		this.track = track;
		this.releaseNotes = releaseNotes;
	}

	public static List<Track.DescriptorImpl> getTrackDescriptors() {
		List<Track.DescriptorImpl> trackDescriptors = new ArrayList<Track.DescriptorImpl>();
		Jenkins instance = Jenkins.getInstance();
		trackDescriptors.add((Track.DescriptorImpl) instance.getDescriptorOrDie(ProductionTrack.class));
		trackDescriptors.add((Track.DescriptorImpl) instance.getDescriptorOrDie(BetaTrack.class));
		trackDescriptors.add((Track.DescriptorImpl) instance.getDescriptorOrDie(AlphaTrack.class));
		return trackDescriptors;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		PrintStream logger = listener.getLogger();
		logger.println("[Google play Publisher] - Starting");
		expandReleaseNotes(build.getEnvironment(listener));
		try {
			new PublishHelper.Builder()
					.setLogger(logger)
					.setCredentials(GoogleRobotCredentials.getById(credentialId))
					.setApkFilePath(new FilePath(build.getModuleRoot(), apkFile))
					.setTrack(track)
					.setReleaseNotes(releaseNotes)
					.createPublishHelper()
					.publish();
		} catch (PublishHelper.ReadPackageNameException e) {
			logger.println("[Google play Publisher] - " + e.getMessage());
			LOG.error(e.getMessage(), e);
			build.setResult(Result.FAILURE);
			return false;
		} catch (PublishHelper.PublishApkException e) {
			logger.println("[Google play Publisher] - " + e.getMessage());
			LOG.error(e.getMessage(), e);
			build.setResult(Result.FAILURE);
			return false;
		}
		logger.println("[Google play Publisher] - Finished");
		return true;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getCredentialId() {
		return credentialId;
	}

	public String getApkFile() {
		return apkFile;
	}

	public Track getTrack() {
		return track;
	}

	public List<ReleaseNotes> getReleaseNotes() {
		return releaseNotes;
	}

	private void expandReleaseNotes(EnvVars envVars) {
		if (releaseNotes != null) {
			for (ReleaseNotes i : releaseNotes) {
				i.expand(envVars);
			}
		}
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Publish on Google play";
		}
	}
}

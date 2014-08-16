package de.hamm.googleplaypublisher;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class GooglePlayPublisher extends Notifier {
	private static final Log LOG = LogFactory.getLog(GooglePlayPublisher.class);
	private static final String ALL_APK_FILES = "*/**.apk";
	private final String emailAddress;
	private final String p12File;
	private final String apkFiles;
	private final String track;

	@DataBoundConstructor
	public GooglePlayPublisher(String emailAddress, String p12File, String apkFiles,
							   String track) {
		this.emailAddress = emailAddress;
		this.p12File = p12File;
		this.apkFiles = apkFiles;
		this.track = track;
	}

	@SuppressWarnings("unused")
	public String getEmailAddress() {
		return emailAddress;
	}

	@SuppressWarnings("unused")
	public String getP12File() {
		return p12File;
	}

	@SuppressWarnings("unused")
	public String getApkFiles() {
		return apkFiles;
	}

	@SuppressWarnings("unused")
	public String getTrack() {
		return track;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		PrintStream logger = listener.getLogger();
		try {
			FilePath[] list = build.getWorkspace()
					.list(apkFiles != null && !apkFiles.isEmpty() ? apkFiles : ALL_APK_FILES);
			for (FilePath i : list) {
				try {
					new PublishHelper.Builder(logger)
							.setEmailAddress(emailAddress)
							.setP12File(new File(p12File))
							.setApkFile(new File(i.toURI()))
							.setTrack(track)
							.build()
							.publish();
				} catch (IOException e) {
					logger.println(String.format("Failed to convert FilePath '%s' to URI", i));
					LOG.error(String.format("Failed to convert FilePath '%s' to URI", i), e);
				} catch (InterruptedException e) {
					logger.println(String.format("Failed to convert FilePath '%s' to URI", i));
					LOG.error(String.format("Failed to convert FilePath '%s' to URI", i), e);
				} catch (PublishHelper.PublishApkException e) {
					logger.println(e.getMessage());
					LOG.error(e.getMessage(), e);
				}
			}
		} catch (IOException e) {
			logger.println("Failed to retrieve the list of Apk Files");
			LOG.error("Failed to retrieve the list of Apk Files", e);
		} catch (InterruptedException e) {
			logger.println("Failed to retrieve the list of Apk Files");
			LOG.error("Failed to retrieve the list of Apk Files", e);
		}
		return true;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		@SuppressWarnings("unused")
		public ListBoxModel doFillTrackItems() {
			ListBoxModel model = new ListBoxModel();
			model.add("Production", PublishHelper.TRACK_PRODUCTION);
			model.add("Beta", PublishHelper.TRACK_BETA);
			model.add("Alpha", PublishHelper.TRACK_ALPHA);
			return model;
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Publish on Google play";
		}
	}
}

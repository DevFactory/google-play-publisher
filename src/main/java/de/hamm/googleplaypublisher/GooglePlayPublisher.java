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
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class GooglePlayPublisher extends Notifier {
	private static final String ALL_APK_FILES = "*/**.apk";
	private final String serviceAccountEmailAddress;
	private final String serviceAccountP12Key;
	private final String apkFiles;
	private final String track;

	@DataBoundConstructor
	public GooglePlayPublisher(String serviceAccountEmailAddress, String serviceAccountP12Key, String apkFiles,
							   String track) {
		this.serviceAccountEmailAddress = serviceAccountEmailAddress;
		this.serviceAccountP12Key = serviceAccountP12Key;
		this.apkFiles = apkFiles;
		this.track = track;
	}

	@SuppressWarnings("unused")
	public String getServiceAccountEmailAddress() {
		return serviceAccountEmailAddress;
	}

	@SuppressWarnings("unused")
	public String getServiceAccountP12Key() {
		return serviceAccountP12Key;
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
					new ApkPublisher(logger, serviceAccountEmailAddress, new File(serviceAccountP12Key),
							new File(i.toURI()), track).publish();
				} catch (ApkPublisher.PublishApkException e) {
					logger.println("Failed to publish '" + i + "'");
				}
			}
		} catch (IOException e) {
			logger.println("Failed to retrieve the list of Apk Files");
		} catch (InterruptedException e) {
			logger.println("Failed to retrieve the list of Apk Files");
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
			model.add("Production", "production");
			model.add("Beta", "beta");
			model.add("Alpha", "alpha");
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

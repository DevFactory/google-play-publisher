package de.hamm.googleplaypublisher;

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
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GooglePlayPublisher extends Recorder {
	private static final Log LOG = LogFactory.getLog(GooglePlayPublisher.class);
	private final String emailAddress;
	private final String p12File;
	private final String apkFile;
	private final Track track;
	private final List<ReleaseNotes> releaseNotes;

	@DataBoundConstructor
	public GooglePlayPublisher(String emailAddress, String p12File, String apkFile, Track track,
							   List<ReleaseNotes> releaseNotes) {
		this.emailAddress = emailAddress;
		this.p12File = p12File;
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
		PublishHelper publishHelper = new PublishHelper.Builder(logger)
				.setEmailAddress(emailAddress)
				.setP12File(new File(p12File))
				.setApkFilePath(new FilePath(build.getModuleRoot(), apkFile))
				.setTrack(track)
				.setReleaseNotes(releaseNotes)
				.build();
		try {
			publishHelper.publish();
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

	private void expandReleaseNotes(EnvVars envVars) {
		if (releaseNotes != null) {
			for (ReleaseNotes i : releaseNotes) {
				i.expand(envVars);
			}
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public String getP12File() {
		return p12File;
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

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		private static final String EMAIL_PATTERN =
				"^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

		@SuppressWarnings("unused")
		public FormValidation doCheckEmailAddress(@QueryParameter String value) {
			Matcher matcher = Pattern.compile(EMAIL_PATTERN).matcher(value);
			if (matcher.matches()) {
				return FormValidation.ok();
			}
			return FormValidation.error("The provided E-Mail address is not valid!");
		}

		@SuppressWarnings("unused")
		public FormValidation doCheckP12File(@QueryParameter String value) {
			if (value == null || value.isEmpty()) {
				return FormValidation.error("Please specify P12 File.");
			}
			FileInputStream stream;
			try {
				stream = new FileInputStream(new File(value));
			} catch (FileNotFoundException e) {
				return FormValidation.error("File not found!");
			}
			KeyStore keystore;
			try {
				keystore = KeyStore.getInstance("PKCS12");
			} catch (KeyStoreException e) {
				return FormValidation.error("No Keystore Provider found!");
			}
			try {
				keystore.load(stream, "notasecret".toCharArray());
			} catch (IOException e) {
				return FormValidation.error("File is not a valid P12 File!");
			} catch (NoSuchAlgorithmException e) {
				return FormValidation.error("File is not a valid P12 File!");
			} catch (CertificateException e) {
				return FormValidation.error("File is not a valid P12 File!");
			}
			try {
				stream.close();
			} catch (IOException ignored) {
			}
			try {
				keystore.getKey("privatekey", "notasecret".toCharArray());
			} catch (KeyStoreException ignored) {
			} catch (NoSuchAlgorithmException ignored) {
			} catch (UnrecoverableKeyException e) {
				return FormValidation.error("Key cannot be recovered!");
			}
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Publish on Google play";
		}
	}
}

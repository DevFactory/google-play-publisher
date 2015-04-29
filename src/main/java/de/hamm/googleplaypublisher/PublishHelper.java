package de.hamm.googleplaypublisher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.ApkListing;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.Track;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.FilePath;
import net.erdfelt.android.apk.AndroidApk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PublishHelper {
	private static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final String APPLICATION_NAME = "de.hamm.googleplaypublisher";
	private final JsonFactory jsonFactory = new JacksonFactory();
	private final HttpTransport httpTransport;
	private final PrintStream logger;
	private final GoogleRobotCredentials credentials;
	private final FilePath apkFilePath;
	private final de.hamm.googleplaypublisher.Track track;
	private final List<ReleaseNotes> releaseNotes;
	private String packageName;
	private AndroidPublisher.Edits edits;
	private String appEditId;

	private PublishHelper(PrintStream logger, GoogleRobotCredentials credentials, FilePath apkFilePath,
						  de.hamm.googleplaypublisher.Track track, List<ReleaseNotes> releaseNotes)
			throws ReadPackageNameException, PublishApkException {
		this.logger = logger;
		this.credentials = credentials;
		this.apkFilePath = apkFilePath;
		this.track = track;
		this.releaseNotes = releaseNotes;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException e) {
			throw new PublishApkException("Failed to create new Trusted Transport", e);
		} catch (IOException e) {
			throw new PublishApkException("Failed to create new Trusted Transport", e);
		}
		try {
			AndroidApk androidApk = new AndroidApk(apkFilePath.read());
			packageName = androidApk.getPackageName();
		} catch (FileNotFoundException e) {
			throw new ReadPackageNameException(
					String.format("Could not find file '%s'", apkFilePath), e);
		} catch (IOException e) {
			throw new ReadPackageNameException(
					String.format("Failed to read package name from file '%s'", apkFilePath), e);
		}
	}

	public void publish() throws PublishApkException {
		createAndroidPublisherEdits();
		createAppEdit();
		final Apk apk = uploadApk();
		updateTracks(apk.getVersionCode());
		publishAllReleaseNotes(apk.getVersionCode());
		commitAppEdit();
	}

	private void createAndroidPublisherEdits() throws PublishApkException {
		try {
			final Credential credential = credentials.getGoogleCredential(new AndroidPublisherScopeRequirement());
			edits = new AndroidPublisher.Builder(httpTransport, jsonFactory, credential)
					.setApplicationName(APPLICATION_NAME)
					.build()
					.edits();
		} catch (GeneralSecurityException e) {
			throw new PublishApkException("Failed to create Android Publisher Edits", e);
		}
	}

	private void createAppEdit() throws PublishApkException {
		try {
			final AppEdit appEdit = edits.insert(packageName, null).execute();
			appEditId = appEdit.getId();
			logger.println(String.format("Created App edit with id: %s", appEditId));
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute insert request", e);
		}
	}

	private Apk uploadApk() throws PublishApkException {
		try {
			Apk apk = edits.apks()
					.upload(packageName, appEditId, new InputStreamContent(MIME_TYPE_APK, apkFilePath.read()))
					.execute();
			logger.println(String.format("Version code %d has been uploaded", apk.getVersionCode()));
			return apk;
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute upload request", e);
		}
	}

	private void updateTracks(Integer currentVersionCode) throws PublishApkException {
		publishVersion(currentVersionCode);
		unpublishLowerVersionsInLowerTracks(currentVersionCode);
		unpublishAllVersionsInLowerTracks();
	}

	private void publishVersion(Integer currentVersionCode) throws PublishApkException {
		try {
			Track updatedTrack = edits.tracks().update(packageName, appEditId, track.getName(),
					track.createApiTrack().setVersionCodes(Collections.singletonList(currentVersionCode))).execute();
			logger.println(String.format("Version codes %s have been published in Track '%s'",
					Arrays.toString(updatedTrack.getVersionCodes().toArray()), updatedTrack.getTrack()));
		} catch (IOException e) {
			throw new PublishApkException(String.format("Failed to publish Version codes %s in Track '%s'",
					Arrays.toString(Collections.singletonList(currentVersionCode).toArray()), track.getName()), e);
		}
	}

	private void unpublishLowerVersionsInLowerTracks(Integer currentVersionCode) throws PublishApkException {
		for (String i : track.getTracksWhereToUnpublishLowerVersions()) {
			unpublishLowerVersionsInTrack(currentVersionCode, i);
		}
	}

	private void unpublishAllVersionsInLowerTracks() throws PublishApkException {
		for (String i : track.getTracksWhereToUnpublishAllVersions()) {
			unpublishAllVersionsInTrack(i);
		}
	}

	private void unpublishLowerVersionsInTrack(Integer currentVersionCode, String trackName)
			throws PublishApkException {
		try {
			Track trackToUpdate = edits.tracks().get(packageName, appEditId, trackName).execute();
			List<Integer> versionCodes = trackToUpdate.getVersionCodes();
			if (versionCodes != null) {
				List<Integer> higherVersionCodes = getHigherVersionCodes(currentVersionCode, versionCodes);
				List<Integer> lowerVersionCodes = getLowerVersionCodes(currentVersionCode, versionCodes);
				if (!versionCodes.equals(higherVersionCodes)) {
					try {
						edits.tracks().update(packageName, appEditId, trackName,
								trackToUpdate.setVersionCodes(higherVersionCodes)).execute();
						logger.println(String.format("Version codes %s have been unpublished from Track '%s'",
								Arrays.toString(lowerVersionCodes.toArray()), trackName));
					} catch (IOException e) {
						throw new PublishApkException(
								String.format("Failed to unpublish Version codes %s in Track '%s'",
										Arrays.toString(lowerVersionCodes.toArray()), track.getName()), e);
					}
				}
			}
		} catch (IOException e) {
			// No published versions in track
		}
	}

	private List<Integer> getHigherVersionCodes(Integer currentVersionCode, List<Integer> versionCodes) {
		List<Integer> higherVersionCodes = new ArrayList<Integer>();
		for (Integer i : versionCodes) {
			if (i > currentVersionCode) {
				higherVersionCodes.add(i);
			}
		}
		return higherVersionCodes;
	}

	private List<Integer> getLowerVersionCodes(Integer currentVersionCode, List<Integer> versionCodes) {
		List<Integer> lowerVersionCodes = new ArrayList<Integer>();
		for (Integer i : versionCodes) {
			if (i <= currentVersionCode) {
				lowerVersionCodes.add(i);
			}
		}
		return lowerVersionCodes;
	}

	private void unpublishAllVersionsInTrack(String trackName) throws PublishApkException {
		try {
			Track trackToUpdate = edits.tracks().get(packageName, appEditId, trackName).execute();
			List<Integer> versionCodes = trackToUpdate.getVersionCodes();
			if (versionCodes != null && !versionCodes.isEmpty()) {
				trackToUpdate.setVersionCodes(null);
				try {
					edits.tracks().update(packageName, appEditId, trackName, trackToUpdate).execute();
					logger.println(String.format("Version codes %s have been unpublished from Track '%s'",
							Arrays.toString(versionCodes.toArray()), trackName));
				} catch (IOException e) {
					throw new PublishApkException(String.format("Failed to unpublish Version codes %s in Track '%s'",
							Arrays.toString(versionCodes.toArray()), track.getName()), e);
				}
			}
		} catch (IOException e) {
			// No published versions in track
		}
	}

	private void publishAllReleaseNotes(Integer versionCode) throws PublishApkException {
		if (releaseNotes != null) {
			for (ReleaseNotes i : releaseNotes) {
				publishReleaseNotes(versionCode, i);
			}
		}
	}

	private void publishReleaseNotes(Integer versionCode, ReleaseNotes releaseNotes) throws PublishApkException {
		try {
			edits.apklistings().update(packageName, appEditId, versionCode, releaseNotes.getLanguage(),
					new ApkListing().setLanguage(releaseNotes.getLanguage())
							.setRecentChanges(releaseNotes.getExpandedReleaseNotes())).execute();
			logger.println(String.format("Release Notes in Language '%s' for Version code '%s' have been published",
					releaseNotes.getLanguage(), versionCode));
		} catch (IOException e) {
			throw new PublishApkException(
					String.format("Failed to publish Release Notes in Language '%s' for Version code '%s'",
							releaseNotes.getLanguage(), versionCode), e);
		}
	}

	private void commitAppEdit() throws PublishApkException {
		try {
			AppEdit appEdit = edits.commit(packageName, appEditId).execute();
			logger.println(String.format("App edit with id %s has been comitted", appEdit.getId()));
		} catch (GoogleJsonResponseException e) {
			throw new PublishApkException(
					String.format("Failed to execute commit request. Google play Api Message: '%s'",
							e.getDetails().getMessage()), e);
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute commit request", e);
		}
	}

	public static class PublishApkException extends RuntimeException {
		public PublishApkException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class ReadPackageNameException extends RuntimeException {
		public ReadPackageNameException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static class Builder {
		private PrintStream logger;
		private GoogleRobotCredentials credentials;
		private FilePath apkFilePath;
		private de.hamm.googleplaypublisher.Track track;
		private List<ReleaseNotes> releaseNotes;

		public Builder setLogger(PrintStream logger) {
			this.logger = logger;
			return this;
		}

		public Builder setCredentials(GoogleRobotCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public Builder setApkFilePath(FilePath apkFilePath) {
			this.apkFilePath = apkFilePath;
			return this;
		}

		public Builder setTrack(de.hamm.googleplaypublisher.Track track) {
			this.track = track;
			return this;
		}

		public Builder setReleaseNotes(List<ReleaseNotes> releaseNotes) {
			this.releaseNotes = releaseNotes;
			return this;
		}

		public PublishHelper createPublishHelper() throws ReadPackageNameException {
			return new PublishHelper(logger, credentials, apkFilePath, track, releaseNotes);
		}
	}
}

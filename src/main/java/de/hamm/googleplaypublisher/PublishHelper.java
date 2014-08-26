package de.hamm.googleplaypublisher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.ApkListing;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.Track;
import hudson.FilePath;
import net.erdfelt.android.apk.AndroidApk;

import java.io.File;
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
	private final PrintStream logger;
	private final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
	private final HttpTransport httpTransport;
	private String emailAddress;
	private File p12File;
	private FilePath apkFilePath;
	private de.hamm.googleplaypublisher.Track track;
	private List<ReleaseNotes> releaseNotes;
	private String packageName;
	private AndroidPublisher.Edits edits;
	private String appEditId;

	public PublishHelper(PrintStream logger) throws PublishApkException {
		this.logger = logger;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException e) {
			throw new PublishApkException("Failed to create new Trusted Transport", e);
		} catch (IOException e) {
			throw new PublishApkException("Failed to create new Trusted Transport", e);
		}
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public void setP12File(File p12File) {
		this.p12File = p12File;
	}

	public void setApkFilePath(FilePath apkFilePath) {
		this.apkFilePath = apkFilePath;
		this.packageName = null;
	}

	public void setTrack(de.hamm.googleplaypublisher.Track track) {
		this.track = track;
	}

	private void setReleaseNotes(List<ReleaseNotes> releaseNotes) {
		this.releaseNotes = releaseNotes;
	}

	private String getPackageName() throws ReadPackageNameException {
		if (packageName == null) {
			try {
				AndroidApk androidApk = new AndroidApk(apkFilePath.read());
				packageName = androidApk.getPackageName();
			} catch (IOException e) {
				throw new ReadPackageNameException(
						String.format("Failed to read package name from file '%s'", apkFilePath), e);
			}
		}
		return packageName;
	}

	public void publish() throws ReadPackageNameException, PublishApkException {
		createAndroidPublisherEdits();
		createAppEdit();
		final Apk apk = uploadApk();
		updateTracks(apk.getVersionCode());
		publishAllReleaseNotes(apk.getVersionCode());
		commitAppEdit();
	}

	private void createAndroidPublisherEdits() throws PublishApkException {
		try {
			final Credential credential = new GoogleCredential.Builder()
					.setTransport(httpTransport)
					.setJsonFactory(jsonFactory)
					.setServiceAccountId(emailAddress)
					.setServiceAccountScopes(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
					.setServiceAccountPrivateKeyFromP12File(p12File)
					.build();

			edits = new AndroidPublisher.Builder(httpTransport, jsonFactory, credential)
					.setApplicationName(APPLICATION_NAME)
					.build()
					.edits();
		} catch (GeneralSecurityException e) {
			throw new PublishApkException("Failed to create Android Publisher Edits", e);
		} catch (IOException e) {
			throw new PublishApkException("Failed to create Android Publisher Edits", e);
		}
	}

	private void createAppEdit() throws ReadPackageNameException, PublishApkException {
		try {
			final AppEdit appEdit = edits.insert(getPackageName(), null).execute();
			appEditId = appEdit.getId();
			logger.println(String.format("Created App edit with id: %s", appEditId));
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute insert request", e);
		}
	}

	private Apk uploadApk() throws ReadPackageNameException, PublishApkException {
		try {
			Apk apk = edits.apks()
					.upload(getPackageName(), appEditId, new InputStreamContent(MIME_TYPE_APK, apkFilePath.read()))
					.execute();
			logger.println(String.format("Version code %d has been uploaded", apk.getVersionCode()));
			return apk;
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute upload request", e);
		}
	}

	private void updateTracks(Integer currentVersionCode) throws ReadPackageNameException, PublishApkException {
		publishVersion(currentVersionCode);
		unpublishLowerVersionsInLowerTracks(currentVersionCode);
		unpublishAllVersionsInLowerTracks();
	}

	private void publishVersion(Integer currentVersionCode) throws ReadPackageNameException, PublishApkException {
		try {
			Track updatedTrack = edits.tracks().update(getPackageName(), appEditId, track.getName(),
					track.createApiTrack().setVersionCodes(Arrays.asList(currentVersionCode))).execute();
			logger.println(String.format("Version codes %s have been published in Track '%s'",
					Arrays.toString(updatedTrack.getVersionCodes().toArray()), updatedTrack.getTrack()));
		} catch (IOException e) {
			throw new PublishApkException(String.format("Failed to publish Version codes %s in Track '%s'",
					Arrays.toString(Arrays.asList(currentVersionCode).toArray()), track.getName()), e);
		}
	}

	private void unpublishLowerVersionsInLowerTracks(Integer currentVersionCode)
			throws ReadPackageNameException, PublishApkException {
		for (String i : track.getTracksWhereToUnpublishLowerVersions()) {
			unpublishLowerVersionsInTrack(currentVersionCode, i);
		}
	}

	private void unpublishAllVersionsInLowerTracks()
			throws ReadPackageNameException, PublishApkException {
		for (String i : track.getTracksWhereToUnpublishAllVersions()) {
			unpublishAllVersionsInTrack(i);
		}
	}

	private void unpublishLowerVersionsInTrack(Integer currentVersionCode, String trackName)
			throws ReadPackageNameException, PublishApkException {
		try {
			Track trackToUpdate = edits.tracks().get(getPackageName(), appEditId, trackName).execute();
			List<Integer> versionCodes = trackToUpdate.getVersionCodes();
			if (versionCodes != null) {
				List<Integer> higherVersionCodes = getHigherVersionCodes(currentVersionCode, versionCodes);
				List<Integer> lowerVersionCodes = getLowerVersionCodes(currentVersionCode, versionCodes);
				if (!versionCodes.equals(higherVersionCodes)) {
					try {
						edits.tracks().update(getPackageName(), appEditId, trackName,
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

	private void unpublishAllVersionsInTrack(String trackName) throws ReadPackageNameException, PublishApkException {
		try {
			Track trackToUpdate = edits.tracks().get(getPackageName(), appEditId, trackName).execute();
			List<Integer> versionCodes = trackToUpdate.getVersionCodes();
			if (versionCodes != null && !versionCodes.isEmpty()) {
				trackToUpdate.setVersionCodes(null);
				try {
					edits.tracks().update(getPackageName(), appEditId, trackName, trackToUpdate).execute();
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
		for (ReleaseNotes i : releaseNotes) {
			publishReleaseNotes(versionCode, i);
		}
	}

	private void publishReleaseNotes(Integer versionCode, ReleaseNotes releaseNotes) throws PublishApkException {
		try {
			edits.apklistings().update(getPackageName(), appEditId, versionCode, releaseNotes.getLanguage(),
					new ApkListing().setLanguage(releaseNotes.getLanguage())
							.setRecentChanges(releaseNotes.getReleaseNotes())).execute();
			logger.println(String.format("Release Notes in Language '%s' for Version code '%s' have been published",
					releaseNotes.getLanguage(), versionCode));
		} catch (IOException e) {
			throw new PublishApkException(
					String.format("Failed to publish Release Notes in Language '%s' for Version code '%s'",
							releaseNotes.getLanguage(), versionCode), e);
		}
	}

	private void commitAppEdit() throws ReadPackageNameException, PublishApkException {
		try {
			AppEdit appEdit = edits.commit(getPackageName(), appEditId).execute();
			logger.println(String.format("App edit with id %s has been comitted", appEdit.getId()));
		} catch (GoogleJsonResponseException e) {
			throw new PublishApkException(
					String.format("Failed to execute commit request. Google play Api Message: '%s'",
							e.getDetails().getMessage()), e);
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute commit request", e);
		}
	}

	public static class Builder {
		private final PublishHelper publishHelper;

		public Builder(PrintStream logger) throws PublishApkException {
			publishHelper = new PublishHelper(logger);
		}

		public Builder setEmailAddress(String emailAddress) {
			publishHelper.setEmailAddress(emailAddress);
			return this;
		}

		public Builder setP12File(File p12File) {
			publishHelper.setP12File(p12File);
			return this;
		}

		public Builder setApkFilePath(FilePath apkFilePath) {
			publishHelper.setApkFilePath(apkFilePath);
			return this;
		}

		public Builder setTrack(de.hamm.googleplaypublisher.Track track) {
			publishHelper.setTrack(track);
			return this;
		}

		public Builder setReleaseNotes(List<ReleaseNotes> releaseNotes) {
			publishHelper.setReleaseNotes(releaseNotes);
			return this;
		}

		public PublishHelper build() {
			return publishHelper;
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
}

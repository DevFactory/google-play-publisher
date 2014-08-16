package de.hamm.googleplaypublisher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.Track;
import net.dongliu.apk.parser.ApkParser;
import net.dongliu.apk.parser.exception.ParserException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PublishHelper {
	public static final String TRACK_PRODUCTION = "production";
	public static final String TRACK_BETA = "beta";
	public static final String TRACK_ALPHA = "alpha";

	private static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final String APPLICATION_NAME = "de.hamm.googleplaypublisher";
	private final PrintStream logger;
	private final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
	private final HttpTransport httpTransport;
	private String emailAddress;
	private File p12File;
	private File apkFile;
	private String track;
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

	public void setApkFile(File apkFile) {
		this.apkFile = apkFile;
		this.packageName = null;
	}

	public void setTrack(String track) {
		this.track = track;
	}

	private String getPackageName() throws ReadPackageNameException {
		if (packageName == null) {
			try {
				ApkParser apkParser = new ApkParser(apkFile);
				packageName = apkParser.getApkMeta().getPackageName();
				apkParser.close();
			} catch (IOException e) {
				throw new ReadPackageNameException(String.format("Failed to read file '%s'", apkFile), e);
			} catch (ParserException e) {
				throw new ReadPackageNameException(
						String.format("Failed to read AndroidManifest.xml from file '%s'", apkFile), e);
			}
		}
		return packageName;
	}

	public void publish() throws ReadPackageNameException, PublishApkException {
		createAndroidPublisherEdits();
		createAppEdit();
		final Apk apk = uploadApk();
		updateTracks(apk.getVersionCode());
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
			Apk apk = edits.apks().upload(getPackageName(), appEditId, new FileContent(MIME_TYPE_APK, apkFile))
					.execute();
			logger.println(String.format("Version code %d has been uploaded", apk.getVersionCode()));
			return apk;
		} catch (IOException e) {
			throw new PublishApkException("Failed to execute upload request", e);
		}
	}

	private void updateTracks(Integer currentVersionCode) throws ReadPackageNameException, PublishApkException {
		publishVersionInTrack(currentVersionCode);
		unpublishLowerVersionsInLowerTracks(currentVersionCode);
	}

	private void unpublishLowerVersionsInLowerTracks(Integer currentVersionCode) {
		if (track.equals(TRACK_PRODUCTION)) {
			unpublishLowerVersionsInTrack(currentVersionCode, TRACK_BETA);
		}
		if (track.equals(TRACK_PRODUCTION) || track.equals(TRACK_BETA)) {
			unpublishLowerVersionsInTrack(currentVersionCode, TRACK_ALPHA);
		}
	}

	private void publishVersionInTrack(Integer currentVersionCode) throws ReadPackageNameException,
			PublishApkException {
		try {
			Track updatedTrack = edits.tracks().update(getPackageName(), appEditId, track,
					new Track().setVersionCodes(Arrays.asList(currentVersionCode))).execute();
			logger.println(String.format("Track %s has been updated.", updatedTrack.getTrack()));
		} catch (IOException e) {
			throw new PublishApkException(
					String.format("Failed to execute update track request for track '%s'", track), e);
		}
	}

	private void unpublishLowerVersionsInTrack(Integer currentVersionCode, String trackName)
			throws ReadPackageNameException, PublishApkException {
		try {
			Track trackToUpdate = edits.tracks().get(getPackageName(), appEditId, trackName).execute();
			List<Integer> versionCodes = trackToUpdate.getVersionCodes();
			if (versionCodes != null) {
				List<Integer> higherVersionCodes = getHigherVersionCodes(currentVersionCode, versionCodes);
				if (!versionCodes.equals(higherVersionCodes)) {
					try {
						edits.tracks().update(getPackageName(), appEditId, trackName,
								new Track().setVersionCodes(higherVersionCodes)).execute();
						logger.println(String.format("Track %s has been updated.", trackName));
					} catch (IOException e) {
						throw new PublishApkException(
								String.format("Failed to execute update request for Track %s", trackName), e);
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

	private void commitAppEdit() throws ReadPackageNameException, PublishApkException {
		try {
			AppEdit appEdit = edits.commit(getPackageName(), appEditId).execute();
			logger.println(String.format("App edit with id %s has been comitted", appEdit.getId()));
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

		public Builder setApkFile(File apkFile) {
			publishHelper.setApkFile(apkFile);
			return this;
		}

		public Builder setTrack(String track) {
			publishHelper.setTrack(track);
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
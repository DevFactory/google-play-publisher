package de.hamm.googleplaypublisher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.ApksListResponse;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;

public class NextAvailableVersionCodeFetcherHelper {
	private static final String APPLICATION_NAME = "de.hamm.googleplaypublisher";
	private final JsonFactory jsonFactory = new JacksonFactory();
	private final HttpTransport httpTransport;
	private final PrintStream logger;
	private final GoogleRobotCredentials credentials;
	private final String packageName;
	private AndroidPublisher.Edits edits;
	private String appEditId;

	private NextAvailableVersionCodeFetcherHelper(PrintStream logger, GoogleRobotCredentials credentials,
												  String packageName) throws NextAvailableVersionCodeFetcherException {
		this.logger = logger;
		this.credentials = credentials;
		this.packageName = packageName;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to create new Trusted Transport", e);
		} catch (IOException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to create new Trusted Transport", e);
		}
	}

	public int fetchNextAvailableVersionCode() throws NextAvailableVersionCodeFetcherException {
		createAndroidPublisherEdits();
		createAppEdit();
		int highestVersionCode = getHighestVersionCode();
		deleteAppEdit();
		return highestVersionCode + 1;
	}

	private void createAndroidPublisherEdits() throws NextAvailableVersionCodeFetcherException {
		try {
			final Credential credential = credentials.getGoogleCredential(new AndroidPublisherScopeRequirement());
			edits = new AndroidPublisher.Builder(httpTransport, jsonFactory, credential)
					.setApplicationName(APPLICATION_NAME)
					.build()
					.edits();
		} catch (GeneralSecurityException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to create Android Publisher Edits", e);
		}
	}

	private void createAppEdit() throws NextAvailableVersionCodeFetcherException {
		try {
			final AppEdit appEdit = edits.insert(packageName, null).execute();
			appEditId = appEdit.getId();
			logger.println(String.format("Created App edit with id: %s", appEditId));
		} catch (IOException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to execute App edit insert request", e);
		}
	}

	private int getHighestVersionCode() {
		int highestVersionCode = 1;
		for (Apk i : getApksList().getApks()) {
			if (highestVersionCode < i.getVersionCode()) {
				highestVersionCode = i.getVersionCode();
			}
		}
		return highestVersionCode;
	}

	private ApksListResponse getApksList() throws NextAvailableVersionCodeFetcherException {
		try {
			return edits.apks()
					.list(packageName, appEditId)
					.execute();
		} catch (IOException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to execute list apks request", e);
		}
	}

	private void deleteAppEdit() throws NextAvailableVersionCodeFetcherException {
		try {
			edits.delete(packageName, appEditId).execute();
			logger.println(String.format("App edit with id %s has been deleted", appEditId));
		} catch (GoogleJsonResponseException e) {
			throw new NextAvailableVersionCodeFetcherException(
					String.format("Failed to execute App edit delete request. Google play Api Message: '%s'",
							e.getDetails().getMessage()), e);
		} catch (IOException e) {
			throw new NextAvailableVersionCodeFetcherException("Failed to execute App edit delete request", e);
		}
	}

	public static class Builder {
		private PrintStream logger;
		private GoogleRobotCredentials credentials;
		private String packageName;

		public Builder setLogger(PrintStream logger) {
			this.logger = logger;
			return this;
		}

		public Builder setCredentials(GoogleRobotCredentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public Builder setPackageName(String packageName) {
			this.packageName = packageName;
			return this;
		}

		public NextAvailableVersionCodeFetcherHelper createNextAvailableVersionCodeFetcherHelper() {
			return new NextAvailableVersionCodeFetcherHelper(logger, credentials, packageName);
		}
	}

	public static class NextAvailableVersionCodeFetcherException extends RuntimeException {
		public NextAvailableVersionCodeFetcherException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}

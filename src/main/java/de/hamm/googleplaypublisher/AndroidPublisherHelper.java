/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hamm.googleplaypublisher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Helper class to initialize the publisher APIs client library.
 * <p>
 * Before making any calls to the API through the client library you need to
 * call the {@link AndroidPublisherHelper#init(String)} method. This will run
 * all precondition checks for for client id and secret setup properly in
 * resources/client_secrets.json and authorize this client against the API.
 * </p>
 */
public class AndroidPublisherHelper {
	static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final Log LOG = LogFactory.getLog(AndroidPublisherHelper.class);

	/**
	 * Path to the client secrets file (only used for Installed Application
	 * auth).
	 */
	private static final String RESOURCES_CLIENT_SECRETS_JSON = "/resources/client_secrets.json";

	/**
	 * Directory to store user credentials (only for Installed Application
	 * auth).
	 */
	private static final String DATA_STORE_SYSTEM_PROPERTY = "user.home";
	private static final String DATA_STORE_FILE = ".store/android_publisher_api";
	private static final File DATA_STORE_DIR =
			new File(System.getProperty(DATA_STORE_SYSTEM_PROPERTY), DATA_STORE_FILE);

	/**
	 * Global instance of the JSON factory.
	 */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	/**
	 * Installed application user ID.
	 */
	private static final String INST_APP_USER_ID = "user";
	/**
	 * Global instance of the HTTP transport.
	 */
	private static HttpTransport HTTP_TRANSPORT;

	private static Credential authorizeWithServiceAccount(String serviceAccountEmail, File p12Key)
			throws GeneralSecurityException, IOException {
		LOG.info(String.format("Authorizing using Service Account: %s", serviceAccountEmail));

		// Build service account credential.
		return new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(serviceAccountEmail)
				.setServiceAccountScopes(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
				.setServiceAccountPrivateKeyFromP12File(p12Key)
				.build();
	}

	/**
	 * Authorizes the installed application to access user's protected data.
	 *
	 * @throws IOException
	 */
	private static Credential authorizeWithInstalledApplication() throws IOException {
		LOG.info("Authorizing using installed application");

		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader(
						AndroidPublisherHelper.class.getResourceAsStream(RESOURCES_CLIENT_SECRETS_JSON)));
		// Ensure file has been filled out.
		checkClientSecretsFile(clientSecrets);

		/*
		 * Global instance of the {@link FileDataStoreFactory}. The best practice is to
		 * make it a single globally shared instance across your application.
		 */
		FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
				.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(INST_APP_USER_ID);
	}

	/**
	 * Ensure the client secrets file has been filled out.
	 *
	 * @param clientSecrets the GoogleClientSecrets containing data from the file
	 */
	private static void checkClientSecretsFile(GoogleClientSecrets clientSecrets) {
		if (clientSecrets.getDetails().getClientId().startsWith("[[INSERT")
				|| clientSecrets.getDetails().getClientSecret().startsWith("[[INSERT")) {
			LOG.error("Enter Client ID and Secret from APIs console into resources/client_secrets.json.");
			System.exit(1);
		}
	}

	/**
	 * Performs all necessary setup steps for running requests against the API
	 * using the Installed Application auth method.
	 *
	 * @param applicationName the name of the application: com.example.app
	 * @return the {@link com.google.api.services.androidpublisher.AndroidPublisher} service
	 */
	protected static AndroidPublisher init(String applicationName) throws Exception {
		return init(applicationName, null, null);
	}

	/**
	 * Performs all necessary setup steps for running requests against the API.
	 *
	 * @param applicationName            the name of the application: com.example.app
	 * @param serviceAccountEmailAddress the Service Account Email (empty if using
	 *                                   oauth client)
	 * @return the {@link com.google.api.services.androidpublisher.AndroidPublisher} service
	 * @throws java.security.GeneralSecurityException
	 * @throws java.io.IOException
	 */
	protected static AndroidPublisher init(String applicationName, @Nullable String serviceAccountEmailAddress,
										   @Nullable File p12Key) throws IOException, GeneralSecurityException {
		// Authorization.
		newTrustedTransport();
		Credential credential;
		if (serviceAccountEmailAddress == null || serviceAccountEmailAddress.isEmpty() || p12Key == null) {
			credential = authorizeWithInstalledApplication();
		} else {
			credential = authorizeWithServiceAccount(serviceAccountEmailAddress, p12Key);
		}

		// Set up and return API client.
		return new AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(applicationName).build();
	}

	private static void newTrustedTransport() throws GeneralSecurityException, IOException {
		if (null == HTTP_TRANSPORT) {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		}
	}
}

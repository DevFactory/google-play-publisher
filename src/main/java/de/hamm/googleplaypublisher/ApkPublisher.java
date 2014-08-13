package de.hamm.googleplaypublisher;

import com.google.api.client.http.FileContent;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.Apk;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TracksListResponse;
import net.dongliu.apk.parser.ApkParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApkPublisher {
	private static final Log LOG = LogFactory.getLog(AndroidPublisherHelper.class);
	private static final String TRACK_PRODUCTION = "production";
	private static final String TRACK_BETA = "beta";
	private static final String TRACK_ALPHA = "alpha";
	private final PrintStream logger;
	private final String serviceAccountEmailAddress;
	private final File serviceAccountP12Key;
	private final File apkFile;
	private final String track;
	private final String packageName;

	public ApkPublisher(PrintStream logger, String serviceAccountEmailAddress, File serviceAccountP12Key, File apkFile,
						String track) throws PublishApkException {
		this.logger = logger;
		this.serviceAccountEmailAddress = serviceAccountEmailAddress;
		this.serviceAccountP12Key = serviceAccountP12Key;
		this.apkFile = apkFile;
		this.track = track;
		try {
			packageName = getPackageName(apkFile);
		} catch (IOException e) {
			logger.println(
					String.format("Failed to retrieve Package Name from apk '%s'. See log for details.", apkFile));
			LOG.error(String.format("Failed to retrieve Package Name from apk '%s'", apkFile), e);
			throw new PublishApkException();
		}
	}

	private String getPackageName(File apkFile) throws IOException {
		ApkParser apkParser = new ApkParser(apkFile);
		String packageName = apkParser.getApkMeta().getPackageName();
		apkParser.close();
		return packageName;
	}

	public void publish() {
		final AndroidPublisher.Edits edits = createApiService();
		final String editId = createNewAppEdit(edits);
		Apk apk = uploadApk(edits, editId);
		updateTracks(edits, editId, apk);
		commitAppEdit(edits, editId);
	}

	private AndroidPublisher.Edits createApiService() throws PublishApkException {
		try {
			AndroidPublisher service =
					AndroidPublisherHelper.init(null, serviceAccountEmailAddress, serviceAccountP12Key);
			return service.edits();
		} catch (IOException e) {
			logger.println("Failed to initialise Android Publishing API. See log for details.");
			LOG.error("Failed to initialise Android Publishing API", e);
			throw new PublishApkException();
		} catch (GeneralSecurityException e) {
			logger.println("Failed to initialise Android Publishing API. See log for details.");
			LOG.error("Failed to initialise Android Publishing API", e);
			throw new PublishApkException();
		}
	}

	private String createNewAppEdit(AndroidPublisher.Edits edits) throws PublishApkException {
		try {
			AndroidPublisher.Edits.Insert editRequest = edits.insert(packageName, null);
			AppEdit appEdit = editRequest.execute();
			final String editId = appEdit.getId();
			logger.println(String.format("Created edit with id: %s", editId));
			return editId;
		} catch (IOException e) {
			logger.println("Failed to execute edit request. See log for details.");
			LOG.error("Failed to execute edit request", e);
			throw new PublishApkException();
		}
	}

	private Apk uploadApk(AndroidPublisher.Edits edits, String editId) throws PublishApkException {
		try {
			AndroidPublisher.Edits.Apks.Upload uploadRequest = edits.apks()
					.upload(packageName, editId, new FileContent(AndroidPublisherHelper.MIME_TYPE_APK, apkFile));
			Apk apk = uploadRequest.execute();
			logger.println(String.format("Version code %d has been uploaded", apk.getVersionCode()));
			return apk;
		} catch (IOException e) {
			logger.println("Failed to execute upload request. See log for details.");
			LOG.error("Failed to execute upload request", e);
			throw new PublishApkException();
		}
	}

	private void updateTracks(AndroidPublisher.Edits edits, String editId, Apk apk) throws PublishApkException {
		publishApkInTrack(edits, editId, apk);
		if (track.equals(TRACK_PRODUCTION)) {
			unpublishLowerApksInTrack(edits, editId, apk.getVersionCode(), TRACK_BETA);
		}
		if (track.equals(TRACK_PRODUCTION) || track.equals(TRACK_BETA)) {
			unpublishLowerApksInTrack(edits, editId, apk.getVersionCode(), TRACK_ALPHA);
		}
	}

	private void publishApkInTrack(AndroidPublisher.Edits edits, String editId, Apk apk) throws PublishApkException {
		try {
			Track updatedTrack = edits.tracks().update(packageName, editId, track,
					new Track().setVersionCodes(Arrays.asList(apk.getVersionCode()))).execute();
			logger.println(String.format("Track %s has been updated.", updatedTrack.getTrack()));
		} catch (IOException e) {
			logger.println("Failed to execute update track request. See log for details.");
			LOG.error("Failed to execute update track request", e);
			throw new PublishApkException();
		}
	}

	private void unpublishLowerApksInTrack(AndroidPublisher.Edits edits, String editId, Integer versionCode,
										   String trackName) throws PublishApkException {
		try {
			TracksListResponse tracksList = edits.tracks().list(packageName, editId).execute();
			Track track = getTrack(trackName, tracksList);
			if (track != null) {
				List<Integer> versionCodesToStayPublished = new ArrayList<Integer>();
				for (int i : track.getVersionCodes()) {
					if (i > versionCode) {
						versionCodesToStayPublished.add(i);
					}
				}
				if (!track.getVersionCodes().equals(versionCodesToStayPublished)) {
					edits.tracks().update(packageName, editId, trackName,
							new Track().setVersionCodes(versionCodesToStayPublished)).execute();
					logger.println(String.format("Track %s has been updated.", trackName));
				}
			}
		} catch (IOException e) {
			logger.println(
					String.format("Failed to execute update request for Track %s. See log for details.", trackName));
			LOG.error(String.format("Failed to execute update request for Track %s", trackName), e);
			throw new PublishApkException();
		}
	}

	private Track getTrack(String trackName, TracksListResponse tracksList) {
		for (Track i : tracksList.getTracks()) {
			if (i.getTrack().equals(trackName)) {
				return i;
			}
		}
		return null;
	}

	private void commitAppEdit(AndroidPublisher.Edits edits, String editId) throws PublishApkException {
		try {
			AndroidPublisher.Edits.Commit commitRequest = edits.commit(packageName, editId);
			AppEdit appEdit = commitRequest.execute();
			logger.println(String.format("App edit with id %s has been comitted", appEdit.getId()));
		} catch (IOException e) {
			logger.println("Failed to execute commit request. See log for details.");
			LOG.error("Failed to execute commit request", e);
			throw new PublishApkException();
		}
	}

	public static class PublishApkException extends RuntimeException {
	}
}

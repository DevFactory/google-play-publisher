package de.hamm.googleplaypublisher;

import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

@RequiresDomain(value = AndroidPublisherScopeRequirement.class)
public class GooglePlayNextAvailableVersionCodeFetcher extends BuildWrapper {
	private static final Log LOG = LogFactory.getLog(GooglePlayNextAvailableVersionCodeFetcher.class);
	private final String credentialId;
	private final String packageName;
	private final String environmentVariable;

	@DataBoundConstructor
	public GooglePlayNextAvailableVersionCodeFetcher(String credentialId, String packageName,
													 String environmentVariable) {
		this.credentialId = credentialId;
		this.packageName = packageName;
		this.environmentVariable = environmentVariable;
	}

	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
		PrintStream logger = listener.getLogger();
		logger.println("[Google play Publisher] - Fetching Next available Versioncode");
		NextAvailableVersionCodeFetcherHelper nextAvailableVersionCodeFetcherHelper =
				new NextAvailableVersionCodeFetcherHelper.Builder()
						.setLogger(logger)
						.setCredentials(GoogleRobotCredentials.getById(credentialId))
						.setPackageName(packageName)
						.createNextAvailableVersionCodeFetcherHelper();
		try {
			int nextAvailableVersionCode = nextAvailableVersionCodeFetcherHelper.fetchNextAvailableVersionCode();
			logger.println(String.format("[Google play Publisher] - Next Available Versioncode is %d",
					nextAvailableVersionCode));
			return new NextAvailableVersionCodeFetcherEnvironment(nextAvailableVersionCode);
		} catch (NextAvailableVersionCodeFetcherHelper.NextAvailableVersionCodeFetcherException e) {
			logger.println("[Google play Publisher] - " + e.getMessage());
			LOG.error(e.getMessage(), e);
			build.setResult(Result.FAILURE);
			return null;
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public String getCredentialId() {
		return credentialId;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getEnvironmentVariable() {
		return environmentVariable;
	}

	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		@Override
		public boolean isApplicable(AbstractProject<?, ?> abstractProject) {
			return true;
		}

		public String getDisplayName() {
			return "Fetch Next available Versioncode from Google play";
		}
	}

	private class NextAvailableVersionCodeFetcherEnvironment extends Environment {
		private final int versionCode;

		public NextAvailableVersionCodeFetcherEnvironment(int versionCode) {
			this.versionCode = versionCode;
		}

		@Override
		public void buildEnvVars(Map<String, String> env) {
			env.put(environmentVariable, Integer.toString(versionCode));
		}
	}
}

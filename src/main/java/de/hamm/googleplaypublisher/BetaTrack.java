package de.hamm.googleplaypublisher;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BetaTrack extends Track {
	public static final String NAME = "beta";

	@DataBoundConstructor
	public BetaTrack() {
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public com.google.api.services.androidpublisher.model.Track createApiTrack() {
		return new com.google.api.services.androidpublisher.model.Track();
	}

	@Override
	public List<String> getTracksWhereToUnpublishLowerVersions() {
		return Arrays.asList(AlphaTrack.NAME);
	}

	@Override
	public List<String> getTracksWhereToUnpublishAllVersions() {
		return Collections.emptyList();
	}

	@Override
	public Descriptor<Track> getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
	}

	@Extension
	public static final class DescriptorImpl extends Track.DescriptorImpl {
		@Override
		public String getDisplayName() {
			return "Beta";
		}
	}
}

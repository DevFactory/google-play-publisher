package de.hamm.googleplaypublisher;

import hudson.Extension;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

public class AlphaTrack extends Track {
	public static final String NAME = "alpha";

	@DataBoundConstructor
	public AlphaTrack() {
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
		return Collections.emptyList();
	}

	@Override
	public List<String> getTracksWhereToUnpublishAllVersions() {
		return Collections.emptyList();
	}

	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
	}

	@Extension
	public static final class DescriptorImpl extends Track.DescriptorImpl {
		@Override
		public String getDisplayName() {
			return "Alpha";
		}
	}
}

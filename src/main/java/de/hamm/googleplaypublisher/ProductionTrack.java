package de.hamm.googleplaypublisher;

import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProductionTrack extends Track {
	public static final String NAME_PRODUCTION = "production";
	public static final String NAME_ROLLOUT = "rollout";
	private final StagedRollout stagedRollout;

	@DataBoundConstructor
	public ProductionTrack(StagedRollout stagedRollout) {
		this.stagedRollout = stagedRollout;
	}

	public StagedRollout getStagedRollout() {
		return stagedRollout;
	}

	@Override
	public String getName() {
		if (stagedRollout == null) {
			return NAME_PRODUCTION;
		}
		return NAME_ROLLOUT;
	}

	@Override
	public com.google.api.services.androidpublisher.model.Track createApiTrack() {
		com.google.api.services.androidpublisher.model.Track track =
				new com.google.api.services.androidpublisher.model.Track().setTrack(getName());
		if (stagedRollout != null) {
			track.setUserFraction(stagedRollout.getQuota());
		}
		return track;
	}

	@Override
	public List<String> getTracksWhereToUnpublishLowerVersions() {
		return Arrays.asList(BetaTrack.NAME, AlphaTrack.NAME);
	}

	@Override
	public List<String> getTracksWhereToUnpublishAllVersions() {
		if (stagedRollout == null) {
			return Arrays.asList(NAME_ROLLOUT);
		}
		return Collections.emptyList();
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
	}

	@Extension
	public static final class DescriptorImpl extends Track.DescriptorImpl {
		@SuppressWarnings("unused")
		public ListBoxModel doFillQuotaItems() {
			ListBoxModel model = new ListBoxModel();
			model.add("0,5 %", "0.005");
			model.add("1 %", "0.01");
			model.add("5 %", "0.05");
			model.add("10 %", "0.1");
			model.add("20 %", "0.2");
			model.add("50 %", "0.5");
			return model;
		}

		@Override
		public String getDisplayName() {
			return "Production";
		}
	}

	public static class StagedRollout {
		private final double quota;

		@DataBoundConstructor
		public StagedRollout(double quota) {
			this.quota = quota;
		}

		public double getQuota() {
			return quota;
		}
	}
}

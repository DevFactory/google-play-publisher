package de.hamm.googleplaypublisher;

import hudson.model.Describable;
import hudson.model.Descriptor;

import java.io.Serializable;
import java.util.List;

public abstract class Track implements Describable<Track>, Serializable {
	public abstract String getName();

	public abstract com.google.api.services.androidpublisher.model.Track createApiTrack();

	public abstract List<String> getTracksWhereToUnpublishLowerVersions();

	public abstract List<String> getTracksWhereToUnpublishAllVersions();

	public static abstract class DescriptorImpl extends Descriptor<Track> {
	}
}

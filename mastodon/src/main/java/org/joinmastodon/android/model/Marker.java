package org.joinmastodon.android.model;

import com.google.gson.annotations.SerializedName;

import org.joinmastodon.android.api.AllFieldsAreRequired;

import java.time.Instant;

@AllFieldsAreRequired
public class Marker extends BaseModel{
	public String lastReadId;
	public long version;
	public Instant updatedAt;
	public Pleroma pleroma;

	@Override
	public String toString(){
		return "Marker{"+
				"lastReadId='"+lastReadId+'\''+
				", version="+version+
				", updatedAt="+updatedAt+
				'}';
	}

	public enum Type {
		@SerializedName("home")
		HOME,
		@SerializedName("notifications")
		NOTIFICATIONS
	}

	public static class Pleroma{
		public int unreadCount;
	}
}

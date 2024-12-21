package org.joinmastodon.android.api.requests.notifications;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.UnreadNotificationsCount;

public class GetUnreadNotificationsCount extends MastodonAPIRequest<UnreadNotificationsCount>{
	public GetUnreadNotificationsCount(){
		super(HttpMethod.GET, "/notifications/unread_count", UnreadNotificationsCount.class);
	}

	@Override
	protected String getPathPrefix(){
		return "/api/v2";
	}
}

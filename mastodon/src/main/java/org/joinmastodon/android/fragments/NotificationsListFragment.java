package org.joinmastodon.android.fragments;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.markers.GetMarkers;
import org.joinmastodon.android.api.requests.notifications.GetUnreadNotificationsCount;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.EmojiReactionsUpdatedEvent;
import org.joinmastodon.android.events.PollUpdatedEvent;
import org.joinmastodon.android.events.RemoveAccountPostsEvent;
import org.joinmastodon.android.events.StatusCountersUpdatedEvent;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.Marker;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.PaginatedResponse;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.TimelineMarkers;
import org.joinmastodon.android.model.UnreadNotificationsCount;
import org.joinmastodon.android.ui.displayitems.AccountCardStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.EmojiReactionsStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.ExtendedFooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.FooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.NotificationHeaderStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.TextStatusDisplayItem;
import org.joinmastodon.android.ui.utils.DiscoverInfoBannerHelper;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.utils.ObjectIdComparator;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.utils.MergeRecyclerAdapter;

public class NotificationsListFragment extends BaseStatusListFragment<Notification> {
	private boolean onlyMentions;
	private boolean onlyPosts;
	private String maxID;
	private boolean reloadingFromCache, markerLoaded;
	private DiscoverInfoBannerHelper bannerHelper;
	private int accurateUnreadCount=-1;
	private boolean usesUnreadEndpoint, unreadCountLoaded;

	@Override
	protected boolean wantsComposeButton() {
		return false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		E.register(this);
		if(savedInstanceState!=null){
			onlyMentions=savedInstanceState.getBoolean("onlyMentions", false);
			onlyPosts=savedInstanceState.getBoolean("onlyPosts", false);
		}
		if (onlyPosts) {
			bannerHelper=new DiscoverInfoBannerHelper(DiscoverInfoBannerHelper.BannerType.POST_NOTIFICATIONS, accountID);
		}

		Instance instance=getInstance().get();
		if(instance.v2!=null && instance.v2.apiVersions!=null && instance.v2.apiVersions.mastodon>=2 && !instance.isAkkoma())
			usesUnreadEndpoint=true;
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(this);
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		onlyMentions=getArguments().getBoolean("onlyMentions", false);
		onlyPosts=getArguments().getBoolean("onlyPosts", false);
		setTitle(R.string.notifications);
	}

	@Override
	protected List<StatusDisplayItem> buildDisplayItems(Notification n){
		NotificationHeaderStatusDisplayItem titleItem;
		if(n.type==Notification.Type.MENTION || n.type==Notification.Type.STATUS){
			titleItem=null;
		}else{
			titleItem=new NotificationHeaderStatusDisplayItem(n.id, this, n, accountID);
		}
		if (n.type == Notification.Type.FOLLOW_REQUEST) {
			ArrayList<StatusDisplayItem> items = new ArrayList<>();
			items.add(titleItem);
			items.add(new AccountCardStatusDisplayItem(n.id, this, accountID, n.account, n));
			return items;
		}
		if(n.status!=null){
			int flags=titleItem==null ? 0 : (StatusDisplayItem.FLAG_NO_FOOTER | StatusDisplayItem.FLAG_INSET | StatusDisplayItem.FLAG_NO_EMOJI_REACTIONS); // | StatusDisplayItem.FLAG_NO_HEADER);
			if (GlobalUserPreferences.spectatorMode)
				flags |= StatusDisplayItem.FLAG_NO_FOOTER;
			ArrayList<StatusDisplayItem> items=StatusDisplayItem.buildItems(this, n.status, accountID, n, knownAccounts, null, flags);
			if(titleItem!=null)
				items.add(0, titleItem);
			return items;
		}else if(titleItem!=null){
			return Collections.singletonList(titleItem);
		}else{
			return Collections.emptyList();
		}
	}
	@Override
	protected void addAccountToKnown(Notification s){
		if(!knownAccounts.containsKey(s.account.id))
			knownAccounts.put(s.account.id, s.account);
		if(s.status!=null && !knownAccounts.containsKey(s.status.account.id))
			knownAccounts.put(s.status.account.id, s.status.account);
		if(s.status!=null && s.status.reblog!=null && !knownAccounts.containsKey(s.status.reblog.account.id))
			knownAccounts.put(s.status.reblog.account.id, s.status.reblog.account);
	}

	@Override
	protected void doLoadData(int offset, int count){
		if(getParentFragment() instanceof NotificationsFragment nf && nf.savingMarkers){
			nf.refreshAfterSavingMarkers=true;
			return;
		}

		dataLoading=true;

		// notifications content
		AccountSessionManager.getInstance()
				.getAccount(accountID).getCacheController()
				.getNotifications(offset>0 ? maxID : null, count, onlyMentions, onlyPosts, refreshing && !reloadingFromCache, new SimpleCallback<>(this){
					@Override
					public void onSuccess(PaginatedResponse<List<Notification>> result){
						if(getActivity()==null)
							return;
						maxID=result.maxID;
						onDataLoaded(result.items.stream().collect(Collectors.toList()), !result.items.isEmpty());
						if(bannerHelper!=null) bannerHelper.onBannerBecameVisible();
						if((offset>0 || (markerLoaded && (!usesUnreadEndpoint || unreadCountLoaded)) || reloadingFromCache) && !(onlyMentions || onlyPosts)){
							updateUnreadCount();
						}else {
							reloadingFromCache=false;
							if(getParentFragment() instanceof NotificationsFragment nf)
								nf.updateMarkAllReadButton();
						}
					}
				});

		if(offset>0 || reloadingFromCache || onlyMentions || onlyPosts)
			return;

		// mastodon v2 unread count endpoint
		if(usesUnreadEndpoint){
			new GetUnreadNotificationsCount()
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(UnreadNotificationsCount result){
							accurateUnreadCount=result.count;
							getSession().setUnreadNotificationsCount(accurateUnreadCount);
							if(!dataLoading && markerLoaded){
								updateUnreadCount();
							}else{
								unreadCountLoaded=true;
							}
						}

						@Override
						public void onError(ErrorResponse error){}
					})
					.exec(getAccountID());
		}

		// markers, also containing Pleroma unread count
		if(getParentFragment() instanceof NotificationsFragment nf){
			new GetMarkers()
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(TimelineMarkers result){
							if(result.notifications==null || TextUtils.isEmpty(result.notifications.lastReadId))
								return;
							Marker m=result.notifications;
							if(ObjectIdComparator.INSTANCE.compare(m.lastReadId, nf.unreadMarker)>0){
								nf.unreadMarker=m.lastReadId;
								getSession().setNotificationsMarker(nf.unreadMarker);
							}
							if(m.pleroma!=null){
								accurateUnreadCount=m.pleroma.unreadCount;
								getSession().setUnreadNotificationsCount(accurateUnreadCount);
							}
							if(!dataLoading && (!usesUnreadEndpoint || unreadCountLoaded)){
								updateUnreadCount();
							}else{
								markerLoaded=true;
							}
						}

						@Override
						public void onError(ErrorResponse error){}
					})
					.exec(getAccountID());
		}
	}

	public void updateUnreadCount() {
		markerLoaded=false;
		unreadCountLoaded=false;
		if(getParentFragment() instanceof NotificationsFragment nf && nf.getParentFragment() instanceof HomeFragment hf){
			if(accurateUnreadCount!=-1 && !usesUnreadEndpoint)
				hf.updateUnreadCount(accurateUnreadCount, false);
			else
				hf.updateUnreadCount(data, nf.unreadMarker, accurateUnreadCount, usesUnreadEndpoint && (accurateUnreadCount==100)); // 100 is default max so it is unknown if it's higher
			nf.updateMarkAllReadButton();
		}
		if(reloadingFromCache){
			reloadingFromCache=false;
			if(GlobalUserPreferences.loadNewPosts)
				refresh();
		}
	}

	@Override
	protected void onShown(){
		super.onShown();
		accurateUnreadCount=getSession().getLastKnownUnreadNotificationsCount();
		if(!dataLoading){
			if(onlyMentions){
				refresh();
			}else{
				reloadingFromCache=true;
				refresh();
			}
		}
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		resetUnreadBackground();
	}

	@Override
	public void onItemClick(String id){
		Notification n=getNotificationByID(id);
		Bundle args = new Bundle();
		if(n.status != null && n.status.inReplyToAccountId != null && knownAccounts.containsKey(n.status.inReplyToAccountId))
			args.putParcelable("inReplyToAccount", Parcels.wrap(knownAccounts.get(n.status.inReplyToAccountId)));
		UiUtils.showFragmentForNotification(getContext(), n, accountID, args);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		list.addItemDecoration(new RecyclerView.ItemDecoration(){
			private Paint paint=new Paint();
			private Rect tmpRect=new Rect();

			{
				paint.setColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3SurfaceVariant));
			}

			@Override
			public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
				if (getParentFragment() instanceof NotificationsFragment nf) {
					if(TextUtils.isEmpty(nf.unreadMarker))
						return;
					for(int i=0;i<parent.getChildCount();i++){
						View child=parent.getChildAt(i);
						if(parent.getChildViewHolder(child) instanceof StatusDisplayItem.Holder<?> holder){
							String itemID=holder.getItemID();
							if(ObjectIdComparator.INSTANCE.compare(itemID, nf.unreadMarker)>0){
								parent.getDecoratedBoundsWithMargins(child, tmpRect);
								c.drawRect(tmpRect, paint);
							}
						}
					}
				}
			}
		}, 0);
		refreshLayout.setOnRefreshListener(()->{
			if(!onlyMentions && !onlyPosts && getParentFragment() instanceof NotificationsFragment nf){
				nf.markAsRead();
			}
			onRefresh();
		});
	}

	@Override
	protected List<View> getViewsForElevationEffect(){
		if (getParentFragment() instanceof NotificationsFragment nf) {
			ArrayList<View> views=new ArrayList<>(super.getViewsForElevationEffect());
			views.add(nf.tabLayout);
			return views;
		} else {
			return super.getViewsForElevationEffect();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putBoolean("onlyMentions", onlyMentions);
		outState.putBoolean("onlyPosts", onlyPosts);
	}

	private Notification getNotificationByID(String id){
		for(Notification n:data){
			if(n.id.equals(id))
				return n;
		}
		return null;
	}

	@Subscribe
	public void onPollUpdated(PollUpdatedEvent ev){
		if(!ev.accountID.equals(accountID))
			return;
		for(Notification ntf:data){
			if(ntf.status==null)
				continue;
			Status contentStatus=ntf.status.getContentStatus();
			if(contentStatus.poll!=null && contentStatus.poll.id.equals(ev.poll.id)){
				updatePoll(ntf.id, contentStatus, ev.poll);
			}
		}
	}

	// copied from StatusListFragment.EventListener (just like the method above)
	// (which assumes this.data to be a list of statuses...)
	@Subscribe
	public void onStatusCountersUpdated(StatusCountersUpdatedEvent ev){
		for(Notification n:data){
			if(n.status!=null && n.status.getContentStatus().id.equals(ev.id)){
				n.status.getContentStatus().update(ev);
				AccountSessionManager.get(accountID).getCacheController().updateNotification(n);
				for(int i=0;i<list.getChildCount();i++){
					RecyclerView.ViewHolder holder=list.getChildViewHolder(list.getChildAt(i));
					if(holder instanceof FooterStatusDisplayItem.Holder footer && footer.getItem().status==n.status.getContentStatus()){
						footer.rebind();
					}else if(holder instanceof ExtendedFooterStatusDisplayItem.Holder footer && footer.getItem().status==n.status.getContentStatus()){
						footer.rebind();
					}
				}
			}
		}
		for(Notification n:preloadedData){
			if(n.status!=null && n.status.getContentStatus().id.equals(ev.id)){
				n.status.getContentStatus().update(ev);
				AccountSessionManager.get(accountID).getCacheController().updateNotification(n);
			}
		}
	}

	@Subscribe
	public void onEmojiReactionsChanged(EmojiReactionsUpdatedEvent ev){
		for(Notification n : data){
			if(n.status!=null && n.status.getContentStatus().id.equals(ev.id)){
				n.status.getContentStatus().update(ev);
				AccountSessionManager.get(accountID).getCacheController().updateNotification(n);
				for(int i=0; i<list.getChildCount(); i++){
					RecyclerView.ViewHolder holder=list.getChildViewHolder(list.getChildAt(i));
					if(holder instanceof EmojiReactionsStatusDisplayItem.Holder reactions && reactions.getItem().status==n.status.getContentStatus() && ev.viewHolder!=holder){
						reactions.rebind();
					}else if(holder instanceof TextStatusDisplayItem.Holder text && text.getItem().parentID.equals(n.getID())){
						text.rebind();
					}
				}
			}
		}
		for(Notification n : preloadedData){
			if(n.status!=null && n.status.getContentStatus().id.equals(ev.id)){
				n.status.getContentStatus().update(ev);
				AccountSessionManager.get(accountID).getCacheController().updateNotification(n);
			}
		}
	}

	@Subscribe
	public void onRemoveAccountPostsEvent(RemoveAccountPostsEvent ev){
		if(!ev.accountID.equals(accountID) || ev.isUnfollow)
			return;
		List<Notification> toRemove=Stream.concat(data.stream(), preloadedData.stream())
				.filter(n->n.account!=null && n.account.id.equals(ev.postsByAccountID))
				.collect(Collectors.toList());
		for(Notification n:toRemove){
			removeNotification(n);
		}
	}

	public void removeNotification(Notification n){
		data.remove(n);
		preloadedData.remove(n);
		int index=-1;
		for(int i=0;i<displayItems.size();i++){
			if(n.id.equals(displayItems.get(i).parentID)){
				index=i;
				break;
			}
		}
		if(index==-1)
			return;
		int lastIndex;
		for(lastIndex=index;lastIndex<displayItems.size();lastIndex++){
			if(!displayItems.get(lastIndex).parentID.equals(n.id))
				break;
		}
		displayItems.subList(index, lastIndex).clear();
		adapter.notifyItemRangeRemoved(index, lastIndex-index);
	}

	@Override
	protected boolean needDividerForExtraItem(View child, View bottomSibling, RecyclerView.ViewHolder holder, RecyclerView.ViewHolder siblingHolder){
		return super.needDividerForExtraItem(child, bottomSibling, holder, siblingHolder) || (siblingHolder!=null && siblingHolder.getAbsoluteAdapterPosition()>=adapter.getItemCount());
	}

	void resetUnreadBackground(){
		list.invalidate();
	}

	@Override
	public void onRefresh(){
		super.onRefresh();
		resetUnreadBackground();
	}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		if (bannerHelper == null) return super.getAdapter();
		MergeRecyclerAdapter adapter=new MergeRecyclerAdapter();
		bannerHelper.maybeAddBanner(list, adapter);
		adapter.addAdapter(super.getAdapter());
		return adapter;
	}

	@Override
	public Uri getWebUri(Uri.Builder base) {
		return base.path(isInstanceAkkoma()
				? "/users/" + getSession().self.username + "/interactions"
				: "/notifications").build();
	}
}

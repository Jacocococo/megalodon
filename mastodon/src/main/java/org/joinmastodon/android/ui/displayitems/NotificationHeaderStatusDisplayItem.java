package org.joinmastodon.android.ui.displayitems;

import static org.joinmastodon.android.MastodonApp.context;
import static org.joinmastodon.android.ui.utils.UiUtils.generateFormattedString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.BaseStatusListFragment;
import org.joinmastodon.android.fragments.NotificationsListFragment;
import org.joinmastodon.android.fragments.ProfileFragment;
import org.joinmastodon.android.model.Emoji;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.CustomEmojiHelper;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.parceler.Parcels;

import java.util.Collections;

import me.grishka.appkit.Nav;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class NotificationHeaderStatusDisplayItem extends StatusDisplayItem{
	public final Notification notification;
	private ImageLoaderRequest avaRequest, emojiRequest;
	private final String accountID;
	private final CustomEmojiHelper emojiHelper=new CustomEmojiHelper();
	private final CharSequence text;
	private final CharSequence timestamp;

	public NotificationHeaderStatusDisplayItem(String parentID, BaseStatusListFragment parentFragment, Notification notification, String accountID){
		super(parentID, parentFragment);
		this.notification=notification;
		this.accountID=accountID;
		this.timestamp=notification.createdAt==null ? null : UiUtils.formatRelativeTimestamp(context, notification.createdAt);

		if(notification.type==Notification.Type.POLL){
			text=parentFragment.getString(R.string.poll_ended);
		}else if(notification.type==Notification.Type.UNKNOWN){
			text=parentFragment.getString(R.string.sk_unknown_notification);
		}else{
			AccountSession session = AccountSessionManager.get(accountID);
			avaRequest=new UrlImageLoaderRequest(
					TextUtils.isEmpty(notification.account.avatar) ? session.getDefaultAvatarUrl() :
						GlobalUserPreferences.playGifs ? notification.account.avatar : notification.account.avatarStatic,
					V.dp(50), V.dp(50));
			SpannableStringBuilder parsedName=new SpannableStringBuilder(notification.account.getDisplayName());
			HtmlParser.parseCustomEmoji(parsedName, notification.account.emojis);
			String str = parentFragment.getString(switch(notification.type){
				case FOLLOW -> R.string.user_followed_you;
				case FOLLOW_REQUEST -> R.string.user_sent_follow_request;
				case REBLOG -> R.string.notification_boosted;
				case FAVORITE -> R.string.user_favorited;
				case POLL -> R.string.poll_ended;
				case UPDATE -> R.string.sk_post_edited;
				case SIGN_UP -> R.string.sk_signed_up;
				case REPORT -> R.string.sk_reported;
				case REACTION, PLEROMA_EMOJI_REACTION -> R.string.sk_reacted;
				case MOVE -> R.string.sk_account_migrated_to;
				default -> R.string.sk_unknown_notification;
			});

			if(notification.type==Notification.Type.MOVE){
				this.text=generateFormattedString(str, notification.account.acct, notification.target.acct);
			}else{
				this.text=generateFormattedString(str, parsedName);
				emojiHelper.setText(text);
			}

			if(!TextUtils.isEmpty(notification.emoji) && !TextUtils.isEmpty(notification.emojiUrl)){
				emojiRequest=new UrlImageLoaderRequest(notification.emojiUrl, 0, V.dp(28));
			}
		}
	}

	@Override
	public Type getType(){
		return Type.NOTIFICATION_HEADER;
	}

	@Override
	public int getImageCount(){
		return 1 + emojiHelper.getImageCount() + (emojiRequest!=null ? 1 : 0);
	}

	@Override
	public ImageLoaderRequest getImageRequest(int index){
		if(index>emojiHelper.getImageCount())
			return emojiRequest;
		if(index>0)
			return emojiHelper.getImageRequest(index-1);
		return avaRequest;
	}

	public static class Holder extends StatusDisplayItem.Holder<NotificationHeaderStatusDisplayItem> implements ImageLoaderViewHolder{
		private final ImageView icon, avatar, deleteNotification;
		private final TextView text, timestamp, emoji;
		private final int selectableItemBackground;

		public Holder(Activity activity, ViewGroup parent){
			super(activity, R.layout.display_item_notification_header, parent);
			icon=findViewById(R.id.icon);
			avatar=findViewById(R.id.avatar);
			text=findViewById(R.id.text);
			timestamp=findViewById(R.id.timestamp);
			deleteNotification=findViewById(R.id.delete_notification);
			emoji=findViewById(R.id.emoji);

			icon.setOutlineProvider(OutlineProviders.roundedRect(8));
			icon.setClipToOutline(true);
			avatar.setOutlineProvider(OutlineProviders.roundedRect(8));
			avatar.setClipToOutline(true);
			deleteNotification.setOnClickListener(v->UiUtils.confirmDeleteNotification(activity, item.parentFragment.getAccountID(), item.notification, ()->{
				if (item.parentFragment instanceof NotificationsListFragment fragment) {
					fragment.removeNotification(item.notification);
				}
			}));

			itemView.setOnClickListener(this::onItemClick);
			TypedValue outValue = new TypedValue();
			context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
			selectableItemBackground = outValue.resourceId;
		}

		@Override
		public void setImage(int index, Drawable image){
			if(index==0){
				avatar.setImageDrawable(image);
			}else if(index>item.emojiHelper.getImageCount()){
				icon.setImageDrawable(image);
				icon.setClipToOutline(false); // no rounded corners
			}else{
				item.emojiHelper.setImageDrawable(index-1, image);
				text.setText(text.getText());
			}
			if(image instanceof Animatable)
				((Animatable) image).start();
		}

		@Override
		public void clearImage(int index){
			if(index==0){
				avatar.setImageResource(R.drawable.image_placeholder);
			}else if(index>item.emojiHelper.getImageCount()){
				icon.setImageResource(R.drawable.image_placeholder);
				icon.setClipToOutline(true); // rounded corners
			}else{
				ImageLoaderViewHolder.super.clearImage(index);
			}
		}

		@SuppressLint("ResourceType")
		@Override
		public void onBind(NotificationHeaderStatusDisplayItem item){
			text.setText(item.text);
			timestamp.setText(item.timestamp);
			avatar.setVisibility(item.notification.type==Notification.Type.POLL || item.notification.type==Notification.Type.UNKNOWN ? View.GONE : View.VISIBLE);
			icon.setImageResource(switch(item.notification.type){
				case FAVORITE -> GlobalUserPreferences.likeIcon ? R.drawable.ic_fluent_heart_24_filled : R.drawable.ic_fluent_star_24_filled;
				case REBLOG -> R.drawable.ic_fluent_arrow_repeat_all_24_filled;
				case FOLLOW, FOLLOW_REQUEST -> R.drawable.ic_fluent_person_add_24_filled;
				case POLL -> R.drawable.ic_fluent_poll_24_filled;
				case REPORT -> R.drawable.ic_fluent_warning_24_filled;
				case SIGN_UP -> R.drawable.ic_fluent_person_available_24_filled;
				case UPDATE -> R.drawable.ic_fluent_edit_24_filled;
				case REACTION, PLEROMA_EMOJI_REACTION -> R.drawable.ic_fluent_add_24_filled;
				case MOVE -> R.drawable.ic_fluent_luggage_24_filled;
				default -> R.drawable.ic_fluent_question_24_filled;
			});
			if(item.emojiRequest==null){
				if((item.notification.type==Notification.Type.REACTION || item.notification.type==Notification.Type.PLEROMA_EMOJI_REACTION)
						&& !TextUtils.isEmpty(item.notification.emoji)){
					icon.setVisibility(View.GONE);
					emoji.setVisibility(View.VISIBLE);
					emoji.setText(item.notification.emoji);
				}else{
					emoji.setVisibility(View.GONE);
					icon.setVisibility(View.VISIBLE);
					icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
					icon.setImageTintList(ColorStateList.valueOf(UiUtils.getThemeColor(item.parentFragment.getActivity(), switch(item.notification.type){
						case FAVORITE -> GlobalUserPreferences.likeIcon ? R.attr.colorLike : R.attr.colorFavorite;
						case REBLOG -> R.attr.colorBoost;
						case POLL -> R.attr.colorPoll;
						default -> android.R.attr.colorAccent;
					})));
				}
			}else{
				emoji.setVisibility(View.GONE);
				icon.setVisibility(View.VISIBLE);
				icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
				icon.setContentDescription(item.notification.emoji);
				icon.setImageTintList(null);
			}
			deleteNotification.setVisibility(GlobalUserPreferences.enableDeleteNotifications && item.notification != null ? View.VISIBLE : View.GONE);
			itemView.setBackgroundResource(item.notification.type != Notification.Type.POLL
					&& item.notification.type != Notification.Type.REPORT ?
					selectableItemBackground : 0);
			itemView.setClickable(item.notification.type != Notification.Type.POLL && (item.notification.type==Notification.Type.REPORT || item.notification.account!=null));
			itemView.setPaddingRelative(itemView.getPaddingStart(), itemView.getPaddingTop(),
					GlobalUserPreferences.enableDeleteNotifications ? V.dp(4) : V.dp(16), itemView.getPaddingBottom());
		}

		public void onItemClick(View v) {
			if (item.notification.type == Notification.Type.REPORT) {
				UiUtils.showFragmentForNotification(item.parentFragment.getContext(), item.notification, item.accountID, null);
				return;
			}
			Bundle args=new Bundle();
			args.putString("account", item.accountID);
			args.putParcelable("profileAccount",
					Parcels.wrap(item.notification.type==Notification.Type.MOVE ? item.notification.target : item.notification.account));
			Nav.go(item.parentFragment.getActivity(), ProfileFragment.class, args);
		}
	}
}

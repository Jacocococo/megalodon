package org.joinmastodon.android.ui.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CenteredImageSpan extends ImageSpan{
	public CenteredImageSpan(@NonNull Drawable d){
		super(d, ImageSpan.ALIGN_BOTTOM);
	}

	@Override
	public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm){
		Drawable drawable=getDrawable();
		if (fm!=null) {
			Paint.FontMetricsInt paintFm=paint.getFontMetricsInt();
			int fontHeight=paintFm.descent-paintFm.ascent;
			int verticalCenter=paintFm.ascent+(fontHeight/2);
			int imgHeight=drawable.getIntrinsicHeight();

			fm.ascent=fm.top=verticalCenter-imgHeight/2;
			fm.descent=fm.bottom=verticalCenter+imgHeight/2;
		}
		return drawable.getIntrinsicWidth();
	}

	@Override
	public void draw(@NonNull Canvas canvas, CharSequence text,
					 int start, int end, float x, int top, int y, int bottom,
					 @NonNull Paint paint){
		Drawable drawable=getDrawable();
		canvas.save();

		int translateY=bottom-drawable.getIntrinsicHeight();
		canvas.translate(x, translateY);

		drawable.draw(canvas);
		canvas.restore();
	}
}

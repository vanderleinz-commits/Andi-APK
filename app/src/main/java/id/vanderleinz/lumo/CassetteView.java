package id.vanderleinz.lumo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

final class CassetteView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF artworkBounds = new RectF(15, 19, 85, 64);
    private final Path artworkClip = new Path();
    private final Rect bitmapCrop = new Rect();
    private Bitmap artwork;

    CassetteView(Context context) {
        super(context);
        artworkClip.addRoundRect(artworkBounds, 5, 5, Path.Direction.CW);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setArtwork(Bitmap bitmap) {
        if (bitmap == artwork) return;
        artwork = bitmap;
        if (bitmap != null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int cropWidth = Math.min(width, Math.round(height * 70f / 45f));
            int cropHeight = Math.min(height, Math.round(width * 45f / 70f));
            int left = (width - cropWidth) / 2;
            int top = (height - cropHeight) / 2;
            bitmapCrop.set(left, top, left + cropWidth, top + cropHeight);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        canvas.save();
        canvas.scale(getWidth() / 100f, getHeight() / 85f);
        paint.setColor(0xffc49779);
        canvas.drawRoundRect(3, 8, 97, 77, 10, 10, paint);
        paint.setColor(0xff17243b);
        canvas.drawRoundRect(6, 11, 94, 74, 8, 8, paint);
        paint.setColor(0xff715249);
        canvas.drawRoundRect(artworkBounds, 5, 5, paint);
        if (artwork != null && !artwork.isRecycled() && !bitmapCrop.isEmpty()) {
            canvas.save();
            canvas.clipPath(artworkClip);
            canvas.drawBitmap(artwork, bitmapCrop, artworkBounds, paint);
            canvas.restore();
        }
        paint.setColor(0xd91b2335);
        canvas.drawRoundRect(20, 40, 80, 60, 8, 8, paint);
        final boolean motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        float angle = motion ? (SystemClock.uptimeMillis() % 1500L) * 0.24f : 0f;
        drawReel(canvas, 32, angle);
        drawReel(canvas, 68, -angle);
        canvas.restore();
        if (motion && isShown() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(32);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) invalidate();
    }

    private void drawReel(Canvas canvas, float center, float angle) {
        canvas.save();
        canvas.rotate(angle, center, 50);
        paint.setColor(0xffffe5c7);
        canvas.drawCircle(center, 50, 7, paint);
        paint.setColor(0xff4c3433);
        for (int spoke = 0; spoke < 3; spoke++) {
            canvas.drawCircle(center, 46, 1.45f, paint);
            canvas.rotate(120, center, 50);
        }
        canvas.restore();
    }
}

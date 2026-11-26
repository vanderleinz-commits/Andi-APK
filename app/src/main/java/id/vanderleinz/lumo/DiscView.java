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

final class DiscView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF artworkBounds = new RectF(6, 6, 94, 94);
    private final Path artworkClip = new Path();
    private final Rect bitmapCrop = new Rect();
    private Bitmap artwork;
    private Bitmap sourceArtwork;
    private long lastFrame;
    private boolean playing;
    private float angle;

    void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        lastFrame = 0;
        invalidate();
    }

    DiscView(Context context) {
        super(context);
        artworkClip.addCircle(50, 50, 44, Path.Direction.CW);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setArtwork(Bitmap bitmap) {
        if (bitmap == sourceArtwork) return;
        sourceArtwork = bitmap;
        artwork = bitmap;
        if (bitmap != null && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            try {
                artwork = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            } catch (IllegalStateException | IllegalArgumentException error) {
                artwork = null;
            }
        }
        if (bitmap != null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int cropWidth = Math.min(width, height);
            int cropHeight = cropWidth;
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
        float scale = Math.min(getWidth(), getHeight()) / 100f;
        canvas.translate((getWidth() - 100 * scale) / 2, (getHeight() - 100 * scale) / 2);
        canvas.scale(scale, scale);
        boolean animate = playing && android.animation.ValueAnimator.areAnimatorsEnabled()
                && isShown() && getWindowVisibility() == VISIBLE;
        long now = SystemClock.uptimeMillis();
        if (animate && lastFrame != 0) angle = (angle + Math.min(50, now - lastFrame) * 0.08f) % 360f;
        lastFrame = animate ? now : 0;
        canvas.rotate(angle, 50, 50);
        paint.setColor(0xffc49779);
        canvas.drawCircle(50, 50, 49, paint);
        paint.setColor(0xff26395b);
        canvas.drawCircle(50, 50, 45, paint);
        if (artwork != null && !artwork.isRecycled() && !bitmapCrop.isEmpty()) {
            canvas.save();
            canvas.clipPath(artworkClip);
            paint.setColor(0xffffffff);
            canvas.drawBitmap(artwork, bitmapCrop, artworkBounds, paint);
            canvas.restore();
        } else {
            paint.setColor(0xff745760);
            canvas.drawArc(artworkBounds, -40, 80, true, paint);
            canvas.drawArc(artworkBounds, 140, 80, true, paint);
        }
        paint.setColor(0x88ffe5c7);
        canvas.drawArc(9, 9, 91, 91, 217, 24, true, paint);
        paint.setColor(0xffe3baa0);
        canvas.drawCircle(50, 50, 12, paint);
        paint.setColor(0xff202c45);
        canvas.drawCircle(50, 50, 6, paint);
        canvas.restore();
        if (animate) postInvalidateDelayed(32);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) invalidate();
    }

}

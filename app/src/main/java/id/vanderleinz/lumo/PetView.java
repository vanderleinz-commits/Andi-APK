package id.vanderleinz.lumo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

final class PetView extends View {
    private static final float CENTER = 60f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final long born = SystemClock.uptimeMillis();

    private PetStyle style;
    private Drawable body;
    private long lastTouch = born;
    private long reactionUntil;
    private boolean dragging;
    private boolean glasses;
    private boolean floating;
    private boolean staticPreview;
    private int reaction;

    PetView(Context context) {
        super(context);
        setContentDescription("Andi");
        setCharacter(PetStyle.CLASSIC);
    }

    void setCharacter(PetStyle style) {
        if (this.style == style) return;
        this.style = style;
        body = getContext().getDrawable(style.body);
        body.setBounds(0, 0, 120, 120);
        invalidate();
    }

    void setGlasses(boolean enabled) {
        glasses = enabled;
        invalidate();
    }

    void setFloating() {
        floating = true;
        invalidate();
    }

    void setStaticPreview() {
        staticPreview = true;
        invalidate();
    }

    void react() {
        lastTouch = SystemClock.uptimeMillis();
        reaction = (reaction + 1) % 3;
        reactionUntil = lastTouch + 1250;
        invalidate();
    }

    void setDragging(boolean active) {
        dragging = active;
        lastTouch = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        long elapsed = now - born;
        boolean reacting = !staticPreview && now < reactionUntil;
        boolean asleep = !staticPreview && now - lastTouch > 24_000;
        boolean blinking = !staticPreview && elapsed % 4200 > 4050;
        float side = Math.min(getWidth(), getHeight());
        float scale = side / 120f;

        canvas.save();
        canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
        canvas.scale(scale, scale);
        if (floating) {
            canvas.scale(.79f, .79f, CENTER, CENTER);
        } else if (!staticPreview) {
            fill(0x380a1027);
            canvas.drawOval(36, 102, 84, 107, paint);
        }
        if (!staticPreview) {
            float bob = dragging ? -3f : (float) Math.sin(elapsed / 480.0) * (asleep ? 1.4f : 3f);
            canvas.translate(0, bob);
            if (reacting && !dragging) {
                float bounce = Math.max(0, 1f - (now - lastTouch) / 380f);
                canvas.rotate((float) Math.sin((now - lastTouch) / 48.0) * bounce * 8, CENTER, 58);
            }
        }

        body.draw(canvas);
        drawFace(canvas, reacting, asleep, blinking);
        if (glasses) drawGlasses(canvas);
        canvas.restore();

        if (!staticPreview && isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(asleep ? 100 : 40);
        }
    }

    private void drawFace(Canvas canvas, boolean reacting, boolean asleep, boolean blinking) {
        float y = style.eyeY;
        float left = CENTER - style.eyeSpacing;
        float right = CENTER + style.eyeSpacing;
        drawEye(canvas, left, y, reacting, asleep || blinking);
        drawEye(canvas, right, y, reacting, asleep || blinking);

        float mouthY = y + 6f;
        if (asleep || dragging || (reacting && reaction == 2)) {
            fill(style.faceColor);
            canvas.drawOval(57, mouthY - 1, 63, mouthY + 4, paint);
        } else {
            stroke(style.faceColor, 2.2f);
            line.reset();
            line.moveTo(55.5f, mouthY);
            line.quadTo(CENTER, mouthY + (reacting ? 8 : 5.5f), 64.5f, mouthY);
            canvas.drawPath(line, paint);
        }

        fill(style.cheekColor);
        float cheekOffset = style == PetStyle.CLASSIC ? 4.5f : 5f;
        canvas.drawOval(left - cheekOffset - 3.8f, y + 7f, left - cheekOffset + 3.8f, y + 10.5f, paint);
        canvas.drawOval(right + cheekOffset - 3.8f, y + 7f, right + cheekOffset + 3.8f, y + 10.5f, paint);
    }

    private void drawEye(Canvas canvas, float x, float y, boolean reacting, boolean closed) {
        if (closed) {
            stroke(style.faceColor, 2.2f);
            canvas.drawLine(x - 3, y, x + 3, y, paint);
        } else if (reacting && reaction == 1) {
            stroke(style.faceColor, 2.4f);
            line.reset();
            line.moveTo(x - 4, y + 1);
            line.quadTo(x, y - 5, x + 4, y + 1);
            canvas.drawPath(line, paint);
        } else {
            float width = style == PetStyle.CLASSIC ? 3.8f : 3f;
            float height = (style == PetStyle.CLASSIC ? 6 : 3.8f) + (dragging ? 1.5f : 0);
            fill(style.faceColor);
            canvas.drawRoundRect(x - width, y - height, x + width, y + height, width, width, paint);
        }
    }

    private void drawGlasses(Canvas canvas) {
        float y = style.eyeY;
        float width = style == PetStyle.CLASSIC ? 9.2f : 7.7f;
        float left = CENTER - style.eyeSpacing;
        float right = CENTER + style.eyeSpacing;
        int frame = style == PetStyle.CLASSIC ? 0xffbd9f83 : (0x99000000 | (style.faceColor & 0x00ffffff));
        stroke(frame, 1.55f);
        canvas.drawRoundRect(left - width, y - 7.4f, left + width, y + 7.4f, 6f, 6f, paint);
        canvas.drawRoundRect(right - width, y - 7.4f, right + width, y + 7.4f, 6f, 6f, paint);
        line.reset();
        line.moveTo(left + width, y);
        line.quadTo(CENTER, y - 2.5f, right - width, y);
        canvas.drawPath(line, paint);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void stroke(int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }
}

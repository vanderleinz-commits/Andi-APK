package id.vanderleinz.lumo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

final class PetView extends View {
    private static final int CREAM = 0xffffedcb;
    private static final int CARAMEL = 0xffe19b56;
    private static final float CENTER = 60f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final long born = SystemClock.uptimeMillis();

    private PetStyle style;
    private Drawable body;
    private long lastTouch = born;
    private long reactionUntil;
    private boolean dragging;
    private boolean glasses = true;
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
        if (asleep || blinking) {
            stroke(CREAM, 2.2f);
            canvas.drawLine(left - 3.5f, y, left + 3.5f, y, paint);
            canvas.drawLine(right - 3.5f, y, right + 3.5f, y, paint);
        } else {
            boolean smiling = reacting ? reaction == 1 : style == PetStyle.MOCHI || style == PetStyle.ORIGAMI;
            drawEye(canvas, left, y, smiling);
            drawEye(canvas, right, y, smiling);
        }

        float mouthY = y + 8;
        if (asleep || dragging || (reacting && reaction == 2)) {
            fill(CREAM);
            canvas.drawOval(57, mouthY - 2, 63, mouthY + 4, paint);
        } else if (style == PetStyle.RETRO) {
            fill(CREAM);
            canvas.drawRect(56, mouthY, 58, mouthY + 2, paint);
            canvas.drawRect(58, mouthY + 2, 62, mouthY + 4, paint);
            canvas.drawRect(62, mouthY, 64, mouthY + 2, paint);
        } else {
            stroke(CREAM, 2.2f);
            line.reset();
            line.moveTo(56, mouthY);
            line.quadTo(60, mouthY + (reacting ? 9 : 6), 64, mouthY);
            canvas.drawPath(line, paint);
        }
    }

    private void drawEye(Canvas canvas, float x, float y, boolean smiling) {
        if (style == PetStyle.RETRO && !smiling) {
            fill(CREAM);
            canvas.drawRect(x - 3.8f, y - 1.9f, x + 3.8f, y + 1.9f, paint);
            canvas.drawRect(x - 1.9f, y - 3.8f, x + 1.9f, y + 3.8f, paint);
        } else if (smiling) {
            stroke(CREAM, 2.4f);
            line.reset();
            line.moveTo(x - 4, y + 1);
            line.quadTo(x, y - 5, x + 4, y + 1);
            canvas.drawPath(line, paint);
        } else {
            float height = dragging ? 6.2f : 4.7f;
            fill(CREAM);
            canvas.drawRoundRect(x - 2.6f, y - height, x + 2.6f, y + height, 2.6f, 2.6f, paint);
        }
    }

    private void drawGlasses(Canvas canvas) {
        float y = style.eyeY;
        float width = style == PetStyle.MOCHI ? 12f : 10f;
        float left = CENTER - style.eyeSpacing;
        float right = CENTER + style.eyeSpacing;
        stroke(CARAMEL, style == PetStyle.MOCHI ? 3.1f : 2.5f);
        line.reset();
        line.moveTo(left + width, y);
        line.quadTo(CENTER, y - 3.6f, right - width, y);
        canvas.drawPath(line, paint);
        drawLens(canvas, left, y, width);
        drawLens(canvas, right, y, width);
    }

    private void drawLens(Canvas canvas, float x, float y, float width) {
        switch (style) {
            case ORIGAMI:
                line.reset();
                line.moveTo(x - width, y);
                line.lineTo(x - width * .49f, y - 8.4f);
                line.lineTo(x + width * .49f, y - 8.4f);
                line.lineTo(x + width, y);
                line.lineTo(x + width * .49f, y + 8.4f);
                line.lineTo(x - width * .49f, y + 8.4f);
                line.close();
                canvas.drawPath(line, paint);
                break;
            case RETRO:
                canvas.drawRoundRect(x - width, y - 7.5f, x + width, y + 7.5f, 3.2f, 3.2f, paint);
                break;
            default:
                canvas.drawCircle(x, y, width, paint);
        }
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

package id.vanderleinz.lumo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

final class PetView extends View {
    private static final int FACE = 0xff17254b;
    private static final int MINT = 0xffffe5c7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final Path lenses = new Path();
    private final Shader frame = new LinearGradient(38, 45, 84, 72,
            new int[]{0xffffd5a1, 0xffbd845b, 0xffe4b68a}, null, Shader.TileMode.CLAMP);
    private final Path line = new Path();
    private final Shader shell = new LinearGradient(28, 22, 98, 91,
            new int[]{0xff6681aa, 0xff2c4368, 0xff15243f}, null, Shader.TileMode.CLAMP);
    private final long born = SystemClock.uptimeMillis();

    private long lastTouch = born;
    private long reactionUntil;
    private boolean dragging;
    private boolean glasses = true;
    private boolean floating;
    private int reaction;

    PetView(Context context) {
        super(context);
        setContentDescription("Andi");
        body.moveTo(11, 64);
        body.lineTo(34, 33);
        body.quadTo(38, 26, 49, 26);
        body.lineTo(71, 26);
        body.quadTo(82, 26, 86, 33);
        body.lineTo(109, 64);
        body.quadTo(113, 70, 105, 74);
        body.lineTo(77, 86);
        body.quadTo(60, 94, 43, 86);
        body.lineTo(15, 74);
        body.quadTo(7, 70, 11, 64);
        body.close();
        lenses.moveTo(53, 55);
        lenses.quadTo(60, 49, 67, 55);
        lenses.moveTo(27, 52);
        lenses.lineTo(32, 54);
        lenses.moveTo(88, 54);
        lenses.lineTo(93, 52);
    }

    void setGlasses(boolean enabled) {
        glasses = enabled;
        invalidate();
    }

    void setFloating() {
        floating = true;
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
        boolean reacting = now < reactionUntil;
        boolean asleep = now - lastTouch > 24000;
        boolean blinking = elapsed % 4200 > 4050;
        float scale = Math.min(getWidth(), getHeight()) / 120f;

        canvas.save();
        canvas.translate((getWidth() - 120 * scale) / 2, (getHeight() - 120 * scale) / 2);
        canvas.scale(scale, scale);
        if (floating) {
            canvas.scale(.79f, .79f, 60, 59);
        } else {
            fill(0x380a1027);
            canvas.drawOval(33, 97, 87, 103, paint);
        }
        float bob = dragging ? -3f : (float) Math.sin(elapsed / 480.0) * (asleep ? 1.4f : 3f);
        canvas.translate(0, bob);
        if (reacting && !dragging) {
            float bounce = Math.max(0, 1f - (now - lastTouch) / 380f);
            canvas.rotate((float) Math.sin((now - lastTouch) / 48.0) * bounce * 8, 60, 58);
        }

        fill(0xff5766b0);
        canvas.save();
        canvas.translate(0, 4);
        canvas.drawPath(body, paint);
        canvas.restore();
        paint.setShader(shell);
        canvas.drawPath(body, paint);
        paint.setShader(null);

        fill(0xffba8969);
        canvas.drawOval(29, 23, 55, 43, paint);
        canvas.drawOval(65, 23, 91, 43, paint);
        fill(0xffedd0b2);
        canvas.drawOval(34, 28, 50, 38, paint);
        canvas.drawOval(70, 28, 86, 38, paint);

        fill(FACE);
        canvas.drawRoundRect(29, 42, 91, 76, 16, 16, paint);
        fill(0xff758cff);
        canvas.drawRoundRect(48, 33, 72, 37, 2, 2, paint);
        fill(0xffcb9b78);
        canvas.drawRoundRect(16, 65, 25, 69, 2, 2, paint);
        canvas.drawRoundRect(95, 65, 104, 69, 2, 2, paint);
        drawFace(canvas, dragging, reacting, asleep, blinking);
        if (glasses) drawGlasses(canvas);
        canvas.restore();

        if (isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(asleep ? 100 : 40);
        }
    }

    private void drawGlasses(Canvas canvas) {
        fill(0x229ce5f5);
        canvas.drawCircle(43, 56, 10.6f, paint);
        canvas.drawCircle(77, 56, 10.6f, paint);
        stroke(0xff132039, 4.4f);
        canvas.drawPath(lenses, paint);
        canvas.drawCircle(43, 56, 11.4f, paint);
        canvas.drawCircle(77, 56, 11.4f, paint);
        stroke(0xffe4b68a, 2.9f);
        paint.setShader(frame);
        canvas.drawPath(lenses, paint);
        canvas.drawCircle(43, 56, 11.4f, paint);
        canvas.drawCircle(77, 56, 11.4f, paint);
        paint.setShader(null);
        stroke(0xbbfff1d3, 1.1f);
        canvas.drawArc(35, 48, 51, 64, 205, 52, false, paint);
        canvas.drawArc(69, 48, 85, 64, 205, 52, false, paint);
    }

    private void drawFace(Canvas canvas, boolean excited, boolean reacting, boolean asleep,
                          boolean blinking) {
        if (asleep || blinking) {
            stroke(MINT, 2.4f);
            canvas.drawLine(39, 56, 47, 56, paint);
            canvas.drawLine(73, 56, 81, 56, paint);
        } else if (reacting && reaction == 1) {
            happyEye(canvas, 43);
            happyEye(canvas, 77);
        } else {
            fill(MINT);
            float height = excited || (reacting && reaction == 2) ? 9 : 6;
            canvas.drawRoundRect(39, 56 - height, 47, 56 + height, 4, 4, paint);
            canvas.drawRoundRect(73, 56 - height, 81, 56 + height, 4, 4, paint);
        }

        if (asleep || excited || (reacting && reaction == 2)) {
            fill(MINT);
            canvas.drawOval(56, 58, 64, excited ? 69 : 66, paint);
        } else {
            stroke(MINT, 2.4f);
            line.reset();
            line.moveTo(reacting ? 53 : 55, 62);
            line.quadTo(60, reacting ? 72 : 67, reacting ? 67 : 65, 62);
            canvas.drawPath(line, paint);
        }

        fill(reacting ? 0xfff7ad9b : 0x80d99280);
        canvas.drawOval(34, 63, 43, 67, paint);
        canvas.drawOval(77, 63, 86, 67, paint);
    }

    private void happyEye(Canvas canvas, float x) {
        stroke(MINT, 2.8f);
        line.reset();
        line.moveTo(x - 5, 57);
        line.quadTo(x, 49, x + 5, 57);
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
        paint.setColor(color);
    }
}

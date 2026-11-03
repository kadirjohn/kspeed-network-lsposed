package io.github.kadirjohn.kspeed;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A small filled triangle drawn in the text color: pointing down (download) or up (upload).
 * Shared between the real status-bar NetSpeedView and the settings preview card.
 *
 * Optionally draws a soft dark-gray shadow offset behind the triangle, used when the
 * indicator sits on a light status bar with a light custom text color (e.g. yellow),
 * so the mark stays readable. The shadow is drawn only when enabled by the caller —
 * dark status bars keep the clean shadowless look.
 */
public final class ArrowDrawable extends Drawable {
    private final boolean down;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean shadowEnabled;

    public ArrowDrawable(boolean down) {
        this.down = down;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x42000000); // translucent dark gray
    }

    /** Enables/disables the drop shadow (e.g. only on light status bars). */
    public void setShadowEnabled(boolean enabled) {
        if (shadowEnabled != enabled) {
            shadowEnabled = enabled;
            invalidateSelf();
        }
    }

    public void setTint(int color, int alpha) {
        paint.setColor(color);
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return;
        }
        float padX = b.width() * 0.16f;
        float padY = b.height() * 0.22f;
        Path p = new Path();
        if (down) {
            p.moveTo(b.left + padX, b.top + padY);
            p.lineTo(b.right - padX, b.top + padY);
            p.lineTo(b.exactCenterX(), b.bottom - padY);
        } else {
            p.moveTo(b.left + padX, b.bottom - padY);
            p.lineTo(b.right - padX, b.bottom - padY);
            p.lineTo(b.exactCenterX(), b.top + padY);
        }
        p.close();
        if (shadowEnabled) {
            float off = Math.max(1f, b.height() * 0.09f);
            canvas.save();
            canvas.translate(off * 0.7f, off);
            canvas.drawPath(p, shadowPaint);
            canvas.restore();
        }
        canvas.drawPath(p, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}


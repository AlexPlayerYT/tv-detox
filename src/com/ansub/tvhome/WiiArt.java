package com.ansub.tvhome;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

// Everything the "Wii" wallpaper draws itself, so it needs no image files: the blue ribbon and the grey
// plates in the bottom corners (painted once into the wallpaper picture), the light grey tiles, the round
// buttons, the page arrows, the gear, the bell and the envelope. It copies the look of a menu, not any
// logo, font or picture from it. Sizes are in the same 1920x1080 design pixels as MainActivity; the caller
// scales them.
final class WiiArt {
    static final int BLUE = 0xFF3F86F5;      // the ribbon, the arrow, the rings and the icons
    static final int BLUE_DEEP = 0xFF1F4FB0; // a focused ring, a tile being moved

    // The bottom of the page, from a measured 1080-pixel-wide reference (design pixels, 1920x1080).
    static final int RIBBON_Y = 905;         // where the ribbon is lowest, in the middle
    static final int PLATE_TOP = 834, PLATE_BOTTOM = 1047, PLATE_END = 284, PLATE_R = 68;
    private static final float K = 1920f / 1080f;

    // The ribbon, half of it (the other half is its mirror): x, then where its upper and its lower edge are,
    // read off the reference at that x. It starts thin at the screen edge, swells over the shoulder,
    // pinches, then runs down as a band that thins to a point in the middle.
    private static final float[] RX = {0, 60, 120, 180, 210, 240, 270, 300, 330, 360, 420, 480, 540};
    private static final float[] RU = {441, 438, 436, 441, 451, 467, 476, 479, 483, 486, 494, 501, 506};
    private static final float[] RL = {444, 450, 457, 464, 467, 471, 485, 495, 499, 502, 505, 507, 508};

    private WiiArt() {}

    // The bottom of the page: a blue ribbon that dips to a point in the middle (where the clock sits above it
    // and the date below), and a grey plate in each corner behind the round buttons. Drawn into the wallpaper
    // bitmap, so it costs no view and no memory of its own. s is device pixels per design pixel.
    static void drawBar(Canvas c, int w, int h, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // The corner plates, with a soft shadow to the bottom right (blurred once, here, on a plain bitmap).
        RectF left = new RectF(-300 * s, PLATE_TOP * s, PLATE_END * s, PLATE_BOTTOM * s);
        RectF right = new RectF((1920 - PLATE_END) * s, PLATE_TOP * s, (1920 + 300) * s, PLATE_BOTTOM * s);
        p.setColor(0xA0707279);
        p.setMaskFilter(new BlurMaskFilter(7 * s, BlurMaskFilter.Blur.NORMAL));
        for (RectF r : new RectF[] {left, right}) {
            c.save();
            c.translate(5 * s, 8 * s);
            c.drawRoundRect(r, PLATE_R * s, PLATE_R * s, p);
            c.restore();
        }
        p.setMaskFilter(null);
        p.setShader(new LinearGradient(0, PLATE_TOP * s, 0, PLATE_BOTTOM * s, 0xFFE0E1E3, 0xFFD3D4D8, Shader.TileMode.CLAMP));
        c.drawRoundRect(left, PLATE_R * s, PLATE_R * s, p);
        c.drawRoundRect(right, PLATE_R * s, PLATE_R * s, p);
        p.setShader(null);

        // The ribbon: 25 points along its upper edge, left to right, and along its lower edge, then joined
        // with smooth curves.
        int n = RX.length, total = 2 * n - 1;
        float[] ux = new float[total], uy = new float[total], lx = new float[total], ly = new float[total];
        for (int i = 0; i < total; i++) {
            boolean mirrored = i >= n;
            int k = mirrored ? total - 1 - i : i;
            float x = mirrored ? 1080 - RX[k] : RX[k];
            ux[i] = x * K * s;
            lx[i] = x * K * s;
            uy[i] = (RIBBON_Y + (RU[k] - 506) * K) * s;
            ly[i] = (RIBBON_Y + (RL[k] - 506) * K) * s;
        }
        Path ribbon = new Path();
        smooth(ribbon, ux, uy, true);
        reverse(lx);
        reverse(ly);
        smooth(ribbon, lx, ly, false);
        ribbon.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(BLUE);
        c.drawPath(ribbon, p);
    }

    private static void reverse(float[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            float t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    // A smooth curve through the points (Catmull-Rom turned into Bezier pieces), joined on from where the path is.
    private static void smooth(Path path, float[] xs, float[] ys, boolean start) {
        int n = xs.length;
        if (start) path.moveTo(xs[0], ys[0]);
        else path.lineTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            int a = Math.max(i - 1, 0), b = Math.min(i + 2, n - 1);
            path.cubicTo(xs[i] + (xs[i + 1] - xs[a]) / 6, ys[i] + (ys[i + 1] - ys[a]) / 6,
                    xs[i + 1] - (xs[b] - xs[i]) / 6, ys[i + 1] - (ys[b] - ys[i]) / 6, xs[i + 1], ys[i + 1]);
        }
    }

    // A shape that can be lit. focus goes from 0 (resting) to 1 (focused) and the shape is drawn between the
    // two, from a few fixed paints, so a focus change makes no objects per frame. The shape (the face) fills
    // the view except for `margin` on the right and bottom, which is room for its drop shadow: the shadow
    // falls down and to the right, and grows as the shape is lit, which is how it looks raised.
    abstract static class Shape extends Drawable {
        float focus;
        final float margin;
        final RectF face = new RectF();
        private final RectF r = new RectF();
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);

        Shape(float margin) {
            this.margin = margin;
        }

        @Override protected void onBoundsChange(Rect b) {
            face.set(b.left, b.top, b.right - margin, b.bottom - margin);
            faceChanged();
        }

        void faceChanged() {}

        void setFocus(float f) {
            focus = f;
            invalidateSelf();
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        static int alpha(float f) { return Math.max(0, Math.min(255, Math.round(255 * f))); }

        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

        // Four stacked translucent copies of the face, offset down and right, each bigger and paler than
        // the one before: a soft edge without a blur (a blur is not available when a view is drawn).
        void shadow(Canvas c, float radius, boolean oval) {
            float dx = margin * lerp(0.28f, 0.52f, focus), dy = margin * lerp(0.35f, 0.58f, focus);
            float spread = margin * lerp(0.28f, 0.40f, focus);
            int[] a = {0x16, 0x1E, 0x28, 0x34};
            for (int i = 0; i < 4; i++) {
                float e = spread * (3 - i) / 3f;
                shade.setColor((a[i] << 24) | 0x6A6D76);
                r.set(face.left + dx - e, face.top + dy - e, face.right + dx + e, face.bottom + dy + e);
                if (oval) c.drawOval(r, shade);
                else c.drawRoundRect(r, radius + e, radius + e, shade);
            }
        }
    }

    // A rounded tile: light grey, a little lighter in the middle than at the edges. Focused, it turns white
    // with a blue edge; being moved, it gets a thick dark blue one.
    static final class Tile extends Shape {
        private final float radius, stroke;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF edge = new RectF();
        private Shader rest, lit;
        private boolean moving;

        Tile(float radius, float stroke, float margin) {
            super(margin);
            this.radius = radius;
            this.stroke = stroke;
            ring.setStyle(Paint.Style.STROKE);
        }

        void setMoving(boolean moving) {
            this.moving = moving;
            invalidateSelf();
        }

        @Override void faceChanged() {
            rest = new LinearGradient(0, face.top, 0, face.bottom, new int[] {0xFFDCDBDD, 0xFFEAEAEA, 0xFFDEDEDF},
                    new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            lit = new LinearGradient(0, face.top, 0, face.bottom, 0xFFFFFFFF, 0xFFF4F7FD, Shader.TileMode.CLAMP);
        }

        @Override public void draw(Canvas c) {
            shadow(c, radius, false);
            fill.setShader(rest);
            fill.setAlpha(255);
            c.drawRoundRect(face, radius, radius, fill);
            if (focus > 0f) {
                fill.setShader(lit);
                fill.setAlpha(alpha(focus));
                c.drawRoundRect(face, radius, radius, fill);
            }
            if (focus > 0f || moving) {
                float w = moving ? stroke * 1.5f : stroke;
                edge.set(face.left + w / 2, face.top + w / 2, face.right - w / 2, face.bottom - w / 2);
                ring.setStrokeWidth(w);
                ring.setColor(moving ? BLUE_DEEP : BLUE);
                ring.setAlpha(moving ? 255 : alpha(focus));
                c.drawRoundRect(edge, radius, radius, ring);
            }
        }
    }

    // A round button: white disc with a blue ring and a shadow to the bottom right. Focused, the ring goes
    // a deeper blue and thicker.
    static final class Disc extends Shape {
        private final float stroke;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader rest, lit;

        Disc(float stroke, float margin) {
            super(margin);
            this.stroke = stroke;
            ring.setStyle(Paint.Style.STROKE);
        }

        @Override void faceChanged() {
            rest = new LinearGradient(0, face.top, 0, face.bottom, 0xFFFFFFFF, 0xFFEEF1F6, Shader.TileMode.CLAMP);
            lit = new LinearGradient(0, face.top, 0, face.bottom, 0xFFFFFFFF, 0xFFF1F6FF, Shader.TileMode.CLAMP);
        }

        @Override public void draw(Canvas c) {
            float cx = face.centerX(), cy = face.centerY(), d = Math.min(face.width(), face.height());
            shadow(c, 0, true);
            fill.setShader(rest);
            fill.setAlpha(255);
            c.drawCircle(cx, cy, d / 2f, fill);
            if (focus > 0f) {
                fill.setShader(lit);
                fill.setAlpha(alpha(focus));
                c.drawCircle(cx, cy, d / 2f, fill);
            }
            float w = stroke * (1f + 0.4f * focus);
            ring.setStrokeWidth(w);
            ring.setColor(BLUE);
            ring.setAlpha(alpha(1f - focus));
            c.drawCircle(cx, cy, d / 2f - w / 2, ring);
            if (focus > 0f) {
                ring.setColor(BLUE_DEEP);
                ring.setAlpha(alpha(focus));
                c.drawCircle(cx, cy, d / 2f - w / 2, ring);
            }
        }
    }

    // The page arrow: a flat blue triangle with rounded corners. dir 1 points right, -1 left. Focused, it
    // turns a deeper blue and gets a white edge.
    static final class Arrow extends Shape {
        private final int dir;
        private final Path path = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader lit;

        Arrow(int dir) {
            super(0);
            this.dir = dir;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeJoin(Paint.Join.ROUND);
            ring.setColor(0xFFFFFFFF);
        }

        @Override void faceChanged() {
            float w = face.width(), h = face.height();
            float inX = w * 0.14f, inY = h * 0.06f; // leave room for the focus ring and the corners
            float base = dir > 0 ? face.left + inX : face.right - inX, tip = dir > 0 ? face.right - inX : face.left + inX;
            rounded(path, new float[] {base, tip, base}, new float[] {face.top + inY, face.centerY(), face.bottom - inY},
                    Math.min(w, h) * 0.2f);
            ring.setStrokeWidth(Math.min(w, h) * 0.08f);
            lit = new LinearGradient(0, face.top, 0, face.bottom, 0xFF2F78F0, BLUE_DEEP, Shader.TileMode.CLAMP);
        }

        @Override public void draw(Canvas c) {
            fill.setShader(null);
            fill.setColor(BLUE);
            c.drawPath(path, fill);
            if (focus > 0f) {
                fill.setShader(lit);
                fill.setAlpha(alpha(focus));
                c.drawPath(path, fill);
                ring.setAlpha(alpha(focus));
                c.drawPath(path, ring);
            }
        }

        // A closed shape through the given corners, each one rounded off by about r.
        private static void rounded(Path p, float[] xs, float[] ys, float r) {
            int n = xs.length;
            p.reset();
            for (int i = 0; i < n; i++) {
                int a = (i + n - 1) % n, b = (i + 1) % n;
                float[] in = toward(xs[i], ys[i], xs[a], ys[a], r), out = toward(xs[i], ys[i], xs[b], ys[b], r);
                if (i == 0) p.moveTo(in[0], in[1]);
                else p.lineTo(in[0], in[1]);
                p.quadTo(xs[i], ys[i], out[0], out[1]);
            }
            p.close();
        }

        // The point d away from (x, y) on the way to (tx, ty), but never past the middle of that edge.
        private static float[] toward(float x, float y, float tx, float ty, float d) {
            float dx = tx - x, dy = ty - y, len = (float) Math.sqrt(dx * dx + dy * dy);
            float k = len == 0 ? 0 : Math.min(d, len / 2) / len;
            return new float[] {x + dx * k, y + dy * k};
        }
    }

    // A gear: eight teeth and a round hole, filled in one colour.
    static final class Gear extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Gear(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onBoundsChange(Rect b) {
            float cx = b.exactCenterX(), cy = b.exactCenterY(), size = Math.min(b.width(), b.height());
            float outer = size * 0.45f, inner = size * 0.35f, hole = size * 0.16f;
            int teeth = 8;
            double step = 2 * Math.PI / teeth;
            paint.setStrokeWidth(size * 0.04f);
            path.reset();
            for (int i = 0; i < teeth; i++) {
                double a = i * step;
                // Each tooth: wide at its root, narrower at its tip.
                double[] angle = {a - 0.30 * step, a - 0.17 * step, a + 0.17 * step, a + 0.30 * step};
                float[] radius = {inner, outer, outer, inner};
                for (int k = 0; k < 4; k++) {
                    float x = cx + (float) (radius[k] * Math.cos(angle[k])), y = cy + (float) (radius[k] * Math.sin(angle[k]));
                    if (i == 0 && k == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
            }
            path.close();
            path.addCircle(cx, cy, hole, Path.Direction.CCW);
            path.setFillType(Path.FillType.EVEN_ODD);
        }

        @Override public void draw(Canvas c) { c.drawPath(path, paint); }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // An envelope: a filled rounded rectangle with a white V on its flap.
    static final class Envelope extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path flap = new Path();
        private final int color;

        Envelope(int color) {
            this.color = color;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), sw = size * 0.075f;
            float cx = b.exactCenterX(), cy = b.exactCenterY(), hw = size * 0.46f, hh = size * 0.33f;
            box.set(cx - hw, cy - hh, cx + hw, cy + hh);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            c.drawRoundRect(box, sw * 1.4f, sw * 1.4f, paint);
            flap.reset();
            flap.moveTo(box.left + sw * 1.6f, box.top + sw * 1.8f);
            flap.lineTo(cx, cy + hh * 0.10f);
            flap.lineTo(box.right - sw * 1.6f, box.top + sw * 1.8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setColor(0xFFFFFFFF);
            c.drawPath(flap, paint);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // The Wi-Fi fan: three thick arcs and a dot. The first `bars` arcs are lit blue, the rest pale grey;
    // offline is all grey with a red slash.
    static final class Wifi extends Drawable {
        private final int bars;
        private final boolean offline;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();

        Wifi(int bars, boolean offline) {
            this.bars = bars;
            this.offline = offline;
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX();
            float cy = b.exactCenterY() + size * 0.36f, stroke = size * 0.12f;
            float[] radius = {size * 0.26f, size * 0.44f, size * 0.62f};
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            for (int i = 0; i < 3; i++) {
                paint.setColor(i < bars ? BLUE : 0xFFC9D2E0);
                arc.set(cx - radius[i], cy - radius[i], cx + radius[i], cy + radius[i]);
                c.drawArc(arc, 225, 90, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bars > 0 ? BLUE : 0xFFC9D2E0);
            c.drawCircle(cx, cy, size * 0.075f, paint);
            if (offline) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFFD9534F);
                paint.setStrokeWidth(stroke * 0.8f);
                c.drawLine(b.left + size * 0.16f, b.top + size * 0.12f, b.right - size * 0.16f, b.bottom - size * 0.08f, paint);
            }
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Mobile signal: four rising bars, `level` of them lit (0 to 4). Without a SIM they are all grey
    // with a red slash.
    static final class Cell extends Drawable {
        private final int level;
        private final boolean noSim;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();

        Cell(int level, boolean noSim) {
            this.level = level;
            this.noSim = noSim;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), bw = size * 0.16f, gap = size * 0.07f;
            float x = b.exactCenterX() - (4 * bw + 3 * gap) / 2, bottom = b.exactCenterY() + size * 0.40f;
            float[] height = {size * 0.28f, size * 0.46f, size * 0.64f, size * 0.82f};
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) {
                paint.setColor(!noSim && i < level ? BLUE : 0xFFC9D2E0);
                bar.set(x + i * (bw + gap), bottom - height[i], x + i * (bw + gap) + bw, bottom);
                c.drawRoundRect(bar, bw * 0.3f, bw * 0.3f, paint);
            }
            if (noSim) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(0xFFD9534F);
                paint.setStrokeWidth(size * 0.10f);
                c.drawLine(b.left + size * 0.14f, b.top + size * 0.12f, b.right - size * 0.14f, b.bottom - size * 0.08f, paint);
            }
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // A bell: a rounded dome flaring out at the bottom, a knob on top and a clapper under it.
    static final class Bell extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Bell(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onBoundsChange(Rect b) {
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), top = b.exactCenterY() - size * 0.46f;
            paint.setStrokeWidth(size * 0.05f);
            path.reset();
            path.moveTo(cx - 0.40f * size, top + 0.72f * size);
            path.cubicTo(cx - 0.27f * size, top + 0.64f * size, cx - 0.30f * size, top + 0.22f * size, cx, top + 0.20f * size);
            path.cubicTo(cx + 0.30f * size, top + 0.22f * size, cx + 0.27f * size, top + 0.64f * size, cx + 0.40f * size, top + 0.72f * size);
            path.close();
            path.addCircle(cx, top + 0.13f * size, 0.06f * size, Path.Direction.CW);
            path.addCircle(cx, top + 0.84f * size, 0.09f * size, Path.Direction.CW);
        }

        @Override public void draw(Canvas c) { c.drawPath(path, paint); }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

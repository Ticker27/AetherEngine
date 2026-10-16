package com.aether.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ImageCompressor — ย่อรูปตามกลไกของแบบจำลอง
 *   decode → centerCrop สี่เหลี่ยมจัตุรัส → scale 128×128 → JPEG q85 → ลดคุณภาพทีละ 5 จน ≤ 10 KB
 */
public final class ImageCompressor {

    public static final int TARGET_PX = 128;      // parity: 128×128
    public static final int MAX_BYTES = 10240;    // parity: ≤10 KB (10,240 B)
    public static final int START_QUALITY = 85;   // parity: quality 85

    private ImageCompressor() {}

    public static byte[] compress(Context ctx, Uri src) throws IOException {
        return compress(ctx, src, TARGET_PX, MAX_BYTES);
    }

    public static byte[] compress(Context ctx, Uri src, int size, int maxBytes) throws IOException {
        try (InputStream in = ctx.getContentResolver().openInputStream(src)) {
            if (in == null) throw new IOException("no input stream for " + src);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp == null) throw new IOException("decode failed");
            Bitmap square = centerCropSquare(bmp, size);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int q = START_QUALITY;
            square.compress(Bitmap.CompressFormat.JPEG, q, bos);
            while (bos.size() > maxBytes && q > 5) {       // parity: loop -5
                q -= 5;
                bos.reset();
                square.compress(Bitmap.CompressFormat.JPEG, q, bos);
            }
            return bos.toByteArray();
        }
    }

    private static Bitmap centerCropSquare(Bitmap src, int size) {
        int w = src.getWidth();
        int h = src.getHeight();
        int edge = Math.min(w, h);
        int x = (w - edge) / 2;
        int y = (h - edge) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, edge, edge);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
        if (cropped != scaled) cropped.recycle();
        return scaled;
    }
}

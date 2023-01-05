package com.google.mlkit.vision.demo.video;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in synchronous mode.
 */
public class CustomMediaCodecAdapter implements MediaCodecAdapter {

    /** A factory for {@link com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter} instances. */
    public static class Factory implements MediaCodecAdapter.Factory {

        @Override
        public MediaCodecAdapter createAdapter(Configuration configuration) throws IOException {
            @Nullable MediaCodec codec = null;
            try {
                codec = createCodec(configuration);
                TraceUtil.beginSection("configureCodec");
                codec.configure(
                        configuration.mediaFormat,
                        /*configuration.surface*/null,
                        configuration.crypto,
                        configuration.flags);
                TraceUtil.endSection();
                TraceUtil.beginSection("startCodec");
                codec.start();
                TraceUtil.endSection();
                return new CustomMediaCodecAdapter(codec);
            } catch (IOException | RuntimeException e) {
                if (codec != null) {
                    codec.release();
                }
                throw e;
            }
        }

        /** Creates a new {@link MediaCodec} instance. */
        protected MediaCodec createCodec(Configuration configuration) throws IOException {
            checkNotNull(configuration.codecInfo);
            String codecName = configuration.codecInfo.name;
            TraceUtil.beginSection("createCodec:" + codecName);
            MediaCodec mediaCodec = MediaCodec.createByCodecName(codecName);
            TraceUtil.endSection();
            return mediaCodec;
        }
    }

    private final MediaCodec codec;
    @Nullable private ByteBuffer[] inputByteBuffers;
    @Nullable private ByteBuffer[] outputByteBuffers;

    private CustomMediaCodecAdapter(MediaCodec mediaCodec) {
        this.codec = mediaCodec;
        if (Util.SDK_INT < 21) {
            inputByteBuffers = codec.getInputBuffers();
            outputByteBuffers = codec.getOutputBuffers();
        }
    }

    @Override
    public int dequeueInputBufferIndex() {
        return codec.dequeueInputBuffer(0);
    }

    @Override
    public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
        int index;
        do {
            index = codec.dequeueOutputBuffer(bufferInfo, 0);
            if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED && Util.SDK_INT < 21) {
                outputByteBuffers = codec.getOutputBuffers();
            }
        } while (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED);

        return index;
    }

    @Override
    public MediaFormat getOutputFormat() {
        return codec.getOutputFormat();
    }

    @Override
    @Nullable
    public ByteBuffer getInputBuffer(int index) {
        if (Util.SDK_INT >= 21) {
            return codec.getInputBuffer(index);
        } else {
            return castNonNull(inputByteBuffers)[index];
        }
    }

    @Override
    @Nullable
    public ByteBuffer getOutputBuffer(int index) {
        if (Util.SDK_INT >= 21) {
            return codec.getOutputBuffer(index);
        } else {
            return castNonNull(outputByteBuffers)[index];
        }
    }

    /*
    * This is added.
    */
    public Image getOutputImage(int index){
        return codec.getOutputImage(index);
    }

    @Override
    public void queueInputBuffer(
            int index, int offset, int size, long presentationTimeUs, int flags) {
        codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
    }

    @Override
    public void queueSecureInputBuffer(
            int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
        codec.queueSecureInputBuffer(
                index, offset, info.getFrameworkCryptoInfo(), presentationTimeUs, flags);
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {
        codec.releaseOutputBuffer(index, render);
    }

    @Override
    @RequiresApi(21)
    public void releaseOutputBuffer(int index, long renderTimeStampNs) {
        codec.releaseOutputBuffer(index, renderTimeStampNs);
    }

    @Override
    public void flush() {
        codec.flush();
    }

    @Override
    public void release() {
        inputByteBuffers = null;
        outputByteBuffers = null;
        codec.release();
    }

    @Override
    @RequiresApi(23)
    public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
        codec.setOnFrameRenderedListener(
                (codec, presentationTimeUs, nanoTime) ->
                        listener.onFrameRendered(
                                CustomMediaCodecAdapter.this, presentationTimeUs, nanoTime),
                handler);
    }

    @Override
    @RequiresApi(23)
    public void setOutputSurface(Surface surface) {
        codec.setOutputSurface(surface);
    }

    @Override
    @RequiresApi(19)
    public void setParameters(Bundle params) {
        codec.setParameters(params);
    }

    @Override
    public void setVideoScalingMode(@C.VideoScalingMode int scalingMode) {
        codec.setVideoScalingMode(scalingMode);
    }

    @Override
    public boolean needsReconfiguration() {
        return false;
    }

    @Override
    public PersistableBundle getMetrics() {
        return null;
    }
}

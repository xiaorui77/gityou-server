package com.gityou.server.Utils;

import org.eclipse.jgit.util.TemporaryBuffer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import static com.gityou.server.Utils.ServletUtils.acceptsGzipEncoding;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;

public class SmartOutputStream extends TemporaryBuffer {
    private static final int LIMIT = 32 * 1024;

    private final HttpServletRequest req;
    private final HttpServletResponse rsp;
    private boolean compressStream;
    private boolean startedOutput;

    public SmartOutputStream(final HttpServletRequest req,
                             final HttpServletResponse rsp,
                             boolean compressStream) {
        super(LIMIT);
        this.req = req;
        this.rsp = rsp;
        this.compressStream = compressStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected OutputStream overflow() throws IOException {
        startedOutput = true;

        OutputStream out = rsp.getOutputStream();
        if (compressStream && acceptsGzipEncoding(req)) {
            rsp.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
            out = new GZIPOutputStream(out);
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        super.close();

        if (!startedOutput) {
            // If output hasn't started yet, the entire thing fit into our
            // buffer. Try to use a proper Content-Length header, and also
            // deflate the response with gzip if it will be smaller.
            @SuppressWarnings("resource")
            TemporaryBuffer out = this;

            if (256 < out.length() && acceptsGzipEncoding(req)) {
                TemporaryBuffer gzbuf = new TemporaryBuffer.Heap(LIMIT);
                try {
                    try (GZIPOutputStream gzip = new GZIPOutputStream(gzbuf)) {
                        out.writeTo(gzip, null);
                    }
                    if (gzbuf.length() < out.length()) {
                        out = gzbuf;
                        rsp.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
                    }
                } catch (IOException err) {
                    // Most likely caused by overflowing the buffer, meaning
                    // its larger if it were compressed. Discard compressed
                    // copy and use the original.
                }
            }

            // The Content-Length cannot overflow when cast to an int, our
            // hardcoded LIMIT constant above assures us we wouldn't store
            // more than 2 GiB of content in memory.
            rsp.setContentLength((int) out.length());
            try (OutputStream os = rsp.getOutputStream()) {
                out.writeTo(os, null);
                os.flush();
            }
        }
    }
}

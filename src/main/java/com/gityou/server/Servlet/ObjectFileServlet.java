package com.gityou.server.Servlet;

import com.gityou.server.Utils.FileSender;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.util.HttpSupport.*;

public abstract class ObjectFileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public static class Loose extends ObjectFileServlet {
        private static final long serialVersionUID = 1L;

        public Loose() {
            super("application/x-git-loose-object");
        }

        @Override
        String etag(FileSender sender) throws IOException {
            Instant lastModified = sender.getLastModified();
            return Long.toHexString(lastModified.getEpochSecond())
                    + Long.toHexString(lastModified.getNano());
        }
    }

    private static abstract class PackData extends ObjectFileServlet {
        private static final long serialVersionUID = 1L;

        PackData(String contentType) {
            super(contentType);
        }

        @Override
        String etag(FileSender sender) throws IOException {
            return sender.getTailChecksum();
        }
    }

    public static class Pack extends PackData {
        private static final long serialVersionUID = 1L;

        public Pack() {
            super("application/x-git-packed-objects");
        }
    }

    public static class PackIdx extends PackData {
        private static final long serialVersionUID = 1L;

        public PackIdx() {
            super("application/x-git-packed-objects-toc");
        }
    }

    private final String contentType;

    ObjectFileServlet(String contentType) {
        this.contentType = contentType;
    }

    abstract String etag(FileSender sender) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(final HttpServletRequest req,
                      final HttpServletResponse rsp) throws IOException {
        serve(req, rsp, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doHead(final HttpServletRequest req,
                          final HttpServletResponse rsp) throws ServletException, IOException {
        serve(req, rsp, false);
    }

    private void serve(final HttpServletRequest req,
                       final HttpServletResponse rsp, final boolean sendBody)
            throws IOException {
        final File obj = new File(objects(req), req.getPathInfo());
        final FileSender sender;
        try {
            sender = new FileSender(obj);
        } catch (FileNotFoundException e) {
            rsp.sendError(SC_NOT_FOUND);
            return;
        }

        try {
            final String etag = etag(sender);
            // HTTP header Last-Modified header has a resolution of 1 sec, see
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.29
            final long lastModified = sender.getLastModified().getEpochSecond();

            String ifNoneMatch = req.getHeader(HDR_IF_NONE_MATCH);
            if (etag != null && etag.equals(ifNoneMatch)) {
                rsp.sendError(SC_NOT_MODIFIED);
                return;
            }

            long ifModifiedSince = req.getDateHeader(HDR_IF_MODIFIED_SINCE);
            if (0 < lastModified && lastModified < ifModifiedSince) {
                rsp.sendError(SC_NOT_MODIFIED);
                return;
            }

            if (etag != null)
                rsp.setHeader(HDR_ETAG, etag);
            if (0 < lastModified)
                rsp.setDateHeader(HDR_LAST_MODIFIED, lastModified);
            rsp.setContentType(contentType);
            sender.serve(req, rsp, sendBody);
        } finally {
            sender.close();
        }
    }

    private static File objects(HttpServletRequest req) {
        final Repository db = getRepository(req);
        return ((ObjectDirectory) db.getObjectDatabase()).getDirectory();
    }
}

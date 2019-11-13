package com.gityou.server.Servlet;

import com.gityou.server.Filter.SmartServiceInfoRefs;
import com.gityou.server.Utils.SmartOutputStream;
import org.eclipse.jgit.http.server.HttpServerText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import static com.gityou.server.Utils.ServletUtils.*;
import static javax.servlet.http.HttpServletResponse.*;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.*;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;

public class UploadPackServlet extends HttpServlet {
    public static class InfoRefs extends SmartServiceInfoRefs {
        private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

        public InfoRefs(UploadPackFactory<HttpServletRequest> uploadPackFactory,
                        List<Filter> filters) {
            super(UPLOAD_PACK, filters);
            this.uploadPackFactory = uploadPackFactory;
        }

        @Override
        protected void begin(HttpServletRequest req, Repository db)
                throws IOException, ServiceNotEnabledException,
                ServiceNotAuthorizedException {
            UploadPack up = uploadPackFactory.create(req, db);
            InternalHttpServerGlue.setPeerUserAgent(
                    up,
                    req.getHeader(HDR_USER_AGENT));
            req.setAttribute(ATTRIBUTE_HANDLER, up);
        }

        @Override
        protected void advertise(HttpServletRequest req,
                                 RefAdvertiser.PacketLineOutRefAdvertiser pck) throws IOException,
                ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
            try {
                up.setBiDirectionalPipe(false);
                up.sendAdvertisedRefs(pck);
            } finally {
                // TODO(jonathantanmy): Move responsibility for closing the
                // RevWalk to UploadPack, either by making it AutoCloseable
                // or by making sendAdvertisedRefs clean up after itself.
                up.getRevWalk().close();
            }
        }

        @Override
        protected void respond(HttpServletRequest req,
                               PacketLineOut pckOut, String serviceName) throws IOException,
                ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
            try {
                up.setBiDirectionalPipe(false);
                up.sendAdvertisedRefs(new RefAdvertiser.PacketLineOutRefAdvertiser(pckOut), serviceName);
            } finally {
                // TODO(jonathantanmy): Move responsibility for closing the
                // RevWalk to UploadPack, either by making it AutoCloseable
                // or by making sendAdvertisedRefs clean up after itself.
                up.getRevWalk().close();
            }
        }
    }

    public static class Factory implements Filter {
        private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

        public Factory(UploadPackFactory<HttpServletRequest> uploadPackFactory) {
            this.uploadPackFactory = uploadPackFactory;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse rsp = (HttpServletResponse) response;
            UploadPack rp;
            try {
                rp = uploadPackFactory.create(req, getRepository(req));
            } catch (ServiceNotAuthorizedException e) {
                rsp.sendError(SC_UNAUTHORIZED, e.getMessage());
                return;
            } catch (ServiceNotEnabledException e) {
                sendError(req, rsp, SC_FORBIDDEN, e.getMessage());
                return;
            }

            try {
                req.setAttribute(ATTRIBUTE_HANDLER, rp);
                chain.doFilter(req, rsp);
            } finally {
                req.removeAttribute(ATTRIBUTE_HANDLER);
            }
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // Nothing.
        }

        @Override
        public void destroy() {
            // Nothing.
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doPost(final HttpServletRequest req,
                       final HttpServletResponse rsp) throws IOException {
        if (!UPLOAD_PACK_REQUEST_TYPE.equals(req.getContentType())) {
            rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        SmartOutputStream out = new SmartOutputStream(req, rsp, false) {
            @Override
            public void flush() throws IOException {
                doFlush();
            }
        };

        UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
        try {
            up.setBiDirectionalPipe(false);
            rsp.setContentType(UPLOAD_PACK_RESULT_TYPE);

            up.upload(getInputStream(req), out, null);
            out.close();

        } catch (ServiceMayNotContinueException e) {
            if (e.isOutput()) {
                consumeRequestBody(req);
                out.close();
            } else if (!rsp.isCommitted()) {
                rsp.reset();
                sendError(req, rsp, e.getStatusCode(), e.getMessage());
            }
            return;

        } catch (UploadPackInternalServerErrorException e) {
            // Special case exception, error message was sent to client.
            log(up.getRepository(), e.getCause());
            consumeRequestBody(req);
            out.close();

        } catch (Throwable e) {
            log(up.getRepository(), e);
            if (!rsp.isCommitted()) {
                rsp.reset();
                sendError(req, rsp, SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }
    }

    private void log(Repository git, Throwable e) {
        getServletContext().log(MessageFormat.format(HttpServerText.get().internalErrorDuringUploadPack, identify(git)), e);
    }
}

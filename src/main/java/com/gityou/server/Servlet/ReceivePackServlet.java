package com.gityou.server.Servlet;

import com.gityou.server.Filter.SmartServiceInfoRefs;
import com.gityou.server.Utils.SmartOutputStream;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.http.server.HttpServerText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.InternalHttpServerGlue;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

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

public class ReceivePackServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public static class InfoRefs extends SmartServiceInfoRefs {
        private final ReceivePackFactory<HttpServletRequest> receivePackFactory;

        public InfoRefs(ReceivePackFactory<HttpServletRequest> receivePackFactory,
                        List<Filter> filters) {
            super(RECEIVE_PACK, filters);
            this.receivePackFactory = receivePackFactory;
        }

        @Override
        protected void begin(HttpServletRequest req, Repository db)
                throws IOException, ServiceNotEnabledException,
                ServiceNotAuthorizedException {
            ReceivePack rp = receivePackFactory.create(req, db);
            InternalHttpServerGlue.setPeerUserAgent(
                    rp,
                    req.getHeader(HDR_USER_AGENT));
            req.setAttribute(ATTRIBUTE_HANDLER, rp);
        }

        @Override
        protected void advertise(HttpServletRequest req,
                                 RefAdvertiser.PacketLineOutRefAdvertiser pck) throws IOException,
                ServiceNotEnabledException, ServiceNotAuthorizedException {
            ReceivePack rp = (ReceivePack) req.getAttribute(ATTRIBUTE_HANDLER);
            try {
                rp.sendAdvertisedRefs(pck);
            } finally {
                rp.getRevWalk().close();
            }
        }
    }

    public static class Factory implements Filter {
        private final ReceivePackFactory<HttpServletRequest> receivePackFactory;

        public Factory(ReceivePackFactory<HttpServletRequest> receivePackFactory) {
            this.receivePackFactory = receivePackFactory;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse rsp = (HttpServletResponse) response;
            ReceivePack rp;
            try {
                rp = receivePackFactory.create(req, getRepository(req));
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
        if (!RECEIVE_PACK_REQUEST_TYPE.equals(req.getContentType())) {
            rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        SmartOutputStream out = new SmartOutputStream(req, rsp, false) {
            @Override
            public void flush() throws IOException {
                doFlush();
            }
        };

        ReceivePack rp = (ReceivePack) req.getAttribute(ATTRIBUTE_HANDLER);
        try {
            rp.setBiDirectionalPipe(false);
            rsp.setContentType(RECEIVE_PACK_RESULT_TYPE);

            rp.receive(getInputStream(req), out, null);
            out.close();
        } catch (CorruptObjectException e) {
            // This should be already reported to the client.
            getServletContext().log(MessageFormat.format(
                    HttpServerText.get().receivedCorruptObject,
                    e.getMessage(),
                    identify(rp.getRepository())));
            consumeRequestBody(req);
            out.close();

        } catch (UnpackException | PackProtocolException e) {
            // This should be already reported to the client.
            log(rp.getRepository(), e.getCause());
            consumeRequestBody(req);
            out.close();

        } catch (Throwable e) {
            log(rp.getRepository(), e);
            if (!rsp.isCommitted()) {
                rsp.reset();
                sendError(req, rsp, SC_INTERNAL_SERVER_ERROR);
            }
            return;
        }
    }

    private void log(Repository git, Throwable e) {
        getServletContext().log(MessageFormat.format(HttpServerText.get().internalErrorDuringReceivePack, identify(git)), e);
    }
}

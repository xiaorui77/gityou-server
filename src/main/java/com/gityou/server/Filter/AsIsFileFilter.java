package com.gityou.server.Filter;

import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

public class AsIsFileFilter implements Filter {
    private final AsIsFileService asIs;

    public AsIsFileFilter(AsIsFileService getAnyFile) {
        this.asIs = getAnyFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FilterConfig config) throws ServletException {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        try {
            final Repository db = getRepository(request);
            asIs.access(req, db);
            chain.doFilter(request, response);
        } catch (ServiceNotAuthorizedException e) {
            res.sendError(SC_UNAUTHORIZED, e.getMessage());
        } catch (ServiceNotEnabledException e) {
            res.sendError(SC_FORBIDDEN, e.getMessage());
        }
    }
}

package com.gityou.server.Filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.eclipse.jgit.util.HttpSupport.*;

public class NoCacheFilter implements Filter {
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
        HttpServletResponse rsp = (HttpServletResponse) response;

        rsp.setHeader(HDR_EXPIRES, "Fri, 01 Jan 1980 00:00:00 GMT");
        rsp.setHeader(HDR_PRAGMA, "no-cache");

        final String nocache = "no-cache, max-age=0, must-revalidate";
        rsp.setHeader(HDR_CACHE_CONTROL, nocache);

        chain.doFilter(request, response);
    }
}

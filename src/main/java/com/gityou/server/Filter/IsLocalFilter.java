package com.gityou.server.Filter;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

public class IsLocalFilter implements Filter {
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
        if (isLocal(getRepository(request)))
            chain.doFilter(request, response);
        else
            ((HttpServletResponse) response).sendError(SC_FORBIDDEN);
    }

    private static boolean isLocal(Repository db) {
        return db.getObjectDatabase() instanceof ObjectDirectory;
    }
}

package com.gityou.server;


import com.gityou.server.Filter.AsIsFileFilter;
import com.gityou.server.Filter.AuthFilter;
import com.gityou.server.Filter.IsLocalFilter;
import com.gityou.server.Filter.NoCacheFilter;
import com.gityou.server.Servlet.*;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.http.server.HttpServerText;
import org.eclipse.jgit.http.server.RepositoryFilter;
import org.eclipse.jgit.http.server.glue.ErrorServlet;
import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.http.server.glue.RegexGroupFilter;
import org.eclipse.jgit.http.server.glue.ServletBinder;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.eclipse.jgit.util.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

public class GityouFilter extends MetaFilter {
    private volatile boolean initialized;

    private RepositoryResolver<HttpServletRequest> resolver;

    private AsIsFileService asIs = new AsIsFileService();

    private UploadPackFactory<HttpServletRequest> uploadPackFactory = new DefaultUploadPackFactory();

    private ReceivePackFactory<HttpServletRequest> receivePackFactory = new DefaultReceivePackFactory();

    private final List<Filter> uploadPackFilters = new LinkedList<>();

    private final List<Filter> receivePackFilters = new LinkedList<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        if (resolver == null) {
            File root = getFile(filterConfig, "base-path");
            boolean exportAll = getBoolean(filterConfig, "export-all");
            this.resolver = new FileResolver<>(root, exportAll);
        }

        initialized = true;

        if (uploadPackFactory != UploadPackFactory.DISABLED) {
            ServletBinder b = serve("*/" + GitSmartHttpTools.UPLOAD_PACK);
            b = b.through(new UploadPackServlet.Factory(uploadPackFactory));
            for (Filter f : uploadPackFilters)
                b = b.through(f);
            b.with(new UploadPackServlet());
        }

        if (receivePackFactory != ReceivePackFactory.DISABLED) {
            ServletBinder b = serve("*/" + GitSmartHttpTools.RECEIVE_PACK);
            for (Filter f : receivePackFilters)
                b = b.through(f);
            b = b.through(new ReceivePackServlet.Factory(receivePackFactory));
            b.with(new ReceivePackServlet());
        }

        ServletBinder refs = serve("*/" + Constants.INFO_REFS);
        // 加入authFilter
        refs.through(new AuthFilter());

        if (uploadPackFactory != UploadPackFactory.DISABLED) {
            refs = refs.through(new UploadPackServlet.InfoRefs(
                    uploadPackFactory, uploadPackFilters));
        }
        if (receivePackFactory != ReceivePackFactory.DISABLED) {
            refs = refs.through(new ReceivePackServlet.InfoRefs(
                    receivePackFactory, receivePackFilters));
        }
        if (asIs != AsIsFileService.DISABLED) {
            refs = refs.through(new IsLocalFilter());
            refs = refs.through(new AsIsFileFilter(asIs));
            refs.with(new InfoRefsServlet());
        } else
            refs.with(new ErrorServlet(HttpServletResponse.SC_NOT_ACCEPTABLE));

        if (asIs != AsIsFileService.DISABLED) {
            final IsLocalFilter mustBeLocal = new IsLocalFilter();
            final AsIsFileFilter enabled = new AsIsFileFilter(asIs);

            serve("*/" + Constants.HEAD)//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .with(new TextFileServlet(Constants.HEAD));

            final String info_alternates = Constants.OBJECTS + "/" + Constants.INFO_ALTERNATES;
            serve("*/" + info_alternates)//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .with(new TextFileServlet(info_alternates));

            final String http_alternates = Constants.OBJECTS + "/" + Constants.INFO_HTTP_ALTERNATES;
            serve("*/" + http_alternates)//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .with(new TextFileServlet(http_alternates));

            serve("*/objects/info/packs")//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .with(new InfoPacksServlet());

            serveRegex("^/(.*)/objects/([0-9a-f]{2}/[0-9a-f]{38})$")//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .through(new RegexGroupFilter(2))//
                    .with(new ObjectFileServlet.Loose());

            serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.pack)$")//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .through(new RegexGroupFilter(2))//
                    .with(new ObjectFileServlet.Pack());

            serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.idx)$")//
                    .through(mustBeLocal)//
                    .through(enabled)//
                    .through(new RegexGroupFilter(2))//
                    .with(new ObjectFileServlet.PackIdx());
        }
    }


    private static File getFile(FilterConfig cfg, String param)
            throws ServletException {
        String n = cfg.getInitParameter(param);
        if (n == null || "".equals(n))
            throw new ServletException(MessageFormat.format(HttpServerText.get().parameterNotSet, param));

        File path = new File(n);
        if (!path.exists())
            throw new ServletException(MessageFormat.format(HttpServerText.get().pathForParamNotFound, path, param));
        return path;
    }

    private static boolean getBoolean(FilterConfig cfg, String param)
            throws ServletException {
        String n = cfg.getInitParameter(param);
        if (n == null)
            return false;
        try {
            return StringUtils.toBoolean(n);
        } catch (IllegalArgumentException err) {
            throw new ServletException(MessageFormat.format(HttpServerText.get().invalidBoolean, param, n));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ServletBinder register(ServletBinder binder) {
        if (resolver == null)
            throw new IllegalStateException(HttpServerText.get().noResolverAvailable);
        binder = binder.through(new NoCacheFilter());
        binder = binder.through(new RepositoryFilter(resolver));
        return binder;
    }

    public void addReceivePackFilter(Filter filter) {
        assertNotInitialized();
        receivePackFilters.add(filter);
    }

    private void assertNotInitialized() {
        if (initialized)
            throw new IllegalStateException(HttpServerText.get().alreadyInitializedByContainer);
    }

}// end

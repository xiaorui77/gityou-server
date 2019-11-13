package com.gityou.server;

import com.gityou.server.Filter.AuthFilter;
import org.eclipse.jgit.http.server.glue.MetaServlet;

import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import java.util.Enumeration;

@WebServlet(name = "gityouServer", urlPatterns = {"/*"},
        loadOnStartup = 1,
        initParams = {
                @WebInitParam(name = "base-path", value = "D:\\tmp\\gityou\\repository\\"),
                @WebInitParam(name = "export-all", value = "true")
        })
public class GitYouServlet extends MetaServlet {
    private final GityouFilter gitFilter;

    public GitYouServlet() {
        super(new GityouFilter());
        gitFilter = (GityouFilter) getDelegateFilter();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        gitFilter.addReceivePackFilter(new AuthFilter());
        gitFilter.init(new FilterConfig() {
            @Override
            public String getFilterName() {
                return gitFilter.getClass().getName();
            }

            @Override
            public String getInitParameter(String name) {
                return config.getInitParameter(name);
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return config.getInitParameterNames();
            }

            @Override
            public ServletContext getServletContext() {
                return config.getServletContext();
            }
        });
    }
}

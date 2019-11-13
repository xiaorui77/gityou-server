package com.gityou.server.Filter;

import com.gityou.server.service.AuthService;
import org.eclipse.jgit.http.server.glue.WrappedRequest;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthFilter implements Filter {

    private AuthService authService = new AuthService();


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequestWrapper req = (HttpServletRequestWrapper) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        String auth = req.getHeader("authorization");
        if (auth == null || auth.equals("")) {
            rsp.setStatus(401);
            rsp.setHeader("WWW-authenticate", "Basic realm=\"unauthorized\"");
            return;
        }

        String authorization = auth.split(" ")[1];

        String loginInfo = new String(Base64.getDecoder().decode(authorization.replace("\r\n", "")), StandardCharsets.UTF_8);
        String user = authService.auth(req.getPathInfo(), loginInfo);
        if (user == null || user.equals("")) {
            rsp.setStatus(401);
            rsp.setHeader("WWW-authenticate", "Basic realm=\"authorized fail\"");
            return;
        }

        chain.doFilter(new WrappedRequest((HttpServletRequest) req.getRequest(), req.getServletPath(), req.getPathInfo()) {
            @Override
            public String getRemoteUser() {
                return user;
            }
        }, response);
    }

    @Override
    public void destroy() {

    }
}

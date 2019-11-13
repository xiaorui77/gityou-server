package com.gityou.server.Servlet;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.lib.ObjectDatabase;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.http.server.ServletUtils.sendPlainText;

public class InfoPacksServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(final HttpServletRequest req,
                      final HttpServletResponse rsp) throws IOException {
        sendPlainText(packList(req), req, rsp);
    }

    private static String packList(HttpServletRequest req) {
        final StringBuilder out = new StringBuilder();
        final ObjectDatabase db = getRepository(req).getObjectDatabase();
        if (db instanceof ObjectDirectory) {
            for (PackFile pack : ((ObjectDirectory) db).getPacks()) {
                out.append("P ");
                out.append(pack.getPackFile().getName());
                out.append('\n');
            }
        }
        out.append('\n');
        return out.toString();
    }
}

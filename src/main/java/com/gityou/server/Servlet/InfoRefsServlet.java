package com.gityou.server.Servlet;

import com.gityou.server.Utils.SmartOutputStream;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefAdvertiser;
import org.eclipse.jgit.util.HttpSupport;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

public class InfoRefsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(final HttpServletRequest req,
                      final HttpServletResponse rsp) throws IOException {
        // Assume a dumb client and send back the dumb client
        // version of the info/refs file.
        rsp.setContentType(HttpSupport.TEXT_PLAIN);
        rsp.setCharacterEncoding(UTF_8.name());

        final Repository db = getRepository(req);
        try (OutputStreamWriter out = new OutputStreamWriter(
                new SmartOutputStream(req, rsp, true),
                UTF_8)) {
            final RefAdvertiser adv = new RefAdvertiser() {
                @Override
                protected void writeOne(CharSequence line)
                        throws IOException {
                    // Whoever decided that info/refs should use a different
                    // delimiter than the native git:// protocol shouldn't
                    // be allowed to design this sort of stuff. :-(
                    out.append(line.toString().replace(' ', '\t'));
                }

                @Override
                protected void end() {
                    // No end marker required for info/refs format.
                }
            };
            adv.init(db);
            adv.setDerefTags(true);
            adv.send(db.getRefDatabase().getRefsByPrefix(Constants.R_REFS));
        }
    }
}

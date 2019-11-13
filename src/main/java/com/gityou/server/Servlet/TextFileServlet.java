package com.gityou.server.Servlet;

import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.IO;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.http.server.ServletUtils.send;

public class TextFileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final String fileName;

    public TextFileServlet(String name) {
        this.fileName = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(final HttpServletRequest req,
                      final HttpServletResponse rsp) throws IOException {
        try {
            rsp.setContentType(HttpSupport.TEXT_PLAIN);
            send(read(req), req, rsp);
        } catch (FileNotFoundException noFile) {
            rsp.sendError(SC_NOT_FOUND);
        }
    }

    private byte[] read(HttpServletRequest req) throws IOException {
        final File gitdir = getRepository(req).getDirectory();
        if (gitdir == null)
            throw new FileNotFoundException(fileName);
        return IO.readFully(new File(gitdir, fileName));
    }
}

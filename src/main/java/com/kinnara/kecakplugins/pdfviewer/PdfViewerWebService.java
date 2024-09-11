package com.kinnara.kecakplugins.pdfviewer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;

// import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
// import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;

public class PdfViewerWebService extends DefaultApplicationPlugin implements PluginWebSupport{
    final static String LABEL = "PDF Web Service";

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getName() {
        return LABEL;
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public void webService(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException {
        String urlString = servletRequest.getParameter("url");

        URL url = new URL(urlString);

        try (InputStream inputStream = url.openStream()) {

            servletResponse.setContentType("application/pdf");

            OutputStream outputStream = servletResponse.getOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    @Override
    public Object execute(Map map) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'execute'");
    }
}

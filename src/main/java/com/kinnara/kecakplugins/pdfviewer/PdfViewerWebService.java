package com.kinnara.kecakplugins.pdfviewer;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public class PdfViewerWebService extends DefaultApplicationPlugin implements PluginWebSupport {
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
        final String urlString = servletRequest.getParameter("url").replaceAll("\\s", "%20");
        LogUtil.info(getClassName(), "url parameter [" + urlString + "]");

        final URL url = new URL(urlString);
        final URLConnection urlConnection = url.openConnection();
        LogUtil.info(getClassName(), "getContentType [" + urlConnection.getContentType() + "] getContentLength [" + urlConnection.getContentLength() + "]");
        urlConnection.getHeaderFields().forEach((key, values) -> {
            LogUtil.info(getClassName(), "header ["+key+"] ["+String.join(" || ", values)+"]");
        });
        servletResponse.setContentType("application/vnd.ms-excel");
        final OutputStream outputStream = servletResponse.getOutputStream();
        try (InputStream inputStream = url.openStream()) {
            byte[] buffer = new byte[4096];

            while (inputStream.read(buffer) >= 0) {
                outputStream.write(buffer);
            }
            outputStream.flush();
        }
    }

    @Override
    public Object execute(Map map) {
        throw new UnsupportedOperationException("Unimplemented method 'execute'");
    }
}

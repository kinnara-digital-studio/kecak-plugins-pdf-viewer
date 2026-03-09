package com.kinnarastudio.kecakplugins.pdfviewer.app;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.kecak.apps.exception.ApiException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * PDF Viewer Web Service
 *
 * Show PDF in [url] as [output]
 */
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
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public void webService(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException {
        try {
            final String outputContentType = getParameter(servletRequest, "output");
            final String urlString = URLEncoder.encode(getParameter(servletRequest, "url"), StandardCharsets.UTF_8);
            LogUtil.info(getClassName(), "url parameter [" + urlString + "]");

            final URL url = new URL(urlString);
            final URLConnection urlConnection = url.openConnection();
            LogUtil.info(getClassName(), "getContentType [" + urlConnection.getContentType() + "] getContentLength [" + urlConnection.getContentLength() + "]");
            urlConnection.getHeaderFields().forEach((key, values) -> {
                LogUtil.info(getClassName(), "header [" + key + "] [" + String.join(" || ", values) + "]");
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
        } catch (ApiException e) {
            LogUtil.error(PdfViewerWebService.class.getName(), e, e.getMessage());
            servletResponse.sendError(e.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public Object execute(Map map) {
        throw new UnsupportedOperationException("Unimplemented method 'execute'");
    }

    private String getParameter(HttpServletRequest request, String name) throws ApiException {
        return optParameter(request, name).orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Parameter [" + name + "] is required"));
    }

    protected Optional<String> optParameter(HttpServletRequest request, String name) {
        return Optional.ofNullable(name)
                .map(request::getParameter);
    }
}

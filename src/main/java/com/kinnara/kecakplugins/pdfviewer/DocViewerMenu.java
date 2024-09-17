package com.kinnara.kecakplugins.pdfviewer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class DocViewerMenu extends UserviewMenu implements PluginWebSupport {
    private final String LABEL = "Doc - Docx Viewer Userview Menu";

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
        return AppUtil.readPluginResource(getClassName(), "/properties/DocViewerMenu.json", null, true, null);
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
    public String getCategory() {
        return "Kecak";
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public String getIcon() {
        return null;
    }

    @Override
    public String getRenderPage() {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        PluginManager pluginManager = (PluginManager) appContext.getBean("pluginManager");

        String urlString = WorkflowUtil.getHttpServletRequest().getParameter("url");
        
        if (urlString != null && !urlString.isEmpty()) {
        try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");

                LogUtil.info(getClassName(), "Content-Type: " + connection.getHeaderField("Content-Type"));
                LogUtil.info(getClassName(), "Content-Length: " + connection.getHeaderField("Content-Length"));
                LogUtil.info(getClassName(), "Last-Modified: " + connection.getHeaderField("Last-Modified"));
                LogUtil.info(getClassName(), "Server: " + connection.getHeaderField("Server"));
                LogUtil.info(getClassName(), "Date: " + connection.getHeaderField("Date"));

                connection.disconnect();
            } catch (IOException e) {
                LogUtil.error(getClassName(), e, e.getMessage() + "; Failed to get metadata from URL: " + urlString);
            }
        }

        Map<String, Object> dataModel = new HashMap<>();
        Map<String, Object> menu = new HashMap<>();
        menu.put("properties", getProperties());
        dataModel.put("menu", menu);
        dataModel.put("label",this.getPropertyString("label"));
        dataModel.put("ratio",this.getPropertyString("ratio"));
        dataModel.put("className",this.getClassName());
        dataModel.put("url", urlString);

        String htmlContent = pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/DocViewerMenu.ftl", null);
        return htmlContent;
    }

    @Override
    public boolean isHomePageSupported() {
        return false;
    }

    @Override
    public void webService(HttpServletRequest servletReqeust, HttpServletResponse servletResponse) throws ServletException, IOException {
        throw new UnsupportedOperationException("Unimplemented method 'webService'");
    }
    
}

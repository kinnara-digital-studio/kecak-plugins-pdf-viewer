package com.kinnara.kecakplugins.pdfviewer;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

public class PdfViewerMenu extends UserviewMenu implements PdfUtils {
    @Override
    public String getCategory() {
        return "Kecak";
    }

    @Override
    public String getIcon() {
        return null;
    }

    @Override
    public String getRenderPage() {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        PluginManager pluginManager = (PluginManager) appContext.getBean("pluginManager");

        Map<String, Object> dataModel = new HashMap<>();
        Map<String, Object> menu = new HashMap<>();
        menu.put("properties", getProperties());
        dataModel.put("menu", menu);
        dataModel.put("label",this.getPropertyString("label"));
        dataModel.put("ratio",this.getPropertyString("ratio"));
        dataModel.put("src", getSrc( null));
        dataModel.put("className",this.getClassName());

        String htmlContent = pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/AcePdfViewerMenu.ftl", null);
        return htmlContent;
    }

    @Override
    public boolean isHomePageSupported() {
        return false;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "PDF Menu";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PdfViewerMenu.json", null, true, null);
    }

    @Override
    public boolean getHtmlEmbed(WorkflowAssignment assignment) {
        return "true".equalsIgnoreCase(AppUtil.processHashVariable(getPropertyString("htmlEmbed"), assignment, null, null));
    }

    @Override
    public String getPdfUrl(WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(getPropertyString("pdfUrl"), assignment, null, null);
    }
}

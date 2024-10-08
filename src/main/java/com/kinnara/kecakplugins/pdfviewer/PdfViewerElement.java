package com.kinnara.kecakplugins.pdfviewer;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.kecak.apps.form.model.AceFormElement;

import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author aristo
 *
 * Pdf Viewer Element
 */
public class PdfViewerElement extends Element implements FileDownloadSecurity, FormBuilderPaletteElement, PdfUtils, AceFormElement {
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "PdfViewerElement.ftl";

        dataModel.put("className", getClassName());
        dataModel.put("src", getElementValue(formData));

        FormUtil.setReadOnlyProperty(this);

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public Object handleElementValueResponse(Element element, FormData formData) {
        return getElementValue(formData);
    }

    @Override
    public String getFormBuilderCategory() {
        return "Kecak";
    }

    @Override
    public int getFormBuilderPosition() {
        return 100;
    }

    @Override
    public String getFormBuilderIcon() {
        return null;
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<img src='${request.contextPath}/plugin/${className}/images/pdf-logo.png' width='320' height='320/>";
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "PDF Element";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PdfViewerElement.json", null, true, null).replaceAll("\"", "'");
    }
    public boolean getHtmlEmbed(WorkflowAssignment assignment) {
        return "true".equalsIgnoreCase(AppUtil.processHashVariable(getPropertyString("htmlEmbed"), assignment, null, null));
    }

    @Override
    public String getPdfUrl(WorkflowAssignment workflowAssignment) {
        return AppUtil.processHashVariable(getPropertyString("pdfUrl"), workflowAssignment, null, null);
    }

    @Override
    public String renderAceTemplate(FormData formData, Map dataModel) {
        String template = "AcePdfViewerElement.ftl";

        dataModel.put("className", getClassName());
        dataModel.put("src", getElementValue(formData));
        dataModel.put("ratio",this.getPropertyString("ratio"));

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    protected String getElementValue(FormData formData) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment workflowAssignment = Optional.of(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);
        return getSrc(workflowAssignment);
    }

    @Override
    public boolean isDownloadAllowed(Map requestParameters) {
        final boolean isAnonymous = WorkflowUtil.isCurrentUserAnonymous();

        Object permissionElement = getProperty("permissionPlugin");
        if(permissionElement == null) {
            return !isAnonymous;
        }

        @SuppressWarnings("rawtypes")
        Map elementMap = (Map) permissionElement;
        String className = (String) elementMap.get("className");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) elementMap.get("properties");

        //convert it to plugin
        PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        UserviewPermission plugin = (UserviewPermission) pm.getPlugin(className);
        if (!(plugin instanceof FormPermission)) {
            return !isAnonymous;
        }

        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        ExtDirectoryManager dm = (ExtDirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
        String username = workflowUserManager.getCurrentUsername();
        User user = dm.getUserByUsername(username);

        plugin.setProperties(properties);
        plugin.setCurrentUser(user);
        plugin.setRequestParameters(requestParameters);

        return plugin.isAuthorize();
    }
}

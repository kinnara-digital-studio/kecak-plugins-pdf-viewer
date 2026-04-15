package com.kinnarastudio.kecakplugins.pdfviewer.userview;

import com.kinnarastudio.kecakplugins.pdfviewer.util.PdfUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class PdfUploadMenu extends UserviewMenu implements PdfUtils, PluginWebSupport {
    public final static String LABEL = "PDF Resize Menu";

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

        String htmlContent = pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/PdfUploadMenu.ftl", null);
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
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PdfUploadMenu.json", null, true, null);
    }

    @Override
    public boolean getHtmlEmbed(WorkflowAssignment assignment) {
        return "true".equalsIgnoreCase(AppUtil.processHashVariable(getPropertyString("htmlEmbed"), assignment, null, null));
    }

    @Override
    public String getPdfUrl(WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(getPropertyString("pdfUrl"), assignment, null, null);
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 1. Check if the request is a POST (usually used for file uploads)
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            try {
                // 2. Get the file part from the AJAX request
                Part filePart = request.getPart("pdfFile");
                File tempFile = null;
                if (filePart != null) {


                    // 1. Create a unique temporary file on the server
                    String originalName = filePart.getSubmittedFileName();
                    tempFile = File.createTempFile("compress_", "_" + originalName);

                    // 2. Transfer the uploaded data into the temp file
                    filePart.write(tempFile.getAbsolutePath());

                    InputStream fileContent = filePart.getInputStream();

                    // 3. Call your Utils class to process the PDF
                    String compressionLevel = getPropertyString("compressionLevel");
                    boolean enableWatermark = "true".equals(getPropertyString("enableWatermark"));
                    String watermarkText = getPropertyString("watermarkText");
                    compressPdf(tempFile, compressionLevel, enableWatermark, watermarkText);

                    // 4. Set response headers for PDF streaming
                    response.setContentType("application/pdf");
                    response.setHeader("Content-Disposition", "inline; filename=preview.pdf");
                    response.setContentLength((int) tempFile.length());

                    // 5. Write the byte array to the response output stream
//                    response.getOutputStream().write(compressedPdf);
                    java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
                    response.getOutputStream().flush();
                }
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing PDF: " + e.getMessage());
            }
        } else {
            // Return 405 Method Not Allowed if not a POST request
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
}

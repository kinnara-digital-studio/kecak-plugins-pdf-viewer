package com.kinnarastudio.kecakplugins.pdfviewer.userview;

import com.kinnarastudio.kecakplugins.pdfviewer.util.PdfUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
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
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        MultipartHttpServletRequest multipartRequest = null;

        try {
            if (request instanceof MultipartHttpServletRequest  ) {
                multipartRequest = (MultipartHttpServletRequest) request;
            } else {
                // Get the existing resolver from Spring Application Context
                ApplicationContext ac = AppUtil.getApplicationContext();
                org.springframework.web.multipart.MultipartResolver resolver =
                        (org.springframework.web.multipart.MultipartResolver) ac.getBean("multipartResolver");

                if (resolver.isMultipart(request)) {
                    multipartRequest = resolver.resolveMultipart(request);
                }
            }

            if (multipartRequest != null) {
                handlePdfProcessing(multipartRequest, response);
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Multipart Request");
            }
        } catch (Exception e) {
            response.sendError(500, "Error: " + e.getMessage());
        } finally {
            // Clean up: If we resolved it manually, we should clean up the files
            if (multipartRequest != null && !(request instanceof MultipartHttpServletRequest)) {
                ApplicationContext ac = AppUtil.getApplicationContext();
                org.springframework.web.multipart.MultipartResolver resolver =
                        (org.springframework.web.multipart.MultipartResolver) ac.getBean("multipartResolver");
                resolver.cleanupMultipart(multipartRequest);
            }
        }
    }

    private void handlePdfProcessing(MultipartHttpServletRequest multipartRequest, HttpServletResponse response) throws Exception {
        File tempFile = null;
        try {
            MultipartFile mFile = multipartRequest.getFile("pdfFile");
            if (mFile != null && !mFile.isEmpty()) {
                tempFile = File.createTempFile("compress_", ".pdf");
                mFile.transferTo(tempFile);

                // Level from menu properties
                String level = getPropertyString("compressionLevel");
                level = (level == null || level.isEmpty()) ? "medium" : level;

                // Your utility method
                byte[] compressedResult = compressPdfToBytes(mFile.getInputStream(), level, true, "PREVIEW");

//                response.setContentType("application/pdf");
//                response.setContentLength((int) tempFile.length());
//                java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
//                response.getOutputStream().flush();
                // Set headers strictly
                response.reset();
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "inline; filename=\"preview.pdf\"");
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                response.setContentLength(compressedResult.length);

                try (OutputStream os = response.getOutputStream()) {
                    os.write(compressedResult);
                    os.flush();
                }

//                // Stream the file bits
//                try (FileInputStream fis = new FileInputStream(tempFile);
//                     OutputStream os = response.getOutputStream()) {
//                    byte[] buffer = new byte[4096];
//                    int bytesRead;
//                    while ((bytesRead = fis.read(buffer)) != -1) {
//                        os.write(buffer, 0, bytesRead);
//                    }
//                    os.flush();
//                }
//
//                java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
                // Delete temp file after streaming
//                tempFile.delete();
            }
        } catch (Exception e) {
            // Log the error so you can see it in catalina.out
            e.printStackTrace();
            if (!response.isCommitted()) {
                response.sendError(500, "PDF Error: " + e.getMessage());
            }
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

//    @Override
//    public void webService(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//
//        // Log the hit to ensure the URL is correct (check catalina.out / joget.log)
//        System.out.println("PdfUploadMenu: WebService hit");
//
//        // 1. Check/Wrap Multipart Request
//        MultipartHttpServletRequest multipartRequest = null;
//        if (request instanceof MultipartHttpServletRequest) {
//            multipartRequest = (MultipartHttpServletRequest) request;
//        } else {
//            // Fallback: Use Spring's resolver to wrap the raw request
//            org.springframework.web.multipart.commons.CommonsMultipartResolver resolver =
//                    new org.springframework.web.multipart.commons.CommonsMultipartResolver(request.getSession().getServletContext());
//            if (resolver.isMultipart(request)) {
//                multipartRequest = resolver.resolveMultipart(request);
//            }
//        }
//
//        if (multipartRequest != null) {
//            File tempFile = null;
//            try {
//                MultipartFile mFile = multipartRequest.getFile("pdfFile");
//
//                if (mFile != null && !mFile.isEmpty()) {
//                    tempFile = File.createTempFile("compress_", ".pdf");
//                    mFile.transferTo(tempFile);
//
//                    // Use properties from your menu configuration
//                    String level = getPropertyString("compressionLevel");
//                    if(level == null || level.isEmpty()) level = "medium";
//
//                    // Call your utility
//                    compressPdf(tempFile, level, true, "PREVIEW");
//
//                    // Stream back
//                    response.setContentType("application/pdf");
//                    response.setHeader("Content-Disposition", "inline; filename=preview.pdf");
//                    response.setContentLength((int) tempFile.length());
//
//                    java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
//                    response.getOutputStream().flush();
//                } else {
//                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file uploaded");
//                }
//            } catch (Exception e) {
//                e.printStackTrace(); // Log the actual stack trace to the server console
//                response.sendError(500, "Processing failed: " + e.getMessage());
//            } finally {
//                if (tempFile != null && tempFile.exists()) tempFile.delete();
//            }
//        } else {
//            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a multipart request");
//        }
//    }

//    @Override
//    public void webService(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//
//        // 1. Check if it's a multipart request
//        if (request instanceof MultipartHttpServletRequest ||
//                request.getContentType() != null && request.getContentType().contains("multipart/form-data")) {
//
//            File tempFile = null;
//            try {
//                // 2. Cast to MultipartHttpServletRequest (Spring-friendly)
//                MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
//                MultipartFile mFile = multipartRequest.getFile("pdfFile");
//
//                if (mFile != null && !mFile.isEmpty()) {
//                    // 3. Create temp file
//                    tempFile = File.createTempFile("compress_", ".pdf");
//
//                    // 4. Transfer the file data
//                    mFile.transferTo(tempFile);
//
//                    // 5. Call your existing utility
//                    compressPdf(tempFile, "medium", true, "PREVIEW");
//
//                    // 6. Stream back to browser
//                    response.setContentType("application/pdf");
//                    response.setHeader("Content-Disposition", "inline; filename=preview.pdf");
//                    response.setContentLength((int) tempFile.length());
//                    java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
//                    response.getOutputStream().flush();
//                }
//            } catch (Exception e) {
//                response.sendError(500, "Processing failed: " + e.getMessage());
//            } finally {
//                if (tempFile != null && tempFile.exists()) tempFile.delete();
//            }
//        }
//    }

//    @Override
//    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        // 1. Check if the request is a POST (usually used for file uploads)
//        if ("POST".equalsIgnoreCase(request.getMethod())) {
//            try {
//                // 2. Get the file part from the AJAX request
//                Part filePart = request.getPart("pdfFile");
//                File tempFile = null;
//                if (filePart != null) {
//
//
//                    // 1. Create a unique temporary file on the server
//                    String originalName = filePart.getSubmittedFileName();
//                    tempFile = File.createTempFile("compress_", "_" + originalName);
//
//                    // 2. Transfer the uploaded data into the temp file
//                    filePart.write(tempFile.getAbsolutePath());
//
//                    InputStream fileContent = filePart.getInputStream();
//
//                    // 3. Call your Utils class to process the PDF
//                    String compressionLevel = getPropertyString("compressionLevel");
//                    boolean enableWatermark = "true".equals(getPropertyString("enableWatermark"));
//                    String watermarkText = getPropertyString("watermarkText");
//                    compressPdf(tempFile, compressionLevel, enableWatermark, watermarkText);
//
//                    // 4. Set response headers for PDF streaming
//                    response.setContentType("application/pdf");
//                    response.setHeader("Content-Disposition", "inline; filename=preview.pdf");
//                    response.setContentLength((int) tempFile.length());
//
//                    // 5. Write the byte array to the response output stream
////                    response.getOutputStream().write(compressedPdf);
//                    java.nio.file.Files.copy(tempFile.toPath(), response.getOutputStream());
//                    response.getOutputStream().flush();
//                }
//            } catch (Exception e) {
//                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing PDF: " + e.getMessage());
//            }
//        } else {
//            // Return 405 Method Not Allowed if not a POST request
//            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
//        }
//    }
}

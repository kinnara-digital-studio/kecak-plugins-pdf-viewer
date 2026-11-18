package com.kinnara.kecakplugins.pdfviewer;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.kecak.apps.form.model.AceFormElement;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

public class MultiSourcePdfViewerElement extends Element implements FileDownloadSecurity, FormBuilderPaletteElement, AceFormElement, PdfUtils, PluginWebSupport {
    private static final String LABEL = "Multi-source PDF Viewer Element";

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "PdfViewerElement.ftl";

        dataModel.put("className", getClassName());
        dataModel.put("src", getElementValue(formData));

        FormUtil.setReadOnlyProperty(this);

        return FormUtil.generateElementHtml(this, formData, template, dataModel);
    }

    @Override
    public String renderAceTemplate(FormData formData, Map dataModel) {
        String template = "AcePdfViewerElement.ftl";

        dataModel.put("className", getClassName());
        dataModel.put("src", getElementValue(formData));
        dataModel.put("ratio", getPropertyString("ratio"));

        return FormUtil.generateElementHtml(this, formData, template, dataModel);
    }

    @Override
    public Object handleElementValueResponse(Element element, FormData formData) {
        return getElementValue(formData);
    }

    /**
     * Menghasilkan nilai src (Base64 data URI atau Streaming Web Service URL)
     */
    protected String getElementValue(FormData formData) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment workflowAssignment = Optional.ofNullable(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);

        boolean htmlEmbed = getHtmlEmbed(workflowAssignment);
        if (htmlEmbed) {
            byte[] mergedPdf = getMergedPdfBytes(workflowAssignment);
            if (mergedPdf != null && mergedPdf.length > 0) {
                return "data:application/pdf;base64," + Base64.getEncoder().encodeToString(mergedPdf);
            }
            return "";
        } else {
            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            String contextPath = (request != null) ? request.getContextPath() : "";
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String appId = (appDef != null) ? appDef.getId() : "";
            String appVersion = (appDef != null) ? appDef.getVersion().toString() : "";
            String primaryKeyValue = (formData != null && formData.getPrimaryKeyValue() != null) ? formData.getPrimaryKeyValue() : "";

            Form rootForm = FormUtil.findRootForm(this);
            String formDefId = (rootForm != null) ? rootForm.getPropertyString(FormUtil.PROPERTY_ID) : "";
            String elementId = getPropertyString(FormUtil.PROPERTY_ID);

            return contextPath + "/web/json/app/" + appId + "/" + appVersion + "/plugin/" + getClassName()
                    + "/service?id=" + primaryKeyValue + "&formDefId=" + formDefId + "&elementId=" + elementId;
        }
    }

    /**
     * Membaca daftar URL dari properti Grid "source" dan melakukan resolve Hash Variable
     */
    public List<String> getSourceUrls(WorkflowAssignment assignment) {
        List<String> list = new ArrayList<>();
        Object source = getProperty("source");
        if (source instanceof Object[]) {
            for (Object obj : (Object[]) source) {
                if (obj instanceof Map) {
                    Object urlObj = ((Map<?, ?>) obj).get("pdfUrl");
                    if (urlObj != null) {
                        String rawUrl = urlObj.toString().trim();
                        if (!rawUrl.isEmpty()) {
                            String processed = AppUtil.processHashVariable(rawUrl, assignment, null, null);
                            if (processed != null && !processed.trim().isEmpty()) {
                                list.add(processed.trim());
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * Mengambil dan menggabungkan byte PDF dari semua sumber
     */
    public byte[] getMergedPdfBytes(WorkflowAssignment assignment) {
        List<String> urls = getSourceUrls(assignment);
        if (urls.isEmpty()) {
            return new byte[0];
        }

        List<byte[]> pdfList = new ArrayList<>();
        for (String url : urls) {
            byte[] bytes = fetchPdfBytes(url);
            if (bytes != null && bytes.length > 0) {
                pdfList.add(bytes);
            }
        }

        if (pdfList.isEmpty()) {
            return new byte[0];
        }

        try {
            return mergePdfs(pdfList);
        } catch (IOException e) {
            LogUtil.error(getClassName(), e, "Error merging PDFs: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Menggabungkan list PDF bytes menggunakan Apache PDFBox
     */
    public byte[] mergePdfs(List<byte[]> pdfList) throws IOException {
        if (pdfList == null || pdfList.isEmpty()) {
            return new byte[0];
        }
        if (pdfList.size() == 1) {
            return pdfList.get(0);
        }

        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        List<InputStream> openStreams = new ArrayList<>();
        try {
            for (byte[] bytes : pdfList) {
                if (bytes != null && bytes.length > 0) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    openStreams.add(bais);
                    pdfMerger.addSource(bais);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfMerger.setDestinationStream(baos);
            pdfMerger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            return baos.toByteArray();
        } finally {
            for (InputStream is : openStreams) {
                try {
                    is.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Fetch PDF bytes dari URL (mendukung full HTTP URL, relative Joget URL, file lokal, dan base64)
     */
    protected byte[] fetchPdfBytes(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.trim().isEmpty()) {
            return null;
        }
        pdfUrl = pdfUrl.trim();

        // 1. Data URI
        if (pdfUrl.startsWith("data:application/pdf;base64,")) {
            try {
                String base64Data = pdfUrl.substring("data:application/pdf;base64,".length()).trim();
                return Base64.getDecoder().decode(base64Data);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Failed to decode base64 data URI");
                return null;
            }
        }

        // 2. File Lokal
        File localFile = new File(pdfUrl);
        if (localFile.exists() && localFile.isFile() && isPdfFile(localFile)) {
            try (InputStream is = new FileInputStream(localFile)) {
                return IOUtils.toByteArray(is);
            } catch (IOException e) {
                LogUtil.error(getClassName(), e, "Failed to read local PDF file: " + pdfUrl);
            }
        }

        // 3. HTTP / Relative URL
        HttpServletRequest currentRequest = WorkflowUtil.getHttpServletRequest();
        String fullUrl = buildFullUrl(pdfUrl, currentRequest);

        try {
            HttpClient client = getHttpClient();
            HttpGet httpGet = new HttpGet(fullUrl);

            // Forward session cookies & authorization agar hak akses Joget tetap terbawa
            if (currentRequest != null) {
                String cookieHeader = currentRequest.getHeader("Cookie");
                if (cookieHeader != null && !cookieHeader.isEmpty()) {
                    httpGet.setHeader("Cookie", cookieHeader);
                }
                String authHeader = currentRequest.getHeader("Authorization");
                if (authHeader != null && !authHeader.isEmpty()) {
                    httpGet.setHeader("Authorization", authHeader);
                }
            }

            HttpResponse response = client.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (InputStream is = entity.getContent()) {
                        byte[] bytes = IOUtils.toByteArray(is);
                        if (isPdfBytes(bytes)) {
                            return bytes;
                        } else {
                            LogUtil.warn(getClassName(), "Content from URL [" + fullUrl + "] is not a valid PDF header");
                        }
                    }
                }
            } else {
                LogUtil.warn(getClassName(), "HTTP status " + statusCode + " fetching PDF from URL: " + fullUrl);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Exception fetching PDF from URL [" + fullUrl + "]: " + e.getMessage());
        }

        return null;
    }

    private boolean isPdfBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private boolean isPdfFile(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 4) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            return fis.read(magic) == 4 && magic[0] == '%' && magic[1] == 'P' && magic[2] == 'D' && magic[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private String buildFullUrl(String url, HttpServletRequest request) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (request != null) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(serverName);
            if ((scheme.equalsIgnoreCase("http") && serverPort != 80) || (scheme.equalsIgnoreCase("https") && serverPort != 443)) {
                sb.append(":").append(serverPort);
            }
            if (!url.startsWith("/")) {
                sb.append("/");
            }
            sb.append(url);
            return sb.toString();
        }
        return url;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!isDownloadAllowed(request.getParameterMap())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String recordId = request.getParameter("id");
        String formDefId = request.getParameter("formDefId");
        String elementId = request.getParameter("elementId");

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Application definition could not be resolved");
            return;
        }

        WorkflowAssignment assignment = new WorkflowAssignment();
        if (recordId != null && !recordId.isEmpty()) {
            assignment.setProcessId(recordId);
        }

        byte[] mergedPdfBytes = null;
        if (formDefId != null && !formDefId.isEmpty() && elementId != null && !elementId.isEmpty()) {
            FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null && formDef.getJson() != null) {
                FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
                String formJson = AppUtil.processHashVariable(formDef.getJson(), assignment, StringUtil.TYPE_JSON, null, appDef);
                Form form = (Form) formService.createElementFromJson(formJson, false);
                FormData formData = new FormData();
                formData.setPrimaryKeyValue(recordId);
                formData.setProcessId(recordId);

                Element targetElement = FormUtil.findElement(elementId, form, formData);
                if (targetElement instanceof MultiSourcePdfViewerElement) {
                    mergedPdfBytes = ((MultiSourcePdfViewerElement) targetElement).getMergedPdfBytes(assignment);
                }
            }
        }

        if (mergedPdfBytes == null || mergedPdfBytes.length == 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No PDF content available to display");
            return;
        }

        response.reset();
        response.setContentType("application/pdf");
        boolean attachment = "true".equalsIgnoreCase(request.getParameter("attachment"));
        String disposition = attachment ? "attachment" : "inline";
        response.setHeader("Content-Disposition", disposition + "; filename=\"merged_" + (recordId != null ? recordId : "document") + ".pdf\"");
        response.setContentLength(mergedPdfBytes.length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(mergedPdfBytes);
            out.flush();
        }
    }

    @Override
    public boolean isDownloadAllowed(Map requestParameters) {
        final boolean isAnonymous = WorkflowUtil.isCurrentUserAnonymous();

        Object permissionElement = getProperty("permissionPlugin");
        if (permissionElement == null) {
            return !isAnonymous;
        }

        @SuppressWarnings("rawtypes")
        Map elementMap = (Map) permissionElement;
        String className = (String) elementMap.get("className");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) elementMap.get("properties");

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

    @Override
    public boolean getHtmlEmbed(WorkflowAssignment assignment) {
        return "true".equalsIgnoreCase(AppUtil.processHashVariable(getPropertyString("htmlEmbed"), assignment, null, null));
    }

    @Override
    public String getPdfUrl(WorkflowAssignment assignment) {
        List<String> urls = getSourceUrls(assignment);
        return urls.isEmpty() ? "" : urls.get(0);
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<img src='${request.contextPath}/plugin/PdfViewerElement/images/pdf-logo.png' width='320' height='320'/>";
    }

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
        return AppUtil.readPluginResource(getClassName(), "/properties/MultiSourcePdfViewerElement.json", null, true, null).replaceAll("\"", "'");
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = (resourceBundle != null && resourceBundle.containsKey("buildNumber"))
                ? resourceBundle.getString("buildNumber")
                : getClass().getPackage().getImplementationVersion();
        return (buildNumber != null) ? buildNumber : "1.0.0";
    }

    @Override
    public String getFormBuilderCategory() {
        return "Kecak";
    }

    @Override
    public String getFormBuilderIcon() {
        return null;
    }

    @Override
    public int getFormBuilderPosition() {
        return 99;
    }
}

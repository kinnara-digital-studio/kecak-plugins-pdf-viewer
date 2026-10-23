package com.kinnarastudio.kecakplugins.pdfviewer.app;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;

import javax.net.ssl.SSLContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * PDF Merger Web Service
 * Merges uploaded PDF file(s) and PDF Viewer content into a single PDF output.
 */
public class PdfMergerWebService extends DefaultApplicationPlugin implements PluginWebSupport {
    final private static String LABEL = "PDF Merger Web Service";

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
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public Object execute(Map map) {
        return null;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String recordId = optParameter(request, "id")
                .orElseGet(() -> optParameter(request, "recordId")
                .orElseGet(() -> optParameter(request, "primaryKeyValue")
                .orElse(null)));

        String formId = optParameter(request, "formId")
                .orElseGet(() -> optParameter(request, "formDefId")
                .orElse(null));

        if (recordId == null || recordId.trim().isEmpty() || formId == null || formId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter [id] and [formId] are required");
            return;
        }

        recordId = recordId.trim();
        formId = formId.trim();

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef == null) {
            String appId = optParameter(request, "appId").orElse(null);
            String appVersion = optParameter(request, "appVersion").orElse(null);
            if (appId != null && !appId.trim().isEmpty()) {
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                appDef = appService.getAppDefinition(appId, appVersion);
            }
        }

        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Application definition could not be resolved");
            return;
        }

        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        FormRowSet rowSet = appService.loadFormData(appDef.getId(), appDef.getVersion().toString(), formId, recordId);
        if (rowSet == null || rowSet.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No data record found for form [" + formId + "] and id [" + recordId + "]");
            return;
        }
        FormRow row = rowSet.get(0);

        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        FormDefinition formDef = formDefinitionDao.loadById(formId, appDef);
        if (formDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Form definition [" + formId + "] not found");
            return;
        }

        FormData formData = new FormData();
        formData.setPrimaryKeyValue(recordId);

        WorkflowAssignment assignment = new WorkflowAssignment();
        assignment.setProcessId(recordId);

        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        String formJson = formDef.getJson();
        if (formJson != null) {
            formJson = AppUtil.processHashVariable(formJson, assignment, StringUtil.TYPE_JSON, null, appDef);
        }
        Form form = (Form) formService.loadFormFromJson(formJson, formData);

        String tableName = (form != null) ? form.getPropertyString(FormUtil.PROPERTY_TABLE_NAME) : "";
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = formId;
        }

        List<Element> elementList = new ArrayList<>();
        if (form != null) {
            collectElements(form, elementList);
        }

        List<PdfSource> pdfSources = new ArrayList<>();
        Set<String> processedFieldIds = new HashSet<>();

        for (Element element : elementList) {
            String fieldId = element.getPropertyString(FormUtil.PROPERTY_ID);

            // Check if element is PdfViewerElement or has pdfUrl property
            String rawPdfUrl = element.getPropertyString("pdfUrl");
            if (rawPdfUrl != null && !rawPdfUrl.trim().isEmpty()) {
                String resolvedPdfUrl = AppUtil.processHashVariable(rawPdfUrl, assignment, null, null, appDef);
                if (resolvedPdfUrl != null && !resolvedPdfUrl.trim().isEmpty()) {
                    byte[] pdfBytes = fetchPdfFromUrl(resolvedPdfUrl, request);
                    if (pdfBytes != null && pdfBytes.length > 0) {
                        pdfSources.add(new PdfSource("PdfViewer-" + fieldId, pdfBytes));
                    }
                }
                if (fieldId != null && !fieldId.trim().isEmpty()) {
                    processedFieldIds.add(fieldId);
                }
                continue;
            }

            // Check if element is a File Upload or form field containing uploaded PDF
            if (fieldId != null && !fieldId.trim().isEmpty()) {
                processedFieldIds.add(fieldId);
                String fieldValue = row.getProperty(fieldId);
                if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                    String[] fileNames = fieldValue.split(";");
                    for (String fileName : fileNames) {
                        fileName = fileName.trim();
                        if (fileName.toLowerCase().endsWith(".pdf")) {
                            File f = findUploadedFile(fileName, fieldId, tableName, formId, recordId, appDef);
                            if (f != null && isPdfFile(f)) {
                                pdfSources.add(new PdfSource(fileName, f));
                            } else {
                                LogUtil.warn(getClassName(), "Could not find physical PDF file for: " + fileName);
                            }
                        }
                    }
                }
            }
        }

        // Fallback: check any remaining properties in row ending with .pdf
        for (String propName : row.stringPropertyNames()) {
            if (processedFieldIds.contains(propName)) continue;
            String val = row.getProperty(propName);
            if (val != null && !val.trim().isEmpty()) {
                String[] files = val.split(";");
                for (String fName : files) {
                    fName = fName.trim();
                    if (fName.toLowerCase().endsWith(".pdf")) {
                        File f = findUploadedFile(fName, propName, tableName, formId, recordId, appDef);
                        if (f != null && isPdfFile(f)) {
                            pdfSources.add(new PdfSource(fName, f));
                        }
                    }
                }
            }
        }

        if (pdfSources.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No PDF files found to merge for form [" + formId + "] and id [" + recordId + "]");
            return;
        }

        // Merge PDFs
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        List<InputStream> openStreams = new ArrayList<>();
        try {
            for (PdfSource source : pdfSources) {
                InputStream is = source.openStream();
                openStreams.add(is);
                pdfMerger.addSource(is);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfMerger.setDestinationStream(baos);
            pdfMerger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

            byte[] mergedPdfBytes = baos.toByteArray();

            response.reset();
            response.setContentType("application/pdf");
            boolean attachment = "true".equalsIgnoreCase(request.getParameter("attachment"));
            String disposition = attachment ? "attachment" : "inline";
            response.setHeader("Content-Disposition", disposition + "; filename=\"merged_" + recordId + ".pdf\"");
            response.setContentLength(mergedPdfBytes.length);

            try (OutputStream out = response.getOutputStream()) {
                out.write(mergedPdfBytes);
                out.flush();
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error while merging PDFs: " + e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to merge PDF: " + e.getMessage());
        } finally {
            for (InputStream is : openStreams) {
                try {
                    is.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void collectElements(Element element, List<Element> result) {
        if (element == null) return;
        result.add(element);
        Collection<Element> children = element.getChildren();
        if (children != null) {
            for (Element child : children) {
                collectElements(child, result);
            }
        }
    }

    private File findUploadedFile(String fileName, String fieldId, String tableName, String formId, String recordId, AppDefinition appDef) {
        String cleanFileName = SecurityUtil.normalizedFileName(fileName);
        if (cleanFileName.contains("/") || cleanFileName.contains("\\")) {
            cleanFileName = new File(cleanFileName).getName();
        }

        // 1. Check via FileUtil.getFile with tableName
        if (tableName != null && !tableName.isEmpty()) {
            try {
                File f = FileUtil.getFile(cleanFileName, tableName, recordId);
                if (isPdfFile(f)) return f;
            } catch (Exception ignored) {}

            try {
                String altTable = tableName.startsWith("app_fd_") ? tableName.substring(7) : "app_fd_" + tableName;
                File f = FileUtil.getFile(cleanFileName, altTable, recordId);
                if (isPdfFile(f)) return f;
            } catch (Exception ignored) {}
        }

        // 2. Check via FileUtil.getFile with formId
        if (formId != null && !formId.isEmpty()) {
            try {
                File f = FileUtil.getFile(cleanFileName, formId, recordId);
                if (isPdfFile(f)) return f;
            } catch (Exception ignored) {}
        }

        // 3. Check with fieldId subfolder
        if (fieldId != null && !fieldId.isEmpty()) {
            try {
                File f = FileUtil.getFile(fieldId + File.separator + cleanFileName, tableName, recordId);
                if (isPdfFile(f)) return f;
            } catch (Exception ignored) {}

            try {
                File f = FileUtil.getFile(fieldId + File.separator + cleanFileName, formId, recordId);
                if (isPdfFile(f)) return f;
            } catch (Exception ignored) {}
        }

        // 4. Check via FileManager.getFileByPath
        try {
            File f = FileManager.getFileByPath(cleanFileName);
            if (isPdfFile(f)) return f;
        } catch (Exception ignored) {}

        // 5. Check direct paths under SetupManager.getBaseDirectory()
        String formUploadPath = SetupManager.getBaseDirectory();
        try {
            SetupManager setupManager = (SetupManager) AppUtil.getApplicationContext().getBean("setupManager");
            if (setupManager != null) {
                String dataFileBasePath = setupManager.getSettingValue("dataFileBasePath");
                if (dataFileBasePath != null && !dataFileBasePath.isEmpty()) {
                    formUploadPath = dataFileBasePath;
                }
            }
        } catch (Exception ignored) {}

        File permDirBase = new File(formUploadPath, "app_formuploads");
        String[] folderCandidates = new String[] {
                tableName,
                tableName.startsWith("app_fd_") ? tableName.substring(7) : "app_fd_" + tableName,
                formId,
                appDef != null ? appDef.getId() + File.separator + tableName : null,
                appDef != null ? appDef.getId() + File.separator + formId : null
        };

        for (String folder : folderCandidates) {
            if (folder == null) continue;
            File targetFolder = new File(permDirBase, folder + File.separator + recordId);
            File candidateFile = new File(targetFolder, cleanFileName);
            if (isPdfFile(candidateFile)) return candidateFile;

            if (fieldId != null && !fieldId.isEmpty()) {
                File subFile = new File(targetFolder, fieldId + File.separator + cleanFileName);
                if (isPdfFile(subFile)) return subFile;
            }
        }

        return null;
    }

    private byte[] fetchPdfFromUrl(String pdfUrl, HttpServletRequest request) {
        if (pdfUrl == null || pdfUrl.trim().isEmpty()) {
            return null;
        }
        pdfUrl = pdfUrl.trim();

        // Data URI
        if (pdfUrl.startsWith("data:application/pdf;base64,")) {
            try {
                String base64Data = pdfUrl.substring("data:application/pdf;base64,".length()).trim();
                return Base64.getDecoder().decode(base64Data);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Failed to decode base64 data URI");
                return null;
            }
        }

        // Local file check
        File localFile = new File(pdfUrl);
        if (isPdfFile(localFile)) {
            try {
                return IOUtils.toByteArray(new FileInputStream(localFile));
            } catch (IOException ignored) {}
        }

        // Build target URL if relative
        String fullUrl;
        if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
            fullUrl = pdfUrl;
        } else {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(serverName);
            if ((scheme.equalsIgnoreCase("http") && serverPort != 80) || (scheme.equalsIgnoreCase("https") && serverPort != 443)) {
                sb.append(":").append(serverPort);
            }
            if (!pdfUrl.startsWith("/")) {
                sb.append("/");
            }
            sb.append(pdfUrl);
            fullUrl = sb.toString();
        }

        try {
            HttpClient client = getHttpClient();
            HttpGet httpGet = new HttpGet(fullUrl);

            // Forward session cookies and authorization
            String cookieHeader = request.getHeader("Cookie");
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                httpGet.setHeader("Cookie", cookieHeader);
            }
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.isEmpty()) {
                httpGet.setHeader("Authorization", authHeader);
            }

            HttpResponse httpResponse = client.execute(httpGet);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    try (InputStream is = entity.getContent()) {
                        byte[] bytes = IOUtils.toByteArray(is);
                        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
                            return bytes;
                        } else {
                            LogUtil.warn(getClassName(), "Content fetched from URL [" + fullUrl + "] is not a valid PDF");
                        }
                    }
                }
            } else {
                LogUtil.warn(getClassName(), "HTTP error " + statusCode + " fetching PDF from URL: " + fullUrl);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Exception fetching PDF from URL: " + fullUrl);
        }

        return null;
    }

    private boolean isPdfFile(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 4) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            if (fis.read(magic) == 4) {
                return magic[0] == '%' && magic[1] == 'P' && magic[2] == 'D' && magic[3] == 'F';
            }
        } catch (IOException e) {
            LogUtil.warn(getClassName(), "Error reading file header: " + file.getAbsolutePath());
        }
        return false;
    }

    private HttpClient getHttpClient() {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
            return HttpClients.custom().setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
            return HttpClientBuilder.create().build();
        }
    }

    private Optional<String> optParameter(HttpServletRequest request, String name) {
        return Optional.ofNullable(name).map(request::getParameter);
    }

    private static class PdfSource {
        private final String name;
        private final File file;
        private final byte[] bytes;

        public PdfSource(String name, File file) {
            this.name = name;
            this.file = file;
            this.bytes = null;
        }

        public PdfSource(String name, byte[] bytes) {
            this.name = name;
            this.file = null;
            this.bytes = bytes;
        }

        public InputStream openStream() throws IOException {
            if (file != null) {
                return new FileInputStream(file);
            } else if (bytes != null) {
                return new ByteArrayInputStream(bytes);
            }
            throw new IOException("Empty source for: " + name);
        }
    }
}

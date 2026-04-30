package com.kinnarastudio.kecakplugins.pdfviewer.form;

import com.kinnarastudio.kecakplugins.pdfviewer.util.PdfUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.Permission;
import org.joget.apps.userview.model.PwaOfflineResources;
import org.joget.commons.util.*;
import org.joget.directory.model.User;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.form.service.FormDataUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * @author fina
 *
 * Pdf Upload Element
 */
public class PdfUploadElement extends Element implements FileDownloadSecurity, FormBuilderPaletteElement, PdfUtils, PluginWebSupport, PwaOfflineResources {

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
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "PDF Resize Upload";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "PdfUploadElement.ftl";

        // set value
        String[] values = FormUtil.getElementPropertyValues(this, formData);

        //check is there a stored value
        String storedValue = formData.getStoreBinderDataProperty(this);
        if (storedValue != null) {
            values = storedValue.split(";");
        }

        Map<String, String> tempFilePaths = new LinkedHashMap<String, String>();
        Map<String, String> filePaths = new LinkedHashMap<String, String>();

        String primaryKeyValue = getPrimaryKeyValue(formData);
        String filePathPostfix = "_path";
        String id = FormUtil.getElementParameterName(this);
        String[] tempExisting = formData.getRequestParameterValues(id + filePathPostfix);

        if (tempExisting != null && tempExisting.length > 0) {
            values = tempExisting;
        }

        String formDefId = "";
        Form form = FormUtil.findRootForm(this);
        if (form != null) {
            formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);
        }
        String appId = "";
        String appVersion = "";

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        if (appDef != null) {
            appId = appDef.getId();
            appVersion = appDef.getVersion().toString();
        }

        for (String value : values) {
            // check if the file is in temp file
            File file = FileManager.getFileByPath(value);

            if (file != null) {
                tempFilePaths.put(value, file.getName());
            } else if (value != null && !value.isEmpty()) {
                // determine actual path for the file uploads
                String fileName = value;
                String encodedFileName = fileName;
                if (fileName != null) {
                    try {
                        encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
                    } catch (UnsupportedEncodingException ex) {
                        // ignore
                    }
                }

                String filePath = "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + primaryKeyValue + "/" + encodedFileName + ".";
                if (Boolean.valueOf(getPropertyString("attachment")).booleanValue()) {
                    filePath += "?attachment=true";
                }
                filePaths.put(filePath, value);
            }
        }

        if (!tempFilePaths.isEmpty()) {
            dataModel.put("tempFilePaths", tempFilePaths);
        }
        if (!filePaths.isEmpty()) {
            dataModel.put("filePaths", filePaths);
        }
        dataModel.put("className", getClassName());
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
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
        return "<img src='${request.contextPath}/plugin/${className}/images/pdf-logo.png' width='320' height='320' />";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PdfUploadElement.json", null, true, null).replaceAll("\"", "'");
    }

    public boolean getHtmlEmbed(WorkflowAssignment assignment) {
        return "true".equalsIgnoreCase(AppUtil.processHashVariable(getPropertyString("htmlEmbed"), assignment, null, null));
    }

    @Override
    public String getPdfUrl(WorkflowAssignment workflowAssignment) {
        return AppUtil.processHashVariable(getPropertyString("pdfUrl"), workflowAssignment, null, null);
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
    public FormData formatDataForValidation(FormData formData) {
        String filePathPostfix = "_path";
        String id = FormUtil.getElementParameterName(this);
        if (id != null) {
            String[] tempFilenames = formData.getRequestParameterValues(id);
            String[] tempExisting = formData.getRequestParameterValues(id + filePathPostfix);

            List<String> filenames = new ArrayList<String>();
            if (tempFilenames != null && tempFilenames.length > 0) {
                filenames.addAll(Arrays.asList(tempFilenames));
            }

            if (tempExisting != null && tempExisting.length > 0) {
                filenames.addAll(Arrays.asList(tempExisting));
            }

            if (filenames.isEmpty()) {
                formData.addRequestParameterValues(id, new String[]{""});
            } else if (!"true".equals(getPropertyString("multiple"))) {
                formData.addRequestParameterValues(id, new String[]{filenames.get(0)});
            } else {
                formData.addRequestParameterValues(id, filenames.toArray(new String[]{}));
            }
        }
        return formData;
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        FormRowSet rowSet = null;

        String id = getPropertyString(FormUtil.PROPERTY_ID);

        Set<String> remove = null;
        if ("true".equals(getPropertyString("removeFile"))) {
            remove = new HashSet<String>();
            Form form = FormUtil.findRootForm(this);
            String originalValues = formData.getLoadBinderDataProperty(form, id);
            if (originalValues != null) {
                remove.addAll(Arrays.asList(originalValues.split(";")));
            }
        }

        // get value
        if (id != null) {
            String[] values = FormUtil.getElementPropertyValues(this, formData);
            if (values != null && values.length > 0) {
                // set value into Properties and FormRowSet object
                FormRow result = new FormRow();
                List<String> resultedValue = new ArrayList<String>();
                List<String> filePaths = new ArrayList<String>();

                for (String value : values) {
                    // check if the file is in temp file
                    File file = FileManager.getFileByPath(value);
                    String compressionLevel = getPropertyString("compressionLevel");
                    boolean enableWatermark = "true".equals(getPropertyString("enableWatermark"));
                    String watermarkText = getPropertyString("watermarkText");

                    if (file.getName().toLowerCase().endsWith(".pdf") && !"none".equals(compressionLevel)) {
                        // --- PDF PROCESSING START ---
                         try {
                            LogUtil.info(getClassName(), "Compressing large PDF: " + file.getName() + " (" + (file.length() / 1024 / 1024) + "MB)");
                            compressPdf(file, compressionLevel, enableWatermark, watermarkText);
                            LogUtil.info(getClassName(), "Compression complete. New size: " + (file.length() / 1024 / 1024) + "MB");
                            } catch (Exception e) {
                                LogUtil.error(getClassName(), e, "Failed to process PDF: " + file.getName());
                            }
                        // --- PDF PROCESSING END ---

                        filePaths.add(value);
                        resultedValue.add(file.getName());
                    } else {
                        if (remove != null && !value.isEmpty()) {
                            remove.remove(value);
                        }
                        resultedValue.add(value);
                    }
                }

                if (!filePaths.isEmpty()) {
                    result.putTempFilePath(id, filePaths.toArray(new String[]{}));
                }

                if (remove != null) {
                    result.putDeleteFilePath(id, remove.toArray(new String[]{}));
                }

                // formulate values
                String delimitedValue = FormUtil.generateElementPropertyValues(resultedValue.toArray(new String[]{}));
                String paramName = FormUtil.getElementParameterName(this);
                formData.addRequestParameterValues(paramName, resultedValue.toArray(new String[]{}));

                // set value into Properties and FormRowSet object
                result.setProperty(id, delimitedValue);
                rowSet = new FormRowSet();
                rowSet.add(result);

                String filePathPostfix = "_path";
                formData.addRequestParameterValues(id + filePathPostfix, new String[]{});
            }
        }

        return rowSet;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String filePath = request.getParameter("_path");

        // HANDLE FILE UPLOAD (POST)
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            try {
                handlePostUpload(request, response);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        // 4. HANDLE PREVIEW REQUEST (GET with _path)
        else if (filePath != null && !filePath.isEmpty()) {
            handleGetPreview(request, response, filePath);
        }
    }

    /**
     * Handles the AJAX File Upload, Processes PDF, and returns JSON.
     */
    private void handlePostUpload(HttpServletRequest request, HttpServletResponse response) throws IOException, JSONException {
        response.setContentType("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            // Use Joget's AppUtil to catch the multipart request
            MultipartFile file = FileStore.getFile("pdfFile");

            if (file != null && !file.isEmpty()) {
                // --- STEP A: PDF PROCESSING (Compression/Watermark) ---
                 byte[] processedBytes = compressPdfToBytes(file.getInputStream(), "medium", true, "PREVIEW");

                // --- STEP B: STORE FILE ---
                String path = storeByteArray(processedBytes, "compressed_" + file.getOriginalFilename());

                // --- STEP C: RETURN JSON ---
                jsonResponse.put("path", path);
                jsonResponse.put("filename", file.getOriginalFilename());
                jsonResponse.put("status", "success");

                LogUtil.info(getClassName(), "File processed and stored at: " + path);
            } else {
                jsonResponse.put("error", "No file found in parameter: pdfFile");
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error during PDF upload");
            jsonResponse.put("error", e.getMessage());
        } finally {
            FileStore.clear(); // Clean up the thread-local file store
        }

        response.getWriter().write(jsonResponse.toString());
        response.getWriter().flush();
    }

    /**
     * Store the byte array PDF data to the server.
     */
    private String storeByteArray(byte[] bytes, String fileName) throws IOException {
        String baseDir = FileManager.getBaseDirectory();
        String uuid = org.joget.commons.util.UuidGenerator.getInstance().getUuid();
        File folder = new File(baseDir + File.separator + uuid);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File targetFile = new File(folder, fileName);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(bytes);
            fos.flush();
        }

        return uuid + File.separator + fileName;
    }

    /**
     * Streams the binary PDF data to the iframe for preview.
     */
    private void handleGetPreview(HttpServletRequest request, HttpServletResponse response, String filePath) throws IOException {
        // Prevent directory traversal attacks
        String normalizedPath = SecurityUtil.normalizedFileName(filePath);
        File file = FileManager.getFileByPath(normalizedPath);

        String appId = request.getParameter("_appId");
        String appVersion = request.getParameter("_appVersion");
        String formDefId = request.getParameter("_formId"); // ID Form (misal: "form_data_user")
        String recordId = request.getParameter("_id");     // ID Record / Primary Key

        if (file == null || !file.exists()) {
            if (appId != null && formDefId != null && recordId != null && !recordId.isEmpty()) {
                file = FileUtil.getFile(normalizedPath, formDefId, recordId);
            }
        }

        if (file != null && file.exists()) {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            response.setContentLength((int) file.length());

            try (FileInputStream in = new FileInputStream(file);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[10240];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Preview file not found.");
        }
    }

    /**
     * Telusuri semua wrapper layer untuk menemukan MultipartFile
     */
    private MultipartFile resolveMultipartFile(HttpServletRequest request) {
        HttpServletRequest current = request;

        // Loop: unwrap semua layer wrapper
        while (current != null) {
            LogUtil.info(getClassName(), "Checking request type: " + current.getClass().getName());

            // Cek apakah layer ini adalah MultipartHttpServletRequest
            if (current instanceof MultipartHttpServletRequest) {
                MultipartHttpServletRequest multipart = (MultipartHttpServletRequest) current;
                MultipartFile mFile = multipart.getFile("pdfFile");
                if (mFile != null && !mFile.isEmpty()) {
                    LogUtil.info(getClassName(), "Found via wrapper: " + current.getClass().getName());
                    return mFile;
                }
            }

            // Unwrap satu layer (HttpServletRequestWrapper → getRequest())
            if (current instanceof javax.servlet.http.HttpServletRequestWrapper) {
                javax.servlet.ServletRequest inner =
                        ((javax.servlet.http.HttpServletRequestWrapper) current).getRequest();
                if (inner instanceof HttpServletRequest) {
                    current = (HttpServletRequest) inner;
                } else {
                    break;
                }
            } else {
                break; // Sudah sampai layer paling dalam
            }
        }

        // Fallback: coba Spring ApplicationContext resolver
        try {
            ApplicationContext ac = AppUtil.getApplicationContext();
            MultipartResolver resolver = (MultipartResolver) ac.getBean("multipartResolver");
            if (resolver.isMultipart(request)) {
                MultipartHttpServletRequest multipart = resolver.resolveMultipart(request);
                MultipartFile mFile = multipart.getFile("pdfFile");
                LogUtil.info(getClassName(), "Found via ApplicationContext resolver");
                return mFile;
            }
        } catch (Exception e) {
            LogUtil.warn(getClassName(), "Resolver fallback failed: " + e.getMessage());
        }

        return null;
    }

    private void handleProcessing(byte[] pdfBytes, HttpServletResponse response, HttpServletRequest request) throws Exception {
        String level = request.getParameter("level");
        LogUtil.info(getClassName(), "compressionLevel dari properties: [" + level + "]");

        if (level == null || level.isEmpty()) level = "medium";
        LogUtil.info(getClassName(), "level yang dipakai: [" + level + "]");

        boolean enableWatermark = "true".equalsIgnoreCase(request.getParameter("watermark"));
        String watermarkText = request.getParameter("watermarkText");
        if (watermarkText == null || watermarkText.isEmpty()) watermarkText = "PREVIEW";

        byte[] compressed = compressPdfToBytes(
                new ByteArrayInputStream(pdfBytes),
                level,
                enableWatermark,
                watermarkText
        );

        response.reset();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"preview.pdf\"");
        response.setContentLength(compressed.length);

        try (OutputStream os = response.getOutputStream()) {
            os.write(compressed);
            os.flush();
        }
    }

    @Override
    public Set<String> getOfflineStaticResources() {
        Set<String> urls = new HashSet<String>();
        String contextPath = AppUtil.getRequestContextPath();
        urls.add(contextPath + "/js/dropzone/dropzone.css");
        urls.add(contextPath + "/js/dropzone/dropzone.js");
        urls.add(contextPath + "/plugin/org.joget.apps.form.lib.FileUpload/js/jquery.fileupload.js");

        return urls;
    }

    @Override
    public String[] handleMultipartDataRequest(@Nonnull String[] values, @Nonnull Element element, @Nonnull FormData formData) {
        final String elementId = element.getPropertyString("id");

        List<String> filePathList = new ArrayList<>();

        try {
            MultipartFile[] fileStore = FileStore.getFiles(elementId);
            if (fileStore != null) {
                for (MultipartFile file : fileStore) {
                    final String filePath = FileManager.storeFile(file);
                    filePathList.add(filePath);
                }
            }
        } catch (FileLimitException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        if (filePathList.isEmpty()) {
            return FormUtil.getElementPropertyValues(element, formData);
        } else {
            return filePathList.toArray(new String[0]);
        }
    }

    @Override
    public String[] handleJsonDataRequest(@Nullable Object value, @Nonnull Element element, FormData formData) {
        String stringValue = value == null ? "" : value.toString();

        JSONArray jsonValue;
        try {
            jsonValue = new JSONArray(stringValue);
        } catch (JSONException e) {
            // handle if it is not an array
            jsonValue = new JSONArray();
            jsonValue.put(stringValue);
        }

        List<String> result = new ArrayList<>();
        for (int i = 0, size = jsonValue.length(); i < size; i++) {
            try {
                String data = jsonValue.getString(i);
                Matcher dataPattern = FormDataUtil.DATA_PATTERN.matcher(data);

                String tempFilePath;

                // as data uri
                if (dataPattern.find()) {
                    String contentType = dataPattern.group("mime");
                    String extension = contentType.split("/")[1];
                    String fileName = FormDataUtil.getFileName(dataPattern.group("properties"), extension);
                    String base64 = dataPattern.group("data");

                    // store in app_tempupload
                    MultipartFile multipartFile = FormDataUtil.decodeFile(fileName, contentType, base64.trim());
                    tempFilePath = FileManager.storeFile(multipartFile);
                } else {
                    tempFilePath = data;
                }

                // check if file really exist in app_tempupload or in current record
                if (FileManager.getFileByPath(tempFilePath) != null || FileUtil.getFile(tempFilePath, this, getPrimaryKeyValue(formData)).isFile()) {
                    result.add(tempFilePath);
                }

            } catch (JSONException | IOException e) {
                LogUtil.error(getClassName(), e, e.getMessage());
            }
        }

        // clean field for empty result
        if (result.isEmpty()) {
            result.add("");
        }

        return result.toArray(new String[0]);
    }

    @Override
    public Object handleElementValueResponse(@Nonnull Element element, @Nonnull FormData formData) throws JSONException {
        if (isReadOnlyLabel() || asAttachment(formData)) {
            return getFileDownloadLink(formData);
        } else {
            return FormUtil.getElementPropertyValue(this, formData);
        }
    }

    protected boolean isReadOnlyLabel() {
        return "true".equalsIgnoreCase(getPropertyString(FormUtil.PROPERTY_READONLY))
                && "true".equalsIgnoreCase(getPropertyString(FormUtil.PROPERTY_READONLY_LABEL));
    }

    protected boolean asAttachment(FormData formData) {
        return Boolean.parseBoolean(getPropertyString("attachment"))
                || "true".equalsIgnoreCase(formData.getRequestParameter(PARAMETER_AS_LINK));
    }

    protected String getFileDownloadLink(FormData formData) {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        // set value
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(fileName -> {
                    // determine actual path for the file uploads
                    String appId = appDefinition.getAppId();
                    long appVersion = appDefinition.getVersion();
                    String formDefId = Optional.ofNullable(FormUtil.findRootForm(this))
                            .map(new Function<Form, String>() {
                                @Override
                                public String apply(Form f) {
                                    return f.getPropertyString(FormUtil.PROPERTY_ID);
                                }
                            })
                            .orElse("");
                    String encodedFileName = fileName;
                    String primaryKeyValue = formData.getPrimaryKeyValue();
                    try {
                        encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
                    } catch (UnsupportedEncodingException ex) {
                        // ignore
                    }

                    String filePath = "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + primaryKeyValue + "/" + encodedFileName + ".";
                    if (asAttachment(formData)) {
                        filePath += "?attachment=true";
                    }

                    return filePath;
                })
                .collect(Collectors.joining(";"));
    }

    @Override
    public boolean isDownloadAllowed(Map requestParameters) {
        String permissionType = getPropertyString("permissionType");
        if (permissionType.equals("public")) {
            return true;
        } else if (permissionType.equals("custom")) {
            Object permissionElement = getProperty("permissionPlugin");
            if (permissionElement != null && permissionElement instanceof Map) {
                Map elementMap = (Map) permissionElement;
                String className = (String) elementMap.get("className");
                Map<String, Object> properties = (Map<String, Object>) elementMap.get("properties");

                //convert it to plugin
                PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
                Permission plugin = (Permission) pm.getPlugin(className);
                if (plugin != null && plugin instanceof FormPermission) {
                    WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
                    User user = workflowUserManager.getCurrentUser();

                    plugin.setProperties(properties);
                    plugin.setCurrentUser(user);
                    plugin.setRequestParameters(requestParameters);

                    return plugin.isAuthorize();
                }
            }
            return false;
        } else {
            return !WorkflowUtil.isCurrentUserAnonymous();
        }
    }
}

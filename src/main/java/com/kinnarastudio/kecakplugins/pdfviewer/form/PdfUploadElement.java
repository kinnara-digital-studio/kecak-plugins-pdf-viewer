package com.kinnarastudio.kecakplugins.pdfviewer.form;

import com.kinnarastudio.kecakplugins.pdfviewer.util.PdfUtils;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.Permission;
import org.joget.apps.userview.model.PwaOfflineResources;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.*;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
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
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
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
        return "PDF Upload Resize";
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

    /**
     * Compresses images within a PDF to reduce total file size.
     * Targets images > 500px and reduces them by 50% with 60% JPEG quality.
     */
//    private void compressPdf(File file, String level) throws IOException {
//        float scale;
//        float quality;
//
//        // Define settings based on user selection
//        switch (level) {
//            case "low":
//                scale = 0.8f;   // 80% of original size
//                quality = 0.8f; // 80% JPEG quality
//                break;
//            case "high":
//                scale = 0.4f;   // 40% of original size
//                quality = 0.4f; // 40% JPEG quality
//                break;
//            case "medium":
//            default:
//                scale = 0.6f;   // 60% of original size
//                quality = 0.6f; // 60% JPEG quality
//                break;
//        }
//
//        try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
//            for (PDPage page : document.getPages()) {
//                PDResources resources = page.getResources();
//                if (resources == null) continue;
//
//                for (COSName name : resources.getXObjectNames()) {
//                    if (resources.isImageXObject(name)) {
//                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
//                        BufferedImage rawImage = image.getImage();
//                        if (rawImage == null) continue;
//
//                        // Apply the scale factor
//                        int newWidth = Math.round(rawImage.getWidth() * scale);
//                        int newHeight = Math.round(rawImage.getHeight() * scale);
//
//                        // Skip if the image is already smaller than the target
//                        if (newWidth < 100) continue;
//
//                        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
//                        Graphics2D g = resizedImage.createGraphics();
//                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//                        g.drawImage(rawImage, 0, 0, newWidth, newHeight, null);
//                        g.dispose();
//
//                        // Apply the JPEG quality factor
//                        PDImageXObject compressedXObject = JPEGFactory.createFromImage(document, resizedImage, quality);
//                        resources.put(name, compressedXObject);
//                    }
//                }
//            }
//            document.save(file);
//        }
//    }

//    private void compressPdf(File file, String level, boolean watermark, String text) throws IOException {
//        float scale = 0.6f;
//        float quality = 0.6f;
//
//        // Map settings
//        if ("low".equals(level)) { scale = 0.8f; quality = 0.8f; }
//        else if ("high".equals(level)) { scale = 0.4f; quality = 0.4f; }
//
//        try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
//            for (PDPage page : document.getPages()) {
//                PDResources resources = page.getResources();
//
//                // 1. Image Compression
//                if (!"none".equals(level) && resources != null) {
//                    for (COSName name : resources.getXObjectNames()) {
//                        if (resources.isImageXObject(name)) {
//                            PDImageXObject image = (PDImageXObject) resources.getXObject(name);
//                            BufferedImage rawImage = image.getImage();
//                            if (rawImage != null && (rawImage.getWidth() > 500)) {
//                                int nW = Math.round(rawImage.getWidth() * scale);
//                                int nH = Math.round(rawImage.getHeight() * scale);
//
//                                BufferedImage resized = new BufferedImage(nW, nH, BufferedImage.TYPE_INT_ARGB);
//                                Graphics2D g = resized.createGraphics();
//                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//                                g.drawImage(rawImage, 0, 0, nW, nH, null);
//                                g.dispose();
//
//                                resources.put(name, JPEGFactory.createFromImage(document, resized, quality));
//                            }
//                        }
//                    }
//                }
//
//                // 2. Watermarking
//                if (watermark && text != null && !text.isEmpty()) {
//                    try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
//                        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
//                        gs.setNonStrokingAlphaConstant(0.3f); // 30% Opacity
//                        cs.setGraphicsStateParameters(gs);
//                        cs.beginText();
//                        cs.setFont(PDType1Font.HELVETICA_BOLD, 50);
//                        cs.setNonStrokingColor(Color.GRAY);
//
//                        float w = page.getMediaBox().getWidth();
//                        float h = page.getMediaBox().getHeight();
//                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), w/5, h/5));
//                        cs.showText(text);
//                        cs.endText();
//                    }
//                }
//            }
//            document.save(file);
//        }
//    }

//    private void compressPdfImages(File file) throws IOException {
//        // Use temp file for buffering to save JVM Heap Space
//        try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
//            for (PDPage page : document.getPages()) {
//                PDResources resources = page.getResources();
//                if (resources == null) continue;
//
//                for (COSName name : resources.getXObjectNames()) {
//                    if (resources.isImageXObject(name)) {
//                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
//
//                        BufferedImage rawImage = image.getImage();
//                        if (rawImage == null) continue;
//
//                        // Only downsample if the image is reasonably large (e.g., > 500px)
//                        if (rawImage.getWidth() > 500 || rawImage.getHeight() > 500) {
//                            double scale = 0.5;
//                            int newWidth = (int) (rawImage.getWidth() * scale);
//                            int newHeight = (int) (rawImage.getHeight() * scale);
//
//                            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
//                            Graphics2D g = resizedImage.createGraphics();
//
//                            // Quality settings for the resize
//                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//                            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
//
//                            g.drawImage(rawImage, 0, 0, newWidth, newHeight, null);
//                            g.dispose();
//
//                            // 0.6f quality offers a great balance between size and legibility
//                            PDImageXObject compressedXObject = JPEGFactory.createFromImage(document, resizedImage, 0.6f);
//                            resources.put(name, compressedXObject);
//                        }
//                    }
//                }
//            }
//            // Overwrite the temp file with compressed version
//            document.save(file);
//        }
//    }

//    public String getServiceUrl() {
//        String url = WorkflowUtil.getHttpServletRequest().getContextPath()+ "/web/json/plugin/org.joget.apps.form.lib.FileUpload/service";
//        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
//
//        //create nonce
//        String paramName = FormUtil.getElementParameterName(this);
//        String fileType = getPropertyString("fileType");
//        String nonce = SecurityUtil.generateNonce(new String[]{"FileUpload", appDef.getAppId(), appDef.getVersion().toString(), paramName, fileType}, 1);
//
//        try {
//            url = url + "?_nonce="+URLEncoder.encode(nonce, "UTF-8")+"&_paramName="+URLEncoder.encode(paramName, "UTF-8")+"&_appId="+URLEncoder.encode(appDef.getAppId(), "UTF-8")+"&_appVersion="+URLEncoder.encode(appDef.getVersion().toString(), "UTF-8")+"&_ft="+URLEncoder.encode(fileType, "UTF-8");
//        } catch (Exception e) {}
//        return url;
//    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String nonce = request.getParameter("_nonce");
        String paramName = request.getParameter("_paramName");
        String appId = request.getParameter("_appId");
        String appVersion = request.getParameter("_appVersion");
        String filePath = request.getParameter("_path");
        String fileType = request.getParameter("_ft");

        if (SecurityUtil.verifyNonce(nonce, new String[]{"FileUpload", appId, appVersion, paramName, fileType})) {
            if ("POST".equalsIgnoreCase(request.getMethod())) {

                try {
                    JSONObject obj = new JSONObject();
                    try {
                        // handle multipart files
                        String validatedParamName = SecurityUtil.validateStringInput(paramName);
                        MultipartFile file = FileStore.getFile(validatedParamName);
                        if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isEmpty()) {
                            String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")).toLowerCase();
                            if (fileType != null && (fileType.isEmpty() || fileType.contains(ext+";") || fileType.endsWith(ext))) {
                                String path = FileManager.storeFile(file);
                                obj.put("path", path);
                                obj.put("filename", file.getOriginalFilename());
                                obj.put("newFilename", path.substring(path.lastIndexOf(File.separator) + 1));
                            } else {
                                obj.put("error", ResourceBundleUtil.getMessage("form.fileupload.fileType.msg.invalidFileType"));
                            }
                        }

                        Collection<String> errorList = FileStore.getFileErrorList();
                        if (errorList != null && !errorList.isEmpty() && errorList.contains(paramName)) {
                            obj.put("error", ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                        }
                    } catch (Exception e) {
                        obj.put("error", e.getLocalizedMessage());
                    } finally {
                        FileStore.clear();
                    }
                    obj.write(response.getWriter());
                } catch (Exception ex) {}
            } else if (filePath != null && !filePath.isEmpty()) {
                String normalizedFilePath = SecurityUtil.normalizedFileName(filePath);

                File file = FileManager.getFileByPath(normalizedFilePath);
                if (file != null) {
                    ServletOutputStream stream = response.getOutputStream();
                    DataInputStream in = new DataInputStream(new FileInputStream(file));
                    byte[] bbuf = new byte[65536];

                    try {
                        String contentType = request.getSession().getServletContext().getMimeType(file.getName());
                        if (contentType != null) {
                            response.setContentType(contentType);
                        }

                        // send output
                        int length = 0;
                        while ((in != null) && ((length = in.read(bbuf)) != -1)) {
                            stream.write(bbuf, 0, length);
                        }
                    } catch (Exception e) {

                    } finally {
                        in.close();
                        stream.flush();
                        stream.close();
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ResourceBundleUtil.getMessage("general.error.error403"));
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

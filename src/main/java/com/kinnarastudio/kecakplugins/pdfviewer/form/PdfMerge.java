package com.kinnarastudio.kecakplugins.pdfviewer.form;

import com.kinnarastudio.kecakplugins.pdfviewer.util.PdfUtils;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.Organization;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * Upload and sign file
 */
public class PdfMerge extends FileUpload {

    public final static String PATH_FORMUPLOADS = "wflow/app_formuploads/";

    @Override
    public FormRowSet formatData(FormData formData) {
        // 1. Let Joget process the files natively first
        FormRowSet rowSet = super.formatData(formData);
        if (rowSet == null || rowSet.isEmpty()) {
            return rowSet;
        }

        String paramName = FormUtil.getElementParameterName(this);
        String fieldId = this.getPropertyString(FormUtil.PROPERTY_ID);
        if (fieldId == null || fieldId.trim().isEmpty()) {
            fieldId = paramName;
        }

        FormRow row = rowSet.get(0);
        String recordId = row.getId();
        if (recordId == null || recordId.trim().isEmpty()) {
            recordId = formData.getPrimaryKeyValue();
        }

        Form form = FormUtil.findRootForm(this);
        String formId = (form != null) ? form.getPropertyString(FormUtil.PROPERTY_ID) : "pdf_merge_test";
        if (formId == null || formId.trim().isEmpty()) {
            formId = "pdf_merge_test";
        }

        // Capture the database table name mapped to the form layout structure
        String tableName = (form != null) ? form.getPropertyString("tableName") : "";
        if (tableName == null || tableName.trim().isEmpty()) {
            tableName = "app_fd_" + formId; // Native framework fallback naming convention
        }

        // 2. Extract and parse the file list references efficiently
        String[] fileNames = FormUtil.getElementPropertyValues(this, formData);
        if (fileNames == null || fileNames.length == 0) {
            String filesPathValue = row.getProperty(fieldId);
            if (filesPathValue == null || filesPathValue.trim().isEmpty()) {
                filesPathValue = row.getProperty(paramName);
            }
            if (filesPathValue != null && !filesPathValue.trim().isEmpty()) {
                fileNames = filesPathValue.split(";");
            }
        } else if (fileNames.length == 1 && fileNames[0].contains(";")) {
            fileNames = fileNames[0].split(";");
        }

        if (fileNames != null && fileNames.length > 1) {
            List<File> physicalFiles = new ArrayList<File>(fileNames.length);

            // Build a single O(1) lookup map for active HTTP multipart streams
            Map<String, MultipartFile> mpFileMap = new HashMap<String, MultipartFile>();
            javax.servlet.http.HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request instanceof MultipartHttpServletRequest) {
                MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
                for (MultipartFile mpFile : multipartRequest.getFileMap().values()) {
                    String origName = mpFile.getOriginalFilename();
                    if (origName != null) {
                        mpFileMap.put(origName.trim().toLowerCase(), mpFile);
                    }
                }
            }

            // Anchor the relative PATH_FORMUPLOADS variable to the absolute environment home directory
            File tempDirBase = new File(FileManager.getBaseDirectory());
            File wflowRoot = tempDirBase.getParentFile();
            File jwHome = wflowRoot.getParentFile();
            File permDirBase = new File(jwHome, PATH_FORMUPLOADS);

            // Fallback target reference folder used for reading inputs
            File recordFolder = new File(wflowRoot, "app_formuploads" + File.separator + tableName + File.separator + recordId);
            File recordFolderFallback = new File(wflowRoot, "app_formuploads" + File.separator + formId + File.separator + recordId);

            for (String fileName : fileNames) {
                if (fileName == null) continue;
                fileName = fileName.trim();
                if (fileName.isEmpty()) continue;

                File resolvedFile = null;
                String lowerFileName = fileName.toLowerCase();

                // Step A: Check against pre-mapped HTTP upload stream
                if (mpFileMap.containsKey(lowerFileName)) {
                    try {
                        File tFile = File.createTempFile("joget_merge_", "_" + fileName);
                        mpFileMap.get(lowerFileName).transferTo(tFile);
                        resolvedFile = tFile;
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, "Error processing stream for: " + fileName);
                    }
                }

                // Step B: Multi-tier file system lookup
                if (resolvedFile == null) {
                    resolvedFile = findPhysicalFileOptimized(fileName, recordFolder, recordFolderFallback, tempDirBase, fieldId);
                }

                if (resolvedFile != null && resolvedFile.exists()) {
                    physicalFiles.add(resolvedFile);
                } else {
                    LogUtil.info(getClassName(), "Could not resolve physical file path for: " + fileName);
                }
            }

            // 3. Optimized Merge Execution
            if (physicalFiles.size() > 1) {
//                processMergePdf(physicalFiles, permDirBase, tableName, fieldId, recordId, formId, paramName, formData, row);
                String mergedFileName = "merged_" + System.currentTimeMillis() + ".pdf";
                try {
                    PDFMergerUtility pdfMerger = new PDFMergerUtility();
                    for (File f : physicalFiles) {
                        pdfMerger.addSource(f);
                    }

//                    String mergedFileName = "merged_" + System.currentTimeMillis() + ".pdf";

                    // Construct the targeted folder layouts based on absolute resolution paths
                    File destTableRoot = new File(permDirBase, tableName + File.separator + recordId);
                    File destFormRoot = new File(permDirBase, formId + File.separator + recordId);
                    File destTableUiSub = new File(destTableRoot, fieldId);
                    File destFormUiSub = new File(destFormRoot, fieldId);

                    // Ensure all path branches are initialized on the drive
                    if (!destTableRoot.exists()) destTableRoot.mkdirs();
                    if (!destFormRoot.exists()) destFormRoot.mkdirs();
                    if (!destTableUiSub.exists()) destTableUiSub.mkdirs();
                    if (!destFormUiSub.exists()) destFormUiSub.mkdirs();

                    File fileTableRoot = new File(destTableRoot, mergedFileName);
                    File fileFormRoot = new File(destFormRoot, mergedFileName);
                    File fileTableUiSub = new File(destTableUiSub, mergedFileName);
                    File fileFormUiSub = new File(destFormUiSub, mergedFileName);

                    // Execute the PDF merge directly to the structural Table Record Root destination
                    pdfMerger.setDestinationFileName(fileTableRoot.getAbsolutePath());
                    pdfMerger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());

                    // Broadcast zero-copy duplications to all alternative path configurations
                    copyFileOptimized(fileTableRoot, fileFormRoot);
                    copyFileOptimized(fileTableRoot, fileTableUiSub);
                    copyFileOptimized(fileTableRoot, fileFormUiSub);

                    // Assign update variables inside row entries natively
                    row.setProperty(fieldId, mergedFileName);
                    row.setProperty(paramName, mergedFileName);
                    String[] parameterPayload = new String[]{mergedFileName};
                    formData.addRequestParameterValues(paramName, parameterPayload);
                    formData.addRequestParameterValues(fieldId, parameterPayload);

                    LogUtil.info(getClassName(), "PDF Merge successfully broadcasted to all targets: " + mergedFileName);
                } catch (Exception e) {
                    LogUtil.error(getClassName(), e, "Error during PDF merging operations: " + e.getMessage());
                    formData.addFileError(paramName, "PDF Merge failed: " + e.getMessage());
                    return null;
                }
            }
        }

        return rowSet;
    }

    private File findPhysicalFileOptimized(String fileRef, File recordFolder, File recordFolderFallback, File tempDirBase, String fieldId) {
        String cleanFileName = fileRef;
        if (cleanFileName.contains("/") || cleanFileName.contains("\\")) {
            cleanFileName = new File(cleanFileName).getName();
        }

        try {
            File f = FileManager.getFileByPath(fileRef);
            if (f != null && f.exists() && f.isFile()) return f;
        } catch (Exception e) {}

        try {
            File f = FileManager.getFileByPath(cleanFileName);
            if (f != null && f.exists() && f.isFile()) return f;
        } catch (Exception e) {}

        // Check primary Table Folder layout routes
        if (recordFolder != null) {
            File pathA = new File(recordFolder, cleanFileName);
            if (pathA.exists() && pathA.isFile()) return pathA;

            if (fieldId != null && !fieldId.isEmpty()) {
                File pathB = new File(recordFolder, fieldId + File.separator + cleanFileName);
                if (pathB.exists() && pathB.isFile()) return pathB;
            }
        }

        // Check alternative Form Folder layout routes
        if (recordFolderFallback != null) {
            File pathA = new File(recordFolderFallback, cleanFileName);
            if (pathA.exists() && pathA.isFile()) return pathA;

            if (fieldId != null && !fieldId.isEmpty()) {
                File pathB = new File(recordFolderFallback, fieldId + File.separator + cleanFileName);
                if (pathB.exists() && pathB.isFile()) return pathB;
            }
        }

        // Run fuzzy matching across directory contents if exact matches aren't found
        String targetNorm = cleanFileName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (recordFolder != null && recordFolder.exists() && recordFolder.isDirectory()) {
            File found = scanFolderFuzzyOptimized(recordFolder, targetNorm);
            if (found != null) return found;
        }
        if (recordFolderFallback != null && recordFolderFallback.exists() && recordFolderFallback.isDirectory()) {
            File found = scanFolderFuzzyOptimized(recordFolderFallback, targetNorm);
            if (found != null) return found;
        }

        try {
            File tempDir = new File(tempDirBase, "temp");
            File scanTarget = tempDir.exists() && tempDir.isDirectory() ? tempDir : tempDirBase;

            List<File> matches = new ArrayList<File>();
            scanForFileMatchesOptimized(scanTarget, targetNorm, matches);

            if (!matches.isEmpty()) {
                if (matches.size() > 1) {
                    Collections.sort(matches, new Comparator<File>() {
                        @Override
                        public int compare(File f1, File f2) {
                            return Long.compare(f2.lastModified(), f1.lastModified());
                        }
                    });
                }
                return matches.get(0);
            }
        } catch (Exception e) {}

        return null;
    }

    private File scanFolderFuzzyOptimized(File dir, String targetNorm) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isFile()) {
                String currentNorm = f.getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (currentNorm.equals(targetNorm)) return f;
            } else if (f.isDirectory()) {
                File subMatch = scanFolderFuzzyOptimized(f, targetNorm);
                if (subMatch != null) return subMatch;
            }
        }
        return null;
    }

    private void scanForFileMatchesOptimized(File dir, String targetNorm, List<File> matches) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanForFileMatchesOptimized(f, targetNorm, matches);
            } else if (f.isFile()) {
                String currentNorm = f.getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (currentNorm.equals(targetNorm)) {
                    matches.add(f);
                }
            }
        }
    }

    private void copyFileOptimized(File source, File dest) throws IOException {
        java.io.FileInputStream fis = null;
        java.io.FileOutputStream fos = null;
        java.nio.channels.FileChannel sourceChannel = null;
        java.nio.channels.FileChannel destChannel = null;
        try {
            fis = new java.io.FileInputStream(source);
            fos = new java.io.FileOutputStream(dest);
            sourceChannel = fis.getChannel();
            destChannel = fos.getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } finally {
            if (sourceChannel != null) try { sourceChannel.close(); } catch(Exception e){}
            if (destChannel != null) try { destChannel.close(); } catch(Exception e){}
            if (fis != null) try { fis.close(); } catch(Exception e){}
            if (fos != null) try { fos.close(); } catch(Exception e){}
        }
    }

    protected String getOrganization() {
        final String propValue = getPropertyString("organization");
        if (!propValue.isEmpty()) {
            return propValue;
        }

        final ExtDirectoryManager directoryManager = (ExtDirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");

        final String orgId = WorkflowUtil.getCurrentUserOrgId();
        return Optional.of(orgId)
                .map(directoryManager::getOrganization)
                .map(Organization::getName)
                .orElse("");
    }

    @Override
    public String getName() {
        return "PDF Merge";
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        return resourceBundle.getString("buildNumber");
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }


    @Override
    public String getPropertyOptions() {
        try {
            JSONArray currentPluginProperties = new JSONArray(AppUtil.readPluginResource(getClassName(), "/properties/PdfMerge.json", null, true, "/messages/DigitalCertificate"));
            JSONArray parentPluginProperties = new JSONArray(super.getPropertyOptions());

            // merge with parent's plugin properties
            JSONStream.of(currentPluginProperties, Try.onBiFunction(JSONArray::getJSONObject))
                    .forEach(parentPluginProperties::put);

            return parentPluginProperties.toString().replace("\"", "'");
        } catch (JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return super.getPropertyOptions();
        }
    }
}

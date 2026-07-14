package com.kinnarastudio.kecakplugins.pdfviewer.tool;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.service.AppService;
import org.joget.commons.util.FileManager;
import java.util.ResourceBundle;

public class PdfMergeTool extends DefaultApplicationPlugin {

    final static String PATH_FORMUPLOADS = "wflow/app_formuploads/";

    @Override
    public String getName() {
        return "PDF Merge Tool";
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

//    @Override
//    public Object execute(Map map) {
//        executeAuditTrail("execute", props);
//
//        final PluginManager pluginManager = (PluginManager) props.get("pluginManager");
//        final WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
//        final WorkflowAssignment workflowAssignment = (WorkflowAssignment) props.get("workflowAssignment");
//        final FormDataDao formDataDao = (FormDataDao) pluginManager.getBean("formDataDao");
//        final AppDefinition appDefinition = (AppDefinition) props.get("appDef");
//        final WorkflowAssignment wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
//        final WorkflowProcess process = workflowManager.getProcess(wfAssignment.getProcessDefId());
//        final String token = generateRandomToken(DIGITS);
//
//        final Collection<String> usernames = WorkflowUtil.getAssignmentUsers(process.getPackageId(), wfAssignment.getProcessDefId(), wfAssignment.getProcessId(), wfAssignment.getProcessVersion(), wfAssignment.getActivityId(), "", PARTICIPANT_OTP);
//        Optional.ofNullable(generateForm(appDefinition, FORM_OTP))
//                .ifPresent(form -> usernames.forEach(username -> {
//                    final FormRow row = new FormRow();
//                    row.put(FIELD_TOKEN, token);
//                    row.put(FIELD_USERNAME, username);
//
//                    final FormRowSet rowSet = new FormRowSet();
//                    rowSet.add(row);
//
//                    formDataDao.saveOrUpdate(form, rowSet);
//                }));
//
//        workflowManager.processVariable(workflowAssignment.getProcessId(), VARIABLE_TOKEN, token);

//        return null;
//    }



    public Object execute(Map properties) {
        // 1. Resolve framework environment parameters from the execution map
        AppDefinition appDef = (AppDefinition) properties.get("appDef");
        WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");

        if (appDef == null || assignment == null) {
            LogUtil.error(getClass().getName(), new NullPointerException(), "Critical workflow execution context parameters missing.");
            return null;
        }

        // 2. Fetch plugin configuration attributes mapped in the Joget Tool UI
        String formId = (String) properties.get("sourceFormId");      // The Form ID where data resides
        String urlFieldId = (String) properties.get("urlFieldId");    // Form column containing the URLs
        String targetFieldId = (String) properties.get("targetFieldId");// Column to store the merged filename

        if (isEmpty(formId) || isEmpty(urlFieldId) || isEmpty(targetFieldId)) {
            LogUtil.warn(getClass().getName(), "Plugin configuration elements are incomplete. Skipping execution.");
            return null;
        }

        // Joget maps the active Process ID directly to the primary record key context
        String recordId = assignment.getProcessId();

        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

        try {
            // 3. Retrieve the target database form row data
            FormRowSet rowSet = appService.loadFormData(appDef.getId(), appDef.getVersion().toString(), formId, recordId);
            if (rowSet == null || rowSet.isEmpty()) {
                LogUtil.warn(getClass().getName(), "No data record found for Form ID: " + formId + " with Record ID: " + recordId);
                return null;
            }

            FormRow row = rowSet.get(0);
            String urlsPayload = row.getProperty(urlFieldId);
            if (isEmpty(urlsPayload)) {
                LogUtil.info(getClass().getName(), "URL input column field is empty. Nothing to process.");
                return null;
            }

            // Clean and parse semicolon-separated URLs submitted from the record
            String[] targetUrls = urlsPayload.split(";");
            List<File> downloadedFiles = new ArrayList<File>();

            // 4. Download Loop Phase
            for (String targetUrl : targetUrls) {
                if (targetUrl == null || targetUrl.trim().isEmpty()) continue;
                targetUrl = targetUrl.trim();

                try {
                    LogUtil.info(getClass().getName(), "Attempting connection download to remote endpoint URL: " + targetUrl);
                    URL urlConnection = new URL(targetUrl);

                    // Stream download payload directly into an isolated local temp file cache
                    File tempDownloadedFile = File.createTempFile("downloaded_tool_", ".pdf");
                    try (InputStream in = urlConnection.openStream()) {
                        Files.copy(in, tempDownloadedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    if (tempDownloadedFile.exists() && tempDownloadedFile.length() > 0) {
                        downloadedFiles.add(tempDownloadedFile);
                        LogUtil.info(getClass().getName(), "Successfully cached remote file file down to: " + tempDownloadedFile.getAbsolutePath());
                    }
                } catch (Exception downloadEx) {
                    // Safeguard: Log target failures separately to allow surviving valid streams to proceed
                    LogUtil.error(getClass().getName(), downloadEx, "Failed to capture file payload from remote URL endpoint: " + targetUrl);
                }
            }

            LogUtil.info(getClass().getName(), "Total valid files downloaded and verified: " + downloadedFiles.size());

            // 5. Merge Strategy Execution Phase
            if (downloadedFiles.size() > 1) {
                org.apache.pdfbox.multipdf.PDFMergerUtility pdfMerger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
                for (File f : downloadedFiles) {
                    pdfMerger.addSource(f);
                }

                String mergedFileName = "merged_" + System.currentTimeMillis() + ".pdf";

                // Resolve base physical environment scopes without doubling bugs
                File tempDirBase = new File(FileManager.getBaseDirectory());
                File wflowRoot = tempDirBase.getParentFile();
                File jwHome = wflowRoot.getParentFile();
                File permDirBase = new File(jwHome, PATH_FORMUPLOADS);

                String tableName = "app_fd_" + formId; // Dynamic default table layout name convention

                // Build structural application synchronization paths matrix
                List<File> targetDestinations = new ArrayList<File>();
                targetDestinations.add(new File(permDirBase, appDef.getId() + File.separator + formId + File.separator + recordId));
                targetDestinations.add(new File(permDirBase, appDef.getId() + File.separator + tableName + File.separator + recordId));
                targetDestinations.add(new File(permDirBase, formId + File.separator + recordId));
                targetDestinations.add(new File(permDirBase, tableName + File.separator + recordId));
                targetDestinations.add(new File(targetDestinations.get(0), targetFieldId)); // Include specific UI element subfolders

                // Make sure target directory tracks are completely initialized on disk
                for (File dir : targetDestinations) {
                    if (!dir.exists()) dir.mkdirs();
                }

                // Execute compilation payload straight to the primary system target destination
                File primaryOutputFile = new File(targetDestinations.get(0), mergedFileName);
                pdfMerger.setDestinationFileName(primaryOutputFile.getAbsolutePath());
                pdfMerger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
                LogUtil.info(getClass().getName(), "PDF Box execution completed cleanly. Main file output target: " + primaryOutputFile.getAbsolutePath());

                // Synchronize zero-copy duplications out across all endpoint locations to protect download URLs
                for (int i = 1; i < targetDestinations.size(); i++) {
                    File syncOutputFile = new File(targetDestinations.get(i), mergedFileName);
                    copyFileChannelZeroCopy(primaryOutputFile, syncOutputFile);
                }

                // 6. Record Updates Phase
                row.setProperty(targetFieldId, mergedFileName);
                appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formId, rowSet, recordId);
                LogUtil.info(getClass().getName(), "Database table records successfully synchronized with filename parameter: " + mergedFileName);
            } else {
                LogUtil.warn(getClass().getName(), "Process require at least 2 valid downloaded PDF payloads. Core execution bypassed.");
            }

            // 7. Cleanup local transient file footprints from disk memory
            for (File tempFile : downloadedFiles) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error encountered during tool execution processing: " + e.getMessage());
        }

        return null;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private void copyFileChannelZeroCopy(File source, File dest) throws java.io.IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
             java.nio.channels.FileChannel sourceChannel = fis.getChannel();
             java.nio.channels.FileChannel destChannel = fos.getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
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
        return null;
    }
}

package com.kinnara.kecakplugins.pdfviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;

import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;

public class PdfViewerWebService extends DefaultApplicationPlugin implements PluginWebSupport{
    final static String LABEL = "PDF Web Service";

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
        return LABEL;
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public void webService(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException {
        String urlString = servletRequest.getParameter("url");

        URL url = new URL(urlString);

        if (urlString.endsWith(".docx")) {
            try (InputStream inputStream = url.openStream()) {
                // Memuat dokumen DOCX
                WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);
                
                LogUtil.info(getClassName(), "Word Package: " + wordMLPackage.getContentType());
                // Output stream untuk menyimpan hasil PDF
                MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
                
                servletResponse.setContentType("application/pdf");
                // Melakukan konversi DOCX ke PDF
                Docx4J.toPDF(wordMLPackage, servletResponse.getOutputStream());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Error processing DOCX file: " + e.getMessage());
                servletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing DOCX file");
            }
        } else if (urlString.endsWith("xlsx")) {
            
        } else {
            try (InputStream inputStream = url.openStream()) {

                servletResponse.setContentType("application/pdf");
    
                OutputStream outputStream = servletResponse.getOutputStream();
    
                byte[] buffer = new byte[4096];
                int bytesRead;
    
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    @Override
    public Object execute(Map map) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'execute'");
    }
}

package com.kinnarastudio.kecakplugins.pdfviewer.util;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
//import org.apache.commons.io.IOUtils;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.web.client.RestClientException;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

public interface PdfUtils {
    boolean getHtmlEmbed(WorkflowAssignment assignment);

    String getPdfUrl(WorkflowAssignment assignment);

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @return
     * @throws RestClientException
     */
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url) {
        final HttpRequestBase request = new HttpGet(url);
        return request;
    }

    /**
     * Get HTTP Client
     *
     * @return
     * @throws RestClientException
     */
    default HttpClient getHttpClient() {
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

    /**
     * Get content type from response
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default String getResponseContentType(@Nonnull HttpResponse response) {
        return Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(HttpEntity::getContentType)
                .map(Header::getValue)
                .orElse("");
    }

    /**
     * Reads a specified InputStream, returning its contents in a byte array
     * @param in
     * @return
     * @throws IOException
     */
    default byte[] readInputStream(InputStream in) throws IOException {
        byte[] fileContent;
        try(ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedInputStream bin = new BufferedInputStream(in)) {
            int len;
            byte[] buffer = new byte[4096];
            while ((len = bin.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            fileContent = out.toByteArray();
            return fileContent;
        }
    }

    default String getSrc(WorkflowAssignment workflowAssignment) {
        if(getHtmlEmbed(workflowAssignment)) {
            try {
                return AppUtil.processHashVariable(getEncodedSrc(workflowAssignment), workflowAssignment, null, null);
            } catch (IOException e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
                return "";
            }
        } else {
            return getPdfUrl(workflowAssignment);
        }
    }

    default String getEncodedSrc(WorkflowAssignment workflowAssignment) throws IOException {
        HttpClient client = getHttpClient();
        HttpUriRequest request = getHttpRequest(workflowAssignment, getPdfUrl(workflowAssignment));
        HttpResponse response = client.execute(request);
        String contentType = getResponseContentType(response);
        if(!contentType.contains("application/pdf")) {
            LogUtil.warn(getClass().getName(), "Content ["+contentType+"] is not PDF");
            return "/plugin/" + getClass().getName() +"/images/pdf-logo.png";
        }

        try(InputStream inputStream = response.getEntity().getContent()) {
            byte[] bytes = readInputStream(inputStream);
            String base64Encoded = Base64.getEncoder().encodeToString(bytes);
            return "data:application/pdf;base64, " + base64Encoded;
        }
    }

    // Import check: Make sure you have java.awt.image.BufferedImage and java.awt.Graphics2D
    default void compressPdf(File file, String level, boolean watermark, String text) throws IOException {
        float scale = 0.6f;
        float quality = 0.6f;

        if ("low".equals(level)) { scale = 0.8f; quality = 0.8f; }
        else if ("high".equals(level)) { scale = 0.4f; quality = 0.4f; }

        // We use a temporary buffer to avoid overwriting the file while it's open
        byte[] pdfBytes;

        try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();

                // 1. Image Compression
                if (!"none".equals(level) && resources != null) {
                    for (COSName name : resources.getXObjectNames()) {
                        if (resources.isImageXObject(name)) {
                            PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                            BufferedImage rawImage = image.getImage();

                            if (rawImage != null && (rawImage.getWidth() > 500)) {
                                int nW = Math.round(rawImage.getWidth() * scale);
                                int nH = Math.round(rawImage.getHeight() * scale);

                                // FIX 1: Use TYPE_INT_RGB. JPEGs do not support ARGB (Transparency).
                                // Using ARGB here often results in inverted colors or black boxes in previews.
                                BufferedImage resized = new BufferedImage(nW, nH, BufferedImage.TYPE_INT_RGB);
                                Graphics2D g = resized.createGraphics();
                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                g.drawImage(rawImage, 0, 0, nW, nH, Color.WHITE, null); // Add white background for transparency
                                g.dispose();

                                resources.put(name, JPEGFactory.createFromImage(document, resized, quality));
                            }
                        }
                    }
                }

                // 2. Watermarking
                if (watermark && text != null && !text.isEmpty()) {
                    // APPEND mode ensures we don't overwrite existing page content
                    try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                        gs.setNonStrokingAlphaConstant(0.3f);
                        cs.setGraphicsStateParameters(gs);
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 50);
                        cs.setNonStrokingColor(Color.GRAY);

                        float w = page.getMediaBox().getWidth();
                        float h = page.getMediaBox().getHeight();
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), w/5, h/5));
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }

            // FIX 2: Save to a ByteArray first to release the file handle
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            pdfBytes = baos.toByteArray();
        } // Document is CLOSED here

        // FIX 3: Now that the document is closed, safely overwrite the file with the compressed bytes
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(pdfBytes);
        }
    }

    /**
     * Compresses PDF and returns bytes. No file is created/saved on the server.
     */
    default byte[] compressPdfToBytes(InputStream inputStream, String level, boolean watermark, String text) throws IOException {

        // Ganti IOUtils.toByteArray() dengan pure Java
        byte[] inputBytes;
        try (InputStream is = inputStream; ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            //ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(chunk)) != -1) {
                bos.write(chunk, 0, bytesRead);
            }
            inputBytes = bos.toByteArray();
        }

        LogUtil.info(getClass().getName(), "PdfUtil: Input received. Size: " + inputBytes.length + " bytes");

        if (inputBytes.length == 0) {
            throw new IOException("Input stream was empty before PDF processing.");
        }

        float scale = 0.6f;
        float quality = 0.6f;

        if ("low".equals(level)) { scale = 0.8f; quality = 0.8f; }
        else if ("high".equals(level)) { scale = 0.4f; quality = 0.4f; }

        try (PDDocument document = PDDocument.load(inputBytes, String.valueOf(MemoryUsageSetting.setupTempFileOnly()));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            LogUtil.info(getClass().getName(), "PdfUtil: Document loaded. Pages: " + document.getNumberOfPages());

            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();

                if (!"none".equals(level) && resources != null) {
                    for (COSName name : resources.getXObjectNames()) {
                        if (resources.isImageXObject(name)) {
                            PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                            BufferedImage rawImage = image.getImage();

                            if (rawImage != null && rawImage.getWidth() > 500) {
                                int nW = Math.round(rawImage.getWidth() * scale);
                                int nH = Math.round(rawImage.getHeight() * scale);

                                BufferedImage resized = new BufferedImage(nW, nH, BufferedImage.TYPE_INT_RGB);
                                Graphics2D g = resized.createGraphics();
                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                g.drawImage(rawImage, 0, 0, nW, nH, Color.WHITE, null);
                                g.dispose();

                                resources.put(name, JPEGFactory.createFromImage(document, resized, quality));
                            }
                        }
                    }
                }

                if (watermark && text != null && !text.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                        gs.setNonStrokingAlphaConstant(0.3f);
                        cs.setGraphicsStateParameters(gs);
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 50);
                        cs.setNonStrokingColor(Color.GRAY);

                        float w = page.getMediaBox().getWidth();
                        float h = page.getMediaBox().getHeight();
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), w / 5, h / 5));
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }

            document.save(baos);
            byte[] result = baos.toByteArray();
            LogUtil.info(getClass().getName(), "PdfUtil: Done. Output size: " + result.length + " bytes");
            return result;
        }
    }

    /**
     * Separate function to save bytes to a physical file if needed.
     */
    default void saveBytesToFile(byte[] data, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
        }
    }

//    default void compressPdf(File file, String level, boolean watermark, String text) throws IOException {
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
}

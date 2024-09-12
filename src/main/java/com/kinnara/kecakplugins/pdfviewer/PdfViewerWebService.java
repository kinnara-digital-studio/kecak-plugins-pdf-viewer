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

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

// import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
// import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

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
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
                
                servletResponse.setContentType("application/pdf");
                // Melakukan konversi DOCX ke PDF
                Docx4J.toPDF(wordMLPackage, servletResponse.getOutputStream());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Error processing DOCX file: " + e.getMessage());
                servletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing DOCX file");
            }
        } else if (urlString.endsWith("xlsx")) {
            try (InputStream inputStream = url.openStream()) {
                Workbook workbook = new XSSFWorkbook(inputStream);
        
                LogUtil.info(getClassName(), "Workbook Title: " + workbook.getSheetName(0));

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                Document pdfDocument = new Document();
                PdfWriter.getInstance(pdfDocument, byteArrayOutputStream);
        
                // Buka dokumen PDF
                pdfDocument.open();
        
                // Iterasi melalui setiap sheet dalam workbook
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    pdfDocument.add(new Paragraph("Sheet: " + sheet.getSheetName()));
        
                    // Hitung jumlah kolom dari baris pertama untuk membuat tabel PDF
                    Row firstRow = sheet.getRow(0);
                    int numColumns = firstRow.getPhysicalNumberOfCells();
                    PdfPTable table = new PdfPTable(numColumns); // Buat tabel PDF dengan kolom sebanyak jumlah sel
        
                    // Iterasi melalui setiap baris dalam sheet
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            // Buat cell untuk PDF
                            PdfPCell pdfCell = new PdfPCell();
                            pdfCell.setPadding(5); // Menambahkan padding untuk tampilan yang lebih baik
                            pdfCell.setBorderWidth(1); // Set border width untuk garis tabel
        
                            // Isi cell berdasarkan tipe datanya
                            switch (cell.getCellType()) {
                                case STRING:
                                    pdfCell.setPhrase(new Phrase(cell.getStringCellValue()));
                                    break;
                                case NUMERIC:
                                    pdfCell.setPhrase(new Phrase(String.valueOf(cell.getNumericCellValue())));
                                    break;
                                case BOOLEAN:
                                    pdfCell.setPhrase(new Phrase(String.valueOf(cell.getBooleanCellValue())));
                                    break;
                                case FORMULA:
                                    pdfCell.setPhrase(new Phrase(cell.getCellFormula()));
                                    break;
                                default:
                                    pdfCell.setPhrase(new Phrase(""));
                            }
                            // Menambahkan cell ke tabel PDF
                            table.addCell(pdfCell);
                        }
                    }
        
                    // Menambahkan tabel ke dokumen PDF
                    pdfDocument.add(table);
                }
        
                // Tutup dokumen PDF
                pdfDocument.close();
                workbook.close();
        
                // Kirim PDF ke output stream servlet response
                servletResponse.setContentType("application/pdf");
                servletResponse.setContentLength(byteArrayOutputStream.size());
                byteArrayOutputStream.writeTo(servletResponse.getOutputStream());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Error processing XLSX file");
                servletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing XLSX file");
            }
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

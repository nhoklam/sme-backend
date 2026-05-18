package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import sme.backend.dto.response.ProductResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    public byte[] exportProductsToExcel(List<ProductResponse> products) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sách sản phẩm");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            // Create Header
            Row headerRow = sheet.createRow(0);
            String[] columns = {"STT", "Mã SKU", "Barcode/ISBN", "Tên sản phẩm", "Danh mục", "Đơn vị", "Giá bán lẻ", "Giá vốn", "Tồn kho", "Trạng thái"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill Data
            int rowIdx = 1;
            for (ProductResponse p : products) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rowIdx - 1);
                row.createCell(1).setCellValue(p.getSku());
                row.createCell(2).setCellValue(p.getIsbnBarcode());
                row.createCell(3).setCellValue(p.getName());
                row.createCell(4).setCellValue(p.getCategoryName());
                row.createCell(5).setCellValue(p.getUnit());
                row.createCell(6).setCellValue(p.getRetailPrice().doubleValue());
                row.createCell(7).setCellValue(p.getMacPrice() != null ? p.getMacPrice().doubleValue() : 0);
                row.createCell(8).setCellValue(p.getAvailableQuantity());
                row.createCell(9).setCellValue(p.getIsActive() ? "Đang bán" : "Ngừng bán");
            }

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}

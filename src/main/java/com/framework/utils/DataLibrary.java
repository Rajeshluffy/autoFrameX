package com.framework.utils;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads test data from Excel files under the {@code ./data/} folder.
 *
 * <p>Parsed workbooks are cached in a {@link ConcurrentHashMap} so that
 * multiple test classes sharing the same Excel file (e.g. "Login.xlsx" used
 * by both TC001 and TC002) only pay the I/O cost once per JVM run.
 *
 * <p>Cache eviction is not needed for test runs — the JVM exits after the
 * suite finishes, clearing all state automatically.
 */
public class DataLibrary {

    private static final Logger logger = Logger.getLogger(DataLibrary.class.getName());

    /** Cache: file base-name → parsed data grid. */
    private static final ConcurrentMap<String, Object[][]> cache = new ConcurrentHashMap<>();

    private DataLibrary() { /* utility class — no instances */ }

    /**
     * Returns test data from {@code ./data/<excelFileName>.xlsx}.
     * The first row (headers) is skipped; every subsequent row becomes one
     * {@code Object[]} entry in the returned array.
     *
     * <p>Results are cached after the first read, so repeated calls with the
     * same file name are free.
     *
     * @param excelFileName base name of the file, without the {@code .xlsx} extension
     * @return two-dimensional array suitable for a TestNG {@code @DataProvider},
     *         or an empty array if the file cannot be read
     */
    public static Object[][] readExcelData(String excelFileName) {
        return cache.computeIfAbsent(excelFileName, DataLibrary::loadFromDisk);
    }

    /** Clears the in-memory cache. Useful between suite runs in long-lived JVMs. */
    public static void clearCache() {
        cache.clear();
        logger.fine("DataLibrary cache cleared");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Object[][] loadFromDisk(String excelFileName) {
        String path = "./data/" + excelFileName + ".xlsx";
        logger.info("Loading Excel data from: " + path);

        try (XSSFWorkbook wbook = new XSSFWorkbook(path)) {
            XSSFSheet sheet = wbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();       // excludes header row
            int colCount = sheet.getRow(0).getLastCellNum();

            if (rowCount < 1) {
                logger.warning("Excel file has no data rows: " + path);
                return new Object[0][0];
            }

            Object[][] data = new Object[rowCount][colCount];
            for (int i = 1; i <= rowCount; i++) {
                for (int j = 0; j < colCount; j++) {
                    data[i - 1][j] = sheet.getRow(i).getCell(j).getStringCellValue();
                }
            }

            logger.info("Loaded " + rowCount + " data row(s) from: " + path);
            return data;

        } catch (IOException e) {
            logger.severe("Failed to read Excel file '" + path + "': " + e.getMessage());
            return new Object[0][0];
        }
    }
}

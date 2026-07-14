package com.framework.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.framework.testng.api.base.AccountData;

/**
 * Reads test data from Excel (.xlsx) and delimited text (CSV/TSV) files
 * under the {@code ./data/} folder.
 *
 * <p>Parsed data is cached in a {@link ConcurrentHashMap} so multiple test
 * classes sharing the same file only pay the I/O cost once per JVM run.
 *
 * <p>Cache eviction is not needed for test runs — the JVM exits after the
 * suite finishes, clearing all state automatically.
 */
public class DataLibrary {

    private static final Logger logger = LoggerFactory.getLogger(DataLibrary.class);

    /** Cache: cache-key → parsed data grid. */
    private static final ConcurrentMap<String, Object[][]> cache = new ConcurrentHashMap<>();

    public DataLibrary() { /* utility class — no instances */ }

    // =========================================================================
    // EXCEL
    // =========================================================================

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
        return cache.computeIfAbsent("xlsx:" + excelFileName, k -> loadFromDisk(excelFileName));
    }

    // =========================================================================
    // CSV / TSV
    // =========================================================================

    /**
     * Returns test data from {@code ./data/<fileName>.csv} using comma as the
     * delimiter. The first row (headers) is skipped.
     *
     * @param fileName base name without the {@code .csv} extension
     * @return two-dimensional array, or an empty array if the file cannot be read
     */
    public static Object[][] readCsvData(String fileName) {
        return cache.computeIfAbsent("csv:" + fileName, k -> loadDelimited(fileName + ".csv", ","));
    }

    /**
     * Returns test data from {@code ./data/<fileName>.tsv} using tab as the
     * delimiter. The first row (headers) is skipped.
     *
     * @param fileName base name without the {@code .tsv} extension
     * @return two-dimensional array, or an empty array if the file cannot be read
     */
    public static Object[][] readTsvData(String fileName) {
        return cache.computeIfAbsent("tsv:" + fileName, k -> loadDelimited(fileName + ".tsv", "\t"));
    }

    /**
     * Returns test data from a delimited file with a custom separator.
     * The first row (headers) is skipped.
     *
     * @param fileName  file name relative to {@code ./data/} (including extension)
     * @param delimiter column separator (e.g. {@code ","}, {@code ";"}, {@code "\t"})
     * @return two-dimensional array, or an empty array if the file cannot be read
     */
    public static Object[][] readDelimitedData(String fileName, String delimiter) {
        return cache.computeIfAbsent("delimited:" + delimiter + ":" + fileName,
                k -> loadDelimited(fileName, delimiter));
    }

    // =========================================================================
    // ACCOUNT DATA (typed Excel rows)
    // =========================================================================

    /**
     * Returns all account rows from {@code ./data/<excelFileName>.xlsx} as typed
     * {@link AccountData} objects, suitable for injection via a TestNG DataProvider.
     *
     * <p>Column convention: col 0 = accountId, col 1 = userName, col 2 = password,
     * col 3 = url (optional). Results are cached after the first read.
     *
     * @param excelFileName base name without the {@code .xlsx} extension
     * @return two-dimensional array where each inner array contains one {@link AccountData}
     */
    public static AccountData[][] readExcelAsAccounts(String excelFileName) {
        Object[][] raw = readExcelData(excelFileName);
        if (raw.length == 0) {
            logger.warn("No account rows found in: " + excelFileName + ".xlsx");
            return new AccountData[0][0];
        }
        AccountData[][] accounts = new AccountData[raw.length][1];
        for (int i = 0; i < raw.length; i++) {
            accounts[i][0] = AccountData.fromRow(raw[i]);
        }
        logger.info("Resolved " + accounts.length + " AccountData row(s) from: " + excelFileName);
        return accounts;
    }
    
    
    /**
     * Loads all valid account rows from the Excel file.
     * Row 0 (header) is skipped. Rows where {@code companyUser} or
     * {@code userUser} is blank are skipped.
     *
     * @return ordered list of {@code [companyUser, companyPass, userUser, userPass]};
     *         empty list if the file is missing or unreadable.
     */
    
    public static List<Object[]> readExcelAsAccount(String excelPath) {
        String path = System.getProperty("user.dir") + File.separator + excelPath;
        File file = new File(path);
        
        if (!file.exists()) {
            logger.warn("Excel file not found: " + file.getAbsolutePath());
            return Collections.emptyList();
        }

        logger.info("Loading Excel account data from: " + path);
        List<Object[]> accounts = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = wb.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();
            for (int i = 1; i <= rowCount; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) continue;

                String companyUser = fmt.formatCellValue(row.getCell(0)).trim();
                String companyPass = fmt.formatCellValue(row.getCell(1)).trim();
                String userUser    = fmt.formatCellValue(row.getCell(2)).trim();
                String userPass    = fmt.formatCellValue(row.getCell(3)).trim();

                if (companyUser.isEmpty() || userUser.isEmpty()) continue;

                accounts.add(new Object[]{ companyUser, companyPass, userUser, userPass });
            }

            logger.info("Loaded " + accounts.size() + " account row(s) from: " + path);

        } catch (Exception e) {
            logger.error("Failed to read Excel file '" + path + "': " + e.getMessage());
        }

        return accounts;
    }
    
    
    
    

    /** Clears the in-memory cache. Useful between suite runs in long-lived JVMs. */
    public static void clearCache() {
        cache.clear();
        logger.debug("DataLibrary cache cleared");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Object[][] loadDelimited(String fileName, String delimiter) {
        String path = "./data/" + fileName;
        logger.info("Loading delimited data from: " + path + " (delimiter: [" + delimiter + "])");

        List<Object[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                if (line.trim().isEmpty()) continue;
                rows.add(line.split(delimiter, -1));
            }
        } catch (IOException e) {
            logger.error("Failed to read delimited file '" + path + "': " + e.getMessage());
            return new Object[0][0];
        }

        if (rows.isEmpty()) {
            logger.warn("Delimited file has no data rows: " + path);
            return new Object[0][0];
        }

        logger.info("Loaded " + rows.size() + " data row(s) from: " + path);
        return rows.toArray(new Object[0][]);
    }

    private static Object[][] loadFromDisk(String excelFileName) {
        String path = "./data/" + excelFileName + ".xlsx";
        logger.info("Loading Excel data from: " + path);

        try (XSSFWorkbook wbook = new XSSFWorkbook(path)) {
            XSSFSheet sheet = wbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();       // excludes header row
            int colCount = sheet.getRow(0).getLastCellNum();

            if (rowCount < 1) {
                logger.warn("Excel file has no data rows: " + path);
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
            logger.error("Failed to read Excel file '" + path + "': " + e.getMessage());
            return new Object[0][0];
        }
    }
}

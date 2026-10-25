package com.framework.utils;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import java.util.regex.Pattern;
import com.framework.exception.FrameworkException;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.framework.config.data.ConfigResolver;

/**
 * Reads test data from Excel (.xlsx) and delimited text (CSV/TSV) files under a
 * configurable base directory (default {@code ./data}, see {@link #baseDataDir()}).
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

    /**
     * Resolves the base data directory: {@code -DdataDir=...} system property,
     * then {@code DATA_DIR} environment variable, then {@code ./data} default.
     * No TestNG-parameter tier — this is a static utility invoked from
     * {@code @DataProvider} methods with no {@code ITestContext} in scope, unlike
     * {@code ConfigManager}'s own resolution chain.
     */
    private static String baseDataDir() {
        return ConfigResolver.resolveString("dataDir", "DATA_DIR", null, "./data");
    }

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
     *         or throws {@link FrameworkException} if the file is missing, unreadable or has no data rows
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
     * @return two-dimensional array, or throws {@link FrameworkException} if the file is missing, unreadable or has no data rows
     */
    public static Object[][] readCsvData(String fileName) {
        return cache.computeIfAbsent("csv:" + fileName, k -> loadDelimited(fileName + ".csv", ","));
    }

    /**
     * Returns test data from {@code ./data/<fileName>.tsv} using tab as the
     * delimiter. The first row (headers) is skipped.
     *
     * @param fileName base name without the {@code .tsv} extension
     * @return two-dimensional array, or throws {@link FrameworkException} if the file is missing, unreadable or has no data rows
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
     * @return two-dimensional array, or throws {@link FrameworkException} if the file is missing, unreadable or has no data rows
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
            throw new FrameworkException("No account rows found in: " + excelFileName + ".xlsx");
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
            // An empty list makes a data-driven test run nothing, and the build stays green.
            throw new FrameworkException("Excel account file not found: " + file.getAbsolutePath());
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
            // Returning whatever was read before the failure would run a partial data set as if it were whole.
            throw new FrameworkException("Failed to read Excel file '" + path + "': " + e.getMessage(), e);
        }

        if (accounts.isEmpty()) {
            throw new FrameworkException("No usable account rows (company user and user name are required) in: " + path);
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
        String path = baseDataDir() + "/" + fileName;
        logger.info("Loading delimited data from: " + path + " (delimiter: [" + delimiter + "])");

        // The delimiter is a literal separator ("|" and "." must not be read as regex), and a failure
        // throws: returning an empty array let a typo in the file name run zero tests and stay green.
        Pattern separator = Pattern.compile(Pattern.quote(delimiter));
        List<Object[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                if (line.trim().isEmpty()) continue;
                rows.add(separator.split(line, -1));
            }
        } catch (IOException e) {
            throw new FrameworkException("Cannot read data file '" + new File(path).getAbsolutePath() + "': "
                    + e.getMessage() + " (set -DdataDir to change the data folder)", e);
        }

        if (rows.isEmpty()) {
            throw new FrameworkException("Data file has no data rows below the header: "
                    + new File(path).getAbsolutePath());
        }

        logger.info("Loaded " + rows.size() + " data row(s) from: " + path);
        return rows.toArray(new Object[0][]);
    }

    private static Object[][] loadFromDisk(String excelFileName) {
        String path = baseDataDir() + "/" + excelFileName + ".xlsx";
        logger.info("Loading Excel data from: " + path);

        File file = new File(path);
        if (!file.isFile()) {
            throw new FrameworkException("Excel data file not found: " + file.getAbsolutePath()
                    + " (set -DdataDir to change the data folder)");
        }

        // Opened read-only on purpose: POI's path constructor opens the package read-write and
        // saves it back to the same file on close, which rewrote every data file that was only
        // being read (it showed up as a modified file in git after each run).
        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ);
             XSSFWorkbook wbook = new XSSFWorkbook(pkg)) {
            XSSFSheet sheet = wbook.getSheetAt(0);
            XSSFRow header = sheet.getRow(0);
            if (header == null) {
                throw new FrameworkException("Excel data file has no header row: " + file.getAbsolutePath());
            }

            // DataFormatter reads any cell type as text (a numeric password, a date, a formula result);
            // getStringCellValue() threw on those, and on blank cells and blank rows (NPE).
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wbook.getCreationHelper().createFormulaEvaluator();

            int colCount = Math.max(header.getLastCellNum(), 0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                XSSFRow row = sheet.getRow(i);
                if (row != null) {
                    colCount = Math.max(colCount, row.getLastCellNum());
                }
            }

            List<Object[]> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) continue;                      // blank row between data rows
                Object[] values = new Object[colCount];
                boolean anyValue = false;
                for (int j = 0; j < colCount; j++) {
                    String text = formatter.formatCellValue(row.getCell(j), evaluator);   // "" for a blank cell
                    values[j] = text;
                    anyValue |= !text.trim().isEmpty();
                }
                if (anyValue) {
                    rows.add(values);
                }
            }

            if (rows.isEmpty()) {
                throw new FrameworkException("Excel data file has no data rows below the header: "
                        + file.getAbsolutePath());
            }

            logger.info("Loaded " + rows.size() + " data row(s) from: " + path);
            return rows.toArray(new Object[0][]);

        } catch (IOException | InvalidFormatException | InvalidOperationException e) {
            throw new FrameworkException("Cannot read Excel data file '" + file.getAbsolutePath() + "': "
                    + e.getMessage(), e);
        }
    }
}

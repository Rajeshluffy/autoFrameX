package com.framework.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.framework.exception.FrameworkException;

/**
 * A missing, unreadable or empty data file used to log an error and hand back an empty array: TestNG then ran
 * zero invocations and the build stayed green. These pin that it fails loudly instead, that real-world
 * spreadsheets (blank cells and rows, numeric cells) load, and that a failure is not cached.
 */
public class DataLibraryFailureTest {

	private Path dir;
	private String savedDataDir;

	@BeforeMethod
	public void useATemporaryDataFolder() throws IOException {
		dir = Files.createTempDirectory("datalibrary-failure");
		savedDataDir = System.getProperty("dataDir");
		System.setProperty("dataDir", dir.toString());
	}

	@AfterMethod(alwaysRun = true)
	public void restore() throws IOException {
		if (savedDataDir == null) {
			System.clearProperty("dataDir");
		} else {
			System.setProperty("dataDir", savedDataDir);
		}
		try (var files = Files.list(dir)) {
			files.forEach(f -> f.toFile().delete());
		}
		Files.deleteIfExists(dir);
	}

	private String uniqueName(String prefix) {
		return prefix + "_" + System.nanoTime();
	}

	private interface SheetFiller {
		void fill(XSSFSheet sheet);
	}

	private void writeWorkbook(String name, SheetFiller filler) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(dir.resolve(name + ".xlsx"))) {
			filler.fill(wb.createSheet("data"));
			wb.write(out);
		}
	}

	// ----------------------------------------------------------------- failures

	@Test
	public void aMissingExcelFileThrowsWithThePath() {
		String name = uniqueName("missing");
		FrameworkException e = Assert.expectThrows(FrameworkException.class, () -> DataLibrary.readExcelData(name));
		Assert.assertTrue(e.getMessage().contains(name + ".xlsx"), e.getMessage());
	}

	@Test
	public void anExcelFileWithOnlyAHeaderThrows() throws IOException {
		String name = uniqueName("header_only");
		writeWorkbook(name, sheet -> sheet.createRow(0).createCell(0).setCellValue("accountId"));
		Assert.assertThrows(FrameworkException.class, () -> DataLibrary.readExcelData(name));
	}

	@Test
	public void aMissingCsvFileThrows() {
		Assert.assertThrows(FrameworkException.class, () -> DataLibrary.readCsvData(uniqueName("missing_csv")));
	}

	@Test
	public void aCsvFileWithOnlyAHeaderThrows() throws IOException {
		String name = uniqueName("header_only_csv");
		Files.writeString(dir.resolve(name + ".csv"), "a,b\n", StandardCharsets.UTF_8);
		Assert.assertThrows(FrameworkException.class, () -> DataLibrary.readCsvData(name));
	}

	@Test
	public void aFailedLoadIsNotCachedSoFixingTheFileWorks() throws IOException {
		String name = uniqueName("fixed_later");
		Assert.assertThrows(FrameworkException.class, () -> DataLibrary.readExcelData(name));

		writeWorkbook(name, sheet -> {
			sheet.createRow(0).createCell(0).setCellValue("id");
			sheet.createRow(1).createCell(0).setCellValue("A1");
		});
		Assert.assertEquals(DataLibrary.readExcelData(name)[0][0], "A1");
	}

	// ----------------------------------------------------------- real spreadsheets

	@Test
	public void blankCellsBlankRowsAndNumericCellsLoad() throws IOException {
		String name = uniqueName("messy");
		writeWorkbook(name, sheet -> {
			XSSFRow header = sheet.createRow(0);
			header.createCell(0).setCellValue("accountId");
			header.createCell(1).setCellValue("userName");
			header.createCell(2).setCellValue("password");
			header.createCell(3).setCellValue("url");

			XSSFRow numeric = sheet.createRow(1);               // password stored as a number, url column left blank
			numeric.createCell(0).setCellValue("ACC001");
			numeric.createCell(1).setCellValue("demo");
			numeric.createCell(2).setCellValue(123456);

			// row 2 is a gap: no row object at all

			XSSFRow last = sheet.createRow(3);
			last.createCell(0).setCellValue("ACC002");
			last.createCell(1).setCellValue("other");
			last.createCell(2).setCellValue("secret");
			last.createCell(3).setCellValue("https://app.example");
		});

		Object[][] data = DataLibrary.readExcelData(name);

		Assert.assertEquals(data.length, 2, "the gap row is skipped, both data rows are kept");
		Assert.assertEquals(data[0][2], "123456", "a numeric cell is read as its text, not an exception");
		Assert.assertEquals(data[0][3], "", "a blank cell is an empty string");
		Assert.assertEquals(data[1][3], "https://app.example");
	}

	@Test
	public void aRowThatIsEntirelyBlankIsNotADataRow() throws IOException {
		String name = uniqueName("blank_row");
		writeWorkbook(name, sheet -> {
			sheet.createRow(0).createCell(0).setCellValue("id");
			sheet.createRow(1).createCell(0).setCellValue("A1");
			sheet.createRow(2).createCell(0).setCellValue("   ");
		});
		Assert.assertEquals(DataLibrary.readExcelData(name).length, 1);
	}

	@Test
	public void theDelimiterIsLiteralNotARegex() throws IOException {
		String name = uniqueName("pipes") + ".txt";
		Files.writeString(dir.resolve(name), "a|b|c\n1|2|3\n", StandardCharsets.UTF_8);

		Object[][] data = DataLibrary.readDelimitedData(name, "|");

		Assert.assertEquals(data.length, 1);
		Assert.assertEquals(data[0].length, 3, "'|' as a regex splits between every character");
		Assert.assertEquals(data[0][1], "2");
	}
}

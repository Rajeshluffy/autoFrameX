package com.framework.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.framework.exception.FrameworkException;

/**
 * {@code DataLibrary.readExcelAsAccount} (company and user credentials, four columns) logged and returned an empty
 * list for a missing file, and returned whatever it had read before a failure: a data-driven test then ran
 * nothing, or ran part of the data as if it were all of it, and the build stayed green. The sibling readers were
 * already fixed to fail loudly; this one now does too.
 */
public class DataLibraryAccountFileTest {

	private static final String RELATIVE_DIR = "target" + java.io.File.separator + "account-file-test";
	private Path dir;

	@BeforeMethod
	public void createFolder() throws IOException {
		dir = Path.of(System.getProperty("user.dir"), RELATIVE_DIR);
		Files.createDirectories(dir);
	}

	@AfterMethod(alwaysRun = true)
	public void cleanFolder() throws IOException {
		try (var files = Files.list(dir)) {
			files.forEach(f -> f.toFile().delete());
		}
		Files.deleteIfExists(dir);
	}

	private String relative(String name) {
		return RELATIVE_DIR + java.io.File.separator + name;
	}

	private void write(String name, String[][] rows) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(dir.resolve(name))) {
			XSSFSheet sheet = wb.createSheet("accounts");
			for (int r = 0; r < rows.length; r++) {
				XSSFRow row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					row.createCell(c).setCellValue(rows[r][c]);
				}
			}
			wb.write(out);
		}
	}

	@Test
	public void aWellFormedFileLoadsItsRowsAndSkipsIncompleteOnes() throws IOException {
		write("ok.xlsx", new String[][] {
				{ "companyUser", "companyPass", "user", "userPass" },
				{ "acme", "cp1", "alice", "up1" },
				{ "", "cp2", "bob", "up2" },            // no company user: skipped
				{ "globex", "cp3", "carol", "up3" } });
		List<Object[]> accounts = DataLibrary.readExcelAsAccount(relative("ok.xlsx"));
		Assert.assertEquals(accounts.size(), 2);
		Assert.assertEquals(accounts.get(0)[0], "acme");
		Assert.assertEquals(accounts.get(1)[2], "carol");
	}

	@Test
	public void aMissingFileFailsInsteadOfReturningNothing() {
		FrameworkException e = Assert.expectThrows(FrameworkException.class,
				() -> DataLibrary.readExcelAsAccount(relative("nope.xlsx")));
		Assert.assertTrue(e.getMessage().contains("nope.xlsx"), e.getMessage());
	}

	@Test
	public void aFileThatIsNotAWorkbookFails() throws IOException {
		Files.writeString(dir.resolve("broken.xlsx"), "this is not a spreadsheet");
		FrameworkException e = Assert.expectThrows(FrameworkException.class,
				() -> DataLibrary.readExcelAsAccount(relative("broken.xlsx")));
		Assert.assertTrue(e.getMessage().contains("broken.xlsx"), e.getMessage());
	}

	@Test
	public void aFileWithNoUsableRowsFails() throws IOException {
		write("empty.xlsx", new String[][] { { "companyUser", "companyPass", "user", "userPass" }, { "", "", "", "" } });
		Assert.assertThrows(FrameworkException.class, () -> DataLibrary.readExcelAsAccount(relative("empty.xlsx")));
	}
}

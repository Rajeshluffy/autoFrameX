package com.framework.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Reading a data file must not touch it. POI's {@code new XSSFWorkbook(path)} opens the
 * package read-write and saves it back on close, so every data-provider run used to
 * rewrite {@code accounts.xlsx} and leave it showing as modified in git.
 */
public class DataLibraryReadOnlyTest {

	@Test
	public void readingAnExcelDataFileLeavesTheFileUntouched() throws IOException {
		Path dir = Files.createTempDirectory("datalibrary-readonly");
		String name = "accounts_" + System.nanoTime();
		File file = dir.resolve(name + ".xlsx").toFile();
		String previousDataDir = System.getProperty("dataDir");
		try {
			try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
				XSSFSheet sheet = wb.createSheet("accounts");
				XSSFRow header = sheet.createRow(0);
				header.createCell(0).setCellValue("accountId");
				header.createCell(1).setCellValue("userName");
				XSSFRow row = sheet.createRow(1);
				row.createCell(0).setCellValue("ACC001");
				row.createCell(1).setCellValue("demo");
				wb.write(out);
			}
			// An old timestamp makes any rewrite visible, however fast the test runs.
			long oldTime = 1_577_836_800_000L; // 2020-01-01
			Assert.assertTrue(file.setLastModified(oldTime));
			byte[] before = Files.readAllBytes(file.toPath());
			long modifiedBefore = file.lastModified();

			System.setProperty("dataDir", dir.toString());
			Object[][] data = DataLibrary.readExcelData(name);

			Assert.assertEquals(data.length, 1, "one data row below the header");
			Assert.assertEquals(data[0][0], "ACC001");
			Assert.assertEquals(data[0][1], "demo");
			Assert.assertEquals(file.lastModified(), modifiedBefore, "the file must not be saved back after a read");
			Assert.assertEquals(Files.readAllBytes(file.toPath()), before, "the file's bytes must be unchanged");
		} finally {
			if (previousDataDir == null) {
				System.clearProperty("dataDir");
			} else {
				System.setProperty("dataDir", previousDataDir);
			}
			Files.deleteIfExists(file.toPath());
			Files.deleteIfExists(dir);
		}
	}

	@Test
	public void aMissingFileStillGivesAnEmptyResultInsteadOfAnException() throws IOException {
		Path dir = Files.createTempDirectory("datalibrary-missing");
		String previousDataDir = System.getProperty("dataDir");
		try {
			System.setProperty("dataDir", dir.toString());
			Object[][] data = DataLibrary.readExcelData("does_not_exist_" + System.nanoTime());
			Assert.assertEquals(data.length, 0);
		} finally {
			if (previousDataDir == null) {
				System.clearProperty("dataDir");
			} else {
				System.setProperty("dataDir", previousDataDir);
			}
			Files.deleteIfExists(dir);
		}
	}
}

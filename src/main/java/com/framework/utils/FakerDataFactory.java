package com.framework.utils;

import com.github.javafaker.Faker;

public class FakerDataFactory {
	// ThreadLocal ensures each thread has its own independent Faker instance
	private static final ThreadLocal<Faker> fakerThreadLocal = ThreadLocal.withInitial(() -> new Faker());

	// --- Personal Info ---
	public static String getFirstName() {
		return fakerThreadLocal.get().name().firstName();
	}

	public static String getLastName() {
		return fakerThreadLocal.get().name().lastName();
	}

	public static String getFullName() {
		return fakerThreadLocal.get().name().fullName();
	}

	public static String getEmail() {
		return fakerThreadLocal.get().internet().emailAddress();
	}

	public static String getPhoneNumber() {
		return fakerThreadLocal.get().phoneNumber().phoneNumber();
	}

	// --- Identity & Credentials ---
	public static String getUsername() {
		return fakerThreadLocal.get().name().username();
	}

	public static String getPassword() {
		// Generates an 8-16 character password with mixed case and symbols
		return fakerThreadLocal.get().internet().password(8, 16, true, true);
	}

	// --- Location & Address ---
	public static String getFullAddress() {
		return fakerThreadLocal.get().address().fullAddress();
	}

	public static String getCity() {
		return fakerThreadLocal.get().address().city();
	}

	public static String getZipCode() {
		return fakerThreadLocal.get().address().zipCode();
	}

	// --- Financials ---
	public static String getCreditCardNumber() {
		return fakerThreadLocal.get().finance().creditCard();
	}

	public static String getIban() {
		return fakerThreadLocal.get().finance().iban();
	}

	// --- Business & Text ---
	public static String getCompanyName() {
		return fakerThreadLocal.get().company().name();
	}

	public static String getJobTitle() {
		return fakerThreadLocal.get().job().title();
	}

	public static String getParagraph() {
		return fakerThreadLocal.get().lorem().paragraph();
	}

	// --- Dates & Numbers ---
	public static java.util.Date getBirthday(int minAge, int maxAge) {
		return fakerThreadLocal.get().date().birthday(minAge, maxAge);
	}

	public static int getRandomNumber(int min, int max) {
		return fakerThreadLocal.get().number().numberBetween(min, max);
	}

	/**
	 * Removes the {@link Faker} instance bound to the current thread.
	 * Call this from {@code @AfterMethod} to prevent thread-local leaks when
	 * tests run in a managed thread pool (e.g. TestNG parallel execution).
	 */
	public static void cleanup() {
		fakerThreadLocal.remove();
	}
}

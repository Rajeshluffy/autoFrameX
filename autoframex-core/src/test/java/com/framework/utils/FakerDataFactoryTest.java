package com.framework.utils;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.annotations.Test;

/**
 * Faker reads its data tables with SnakeYAML. It used to be given snakeyaml:android:1.23 (a version-pin in the
 * root pom never reached it, being a different classifier); it now runs on the real SnakeYAML 2.x jar. This calls
 * every generator, so a library upgrade that breaks Faker's YAML loading fails here instead of in a test run.
 */
public class FakerDataFactoryTest {

    private static void assertText(String value, String what) {
        assertNotNull(value, what);
        assertFalse(value.isBlank(), what + " is blank");
    }

    @Test
    public void everyTextGeneratorProducesAValue() {
        assertText(FakerDataFactory.getFirstName(), "first name");
        assertText(FakerDataFactory.getLastName(), "last name");
        assertText(FakerDataFactory.getFullName(), "full name");
        assertText(FakerDataFactory.getUsername(), "username");
        assertText(FakerDataFactory.getFullAddress(), "address");
        assertText(FakerDataFactory.getCity(), "city");
        assertText(FakerDataFactory.getZipCode(), "zip code");
        assertText(FakerDataFactory.getCreditCardNumber(), "card number");
        assertText(FakerDataFactory.getIban(), "iban");
        assertText(FakerDataFactory.getCompanyName(), "company");
        assertText(FakerDataFactory.getJobTitle(), "job title");
        assertText(FakerDataFactory.getParagraph(), "paragraph");
        assertText(FakerDataFactory.getPhoneNumber(), "phone number");
    }

    @Test
    public void anEmailLooksLikeAnEmail() {
        assertTrue(FakerDataFactory.getEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"), FakerDataFactory.getEmail());
    }

    @Test
    public void aPasswordHonoursItsLengthAndMixesCharacterClasses() {
        for (int i = 0; i < 25; i++) {
            String password = FakerDataFactory.getPassword();
            assertTrue(password.length() >= 8 && password.length() <= 16, "length " + password.length());
        }
    }

    @Test
    public void aBirthdayFallsInsideTheRequestedAgeRange() {
        Date birthday = FakerDataFactory.getBirthday(20, 30);
        long years = (System.currentTimeMillis() - birthday.getTime()) / (365L * 24 * 3600 * 1000);
        assertTrue(years >= 19 && years <= 31, "age was about " + years);
    }

    @Test
    public void aRandomNumberStaysInsideItsBounds() {
        for (int i = 0; i < 200; i++) {
            int n = FakerDataFactory.getRandomNumber(5, 9);
            assertTrue(n >= 5 && n <= 9, "got " + n);
        }
        assertEquals(FakerDataFactory.getRandomNumber(7, 7), 7);
    }

    @Test
    public void everyThreadGetsItsOwnGenerator() throws Exception {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        Thread[] threads = new Thread[6];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 20; i++) {
                    seen.add(FakerDataFactory.getFullName());
                }
                FakerDataFactory.cleanup();
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertTrue(seen.size() > 20, "parallel generators produced only " + new HashSet<>(seen).size() + " distinct names");
    }
}

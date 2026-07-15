package design.patterns.factory.browser;

import java.lang.annotation.*;

/**
 * Annotation to specify browser configuration for test classes or methods.
 * 
 * <p>Usage:
 * <pre>
 * {@literal @}BrowserConfig("chrome")
 * public class MyTest {
 *     
 *     {@literal @}BrowserConfig(value = "chrome", url = "https://example.com")
 *     public void testMethod() {
 *         // Test code
 *     }
 * }
 * </pre>
 * 
 * @author Framework Team
 * @version 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface BrowserConfig {

	/**
	 * Browser type (chrome, firefox, etc.).
	 * 
	 * @return browser type
	 */
	BrowserType browser() default BrowserType.CHROME;
	;

	/**
	 * Optional URL to navigate to.
	 * 
	 * @return URL string or empty
	 */
	String url() default "";
	
	

	String value() default "";

}

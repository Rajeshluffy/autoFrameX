package com.framework.testng.api.base;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

import org.testng.Assert;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Test;
import org.testng.internal.ConstructorOrMethod;

/**
 * What {@link RetryEngine} does with a failing test: how many times it retries by default, when it decides a
 * failure is deterministic, and that two different failures are not mistaken for one (every step failure is
 * thrown from the same line in {@code Reporter.reportStep}, so comparing the line alone made them look equal).
 */
public class RetryEngineTest {

	// -------------------------------------------------------- methods under test

	@Test
	@SuppressWarnings("unused")
	public void plainTest() {
	}

	@Retry(limit = 3)
	@SuppressWarnings("unused")
	public void patientTest() {
	}

	// ------------------------------------------------------------------- stubs

	/** Always thrown from this one line, like the step failures that all come out of Reporter.reportStep. */
	private static RuntimeException failure(String message) {
		return new IllegalStateException(message);
	}

	@SuppressWarnings("unchecked")
	private static <T> T stub(Class<T> type, Function<String, Object> answers) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
			Object answer = answers.apply(method.getName());
			if (answer != null) {
				return answer;
			}
			Class<?> returns = method.getReturnType();
			if (returns == boolean.class) return false;
			if (returns == int.class) return 0;
			return null;
		});
	}

	private static ITestResult failed(String testMethod, Throwable thrown) throws NoSuchMethodException {
		Method method = RetryEngineTest.class.getDeclaredMethod(testMethod);
		ITestNGMethod testng = stub(ITestNGMethod.class, name -> {
			switch (name) {
			case "getQualifiedName": return "RetryEngineTest." + testMethod;
			case "getConstructorOrMethod": return new ConstructorOrMethod(method);
			case "getGroups": return new String[0];
			default: return null;
			}
		});
		int[] status = { ITestResult.FAILURE };
		return stub(ITestResult.class, name -> {
			switch (name) {
			case "isSuccess": return false;
			case "getThrowable": return thrown;
			case "getMethod": return testng;
			case "getParameters": return new Object[0];
			case "getStatus": return status[0];
			default: return null;
			}
		});
	}

	// ----------------------------------------------------------------- defaults

	@Test
	public void theDefaultIsOneRetryNotFour() throws Exception {
		RetryEngine engine = new RetryEngine();

		Assert.assertTrue(engine.retry(failed("plainTest", failure("first problem"))), "one retry is allowed");
		Assert.assertFalse(engine.retry(failed("plainTest", failure("second problem"))),
				"a different failure after the retry is still not retried a second time");
	}

	@Test
	public void aTestCanOptInToMoreRetriesWithTheAnnotation() throws Exception {
		RetryEngine engine = new RetryEngine();

		Assert.assertTrue(engine.retry(failed("patientTest", failure("problem one"))));
		Assert.assertTrue(engine.retry(failed("patientTest", failure("problem two"))));
		Assert.assertTrue(engine.retry(failed("patientTest", failure("problem three"))));
		Assert.assertFalse(engine.retry(failed("patientTest", failure("problem four"))), "limit = 3 is used up");
	}

	// ------------------------------------------------------- same failure twice

	@Test
	public void theSameFailureTwiceIsDeterministicSoRetryingStops() throws Exception {
		RetryEngine engine = new RetryEngine();

		Assert.assertTrue(engine.retry(failed("patientTest", failure("login button missing"))));
		Assert.assertFalse(engine.retry(failed("patientTest", failure("login button missing"))),
				"identical failure: another run will not change it, even with retries left");
	}

	@Test
	public void twoDifferentFailuresFromTheSameLineAreNotTreatedAsTheSame() throws Exception {
		RetryEngine engine = new RetryEngine();

		Assert.assertTrue(engine.retry(failed("patientTest", failure("login button missing"))));
		Assert.assertTrue(engine.retry(failed("patientTest", failure("search box missing"))),
				"a different message is a different failure, so it is retried");
	}

	// ----------------------------------------------------------------- signature

	@Test
	public void numbersInTheMessageDoNotMakeTheSameFailureLookDifferent() {
		RuntimeException a = failure("Element not clickable after 20 seconds, id 4711");
		RuntimeException b = failure("Element not clickable after 19 seconds, id 4802");

		Assert.assertEquals(RetryEngine.failureSignature(a, 12), RetryEngine.failureSignature(b, 12));
	}

	@Test
	public void theExceptionTypeAndTheLineAreAlsoPartOfTheSignature() {
		Assert.assertNotEquals(RetryEngine.failureSignature(new IllegalStateException("x"), 12),
				RetryEngine.failureSignature(new IllegalArgumentException("x"), 12));
		Assert.assertNotEquals(RetryEngine.failureSignature(new IllegalStateException("x"), 12),
				RetryEngine.failureSignature(new IllegalStateException("x"), 13));
	}

	@Test
	public void aFailureWithoutAMessageOrAThrowableStillHasASignature() {
		Assert.assertNotNull(RetryEngine.failureSignature(new IllegalStateException(), 5));
		Assert.assertNotNull(RetryEngine.failureSignature(null, 5));
	}
}

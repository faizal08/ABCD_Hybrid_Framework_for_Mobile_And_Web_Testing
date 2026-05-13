package com.eit.automation.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.HidesKeyboard; // Essential for the hideKeyboard action
import org.openqa.selenium.remote.DesiredCapabilities;
import java.net.URL;
import com.eit.automation.actions.MobileActions;
import java.util.ArrayList;
import java.util.Collections;

import com.eit.automation.actions.ClickActions;
import com.eit.automation.actions.FileActions;
import com.eit.automation.actions.InputActions;
import com.eit.automation.actions.ToastActions;
import com.eit.automation.actions.ScrollActions;
import com.eit.automation.actions.AutoItActions;
import com.eit.automation.actions.VerificationActions;
import com.eit.automation.actions.WaitActions;
import com.eit.automation.parser.TestStep;
import com.eit.automation.utils.ReportGenerator;
import com.eit.automation.utils.DatabaseUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TestExecutor {

	private WebDriver driver;
	private WebDriverWait wait;

	// --- NEW: Universal Session Management ---
	private Map<String, WebDriver> driverPool = new HashMap<>(); // Stores Admin, User, and Driver sessions
	private Map<String, WebDriverWait> waitPool = new HashMap<>(); // Stores corresponding wait objects
	private String currentSessionRole = "web"; // Tracks who is currently active (e.g., "user", "driver", "admin")

	// Action handlers
	private WaitActions waitActions;
	private ClickActions clickActions;
	private InputActions inputActions;
	private VerificationActions verificationActions;
	private FileActions fileActions;
	private ToastActions toastActions;
	private ScrollActions scrollActions;
	private AutoItActions autoItActions;

	private MobileActions mobileActions;
	private ActionRegistry actionRegistry;
	private PageObjectManager pageObjectManager;

	private ReportGenerator reportGenerator;

	private Properties config;

	private String excelName;

	private boolean isCleanupMode = false; // Flag for special cleanup UI
	// Logging configuration
	private boolean detailedLogging = true; // Enabled by default for better debugging
	private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	// Error tracking
	private int totalStepsExecuted = 0;
	private int passedSteps = 0;
	private int failedSteps = 0;

	public TestExecutor() {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                        INITIALIZING UNIVERSAL EXECUTOR                         ║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");

		// Start with a default Web session (Super Admin)
		initializeWebDriver("web");
	}

	public TestExecutor(ReportGenerator reportGenerator, Properties config) {
		this.reportGenerator = reportGenerator;
		this.config = config;

		// Initialize the name from config
		String fullPath = config.getProperty("excel.name");
		this.excelName = (fullPath != null) ? fullPath.split("\\.")[0] : "Unknown_Excel";

		// Call the setup logic
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                        INITIALIZING UNIVERSAL EXECUTOR                         ║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");

		// Setup initial default session
		initializeWebDriver("web");

		log("✓ Report generator configured");
		log("");
	}

	/**
	 * Preserves all your existing Chrome functionalities while adding them to the Pool
	 */
	public void initializeWebDriver(String role) {
		log("→ Setting up Chrome Browser for Role: " + role.toUpperCase());

		io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
		log("  • WebDriverManager: Synchronized ChromeDriver");

		ChromeOptions options = new ChromeOptions();

		// --- PRESERVED: Your exact original Preferences ---
		Map<String, Object> prefs = new HashMap<>();
		prefs.put("credentials_enable_service", false);
		prefs.put("profile.password_manager_enabled", false);
		prefs.put("autofill.profile_enabled", false);
		prefs.put("profile.password_manager_leak_detection", false);
		options.setExperimentalOption("prefs", prefs);

		// --- PRESERVED: Your exact original Arguments ---
		options.addArguments("--start-maximized");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-features=SafeBrowsingPasswordCheck");
		options.setExperimentalOption("detach", true);
		options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

		WebDriver newDriver = new ChromeDriver(options);
		WebDriverWait newWait = new WebDriverWait(newDriver, Duration.ofSeconds(30));

		// Add to Universal Pool
		driverPool.put(role, newDriver);
		waitPool.put(role, newWait);

		// Set as active context
		this.driver = newDriver;
		this.wait = newWait;
		this.currentSessionRole = role;

		refreshActionHandlers();
		log("✓ Web Session [" + role + "] is active and ready");
	}

	/**
	 * Refreshes handlers to point to the current active driver/wait objects
	 */
	private void refreshActionHandlers() {
		this.waitActions = new WaitActions(driver, wait);
		this.clickActions = new ClickActions(driver, wait, waitActions);
		this.inputActions = new InputActions(driver, wait, waitActions);
		this.verificationActions = new VerificationActions(driver, wait, waitActions);

		// Only initialize Web-specific actions if it's not a mobile driver
		if (!(driver instanceof AppiumDriver)) {
			this.fileActions = new FileActions(driver, wait, waitActions);
			this.toastActions = new ToastActions(driver, wait, waitActions);
			this.scrollActions = new ScrollActions(driver, wait, waitActions);
			this.autoItActions = new AutoItActions(driver, wait, waitActions);
		} else {
			// Initialize Mobile Specifics
			this.mobileActions = new MobileActions((AppiumDriver) driver, wait);
		}
	}

	/**
	 * Execute list of test steps - CONTINUES ON ERROR, DOESN'T CLOSE BROWSER
	 */
	public boolean run(String sheetName, List<TestStep> steps, String testCaseName) {
		long testStartTime = System.currentTimeMillis();

		totalStepsExecuted = 0;
		passedSteps = 0;
		failedSteps = 0;

		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║  TEST CASE: " + padRight(testCaseName, 66) + "║");
		log("║  Total Steps: " + padRight(String.valueOf(steps.size()), 63) + "║");
		log("║  Start Time: " + padRight(getCurrentTime(), 64) + "║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		log("");

		try {
			if (reportGenerator != null) {
				reportGenerator.startTestCase(testCaseName);
			}

			for (int i = 0; i < steps.size(); i++) {
				TestStep step = steps.get(i);
				int stepNumber = i + 1;
				String action = step.getAction().toLowerCase();

				// --- NEW: UNIVERSAL SESSION SWITCHING ---
				// If the action is 'switch_to', we handle it here before executeStep
				if (action.equals("switch_to") || action.equals("switchsession")) {
					logStepHeader(stepNumber, steps.size(), step);
					switchSession(step.getValue()); // Role name like 'user' or 'driver'
					passedSteps++;
					totalStepsExecuted++;
					continue;
				}

				// --- NEW: GUARD OVERLAY ---
				// Only update the browser overlay if we are NOT on a mobile device
				if (!(driver instanceof AppiumDriver)) {
					updateBrowserOverlay(sheetName, testCaseName, stepNumber, step);
				}

				logStepHeader(stepNumber, steps.size(), step);

				long stepStartTime = System.currentTimeMillis();

				try {
					executeStep(step);
					passedSteps++;

					long stepDuration = System.currentTimeMillis() - stepStartTime;
					logStepSuccess(stepNumber, stepDuration);

					if (reportGenerator != null) {
						reportGenerator.logStep(stepNumber, step, "PASSED", "", driver);
					}

				} catch (Exception e) {
					failedSteps++;
					long stepDuration = System.currentTimeMillis() - stepStartTime;
					logStepFailure(stepNumber, stepDuration, e);

					if (reportGenerator != null) {
						StringBuilder errorDetails = new StringBuilder();
						errorDetails.append("❌ MUST FIX: ").append(e.getMessage() != null ? e.getMessage() : "Unknown Error").append("\n");

						if (e.getCause() != null) {
							String causeMsg = e.getCause().getMessage();
							if (causeMsg != null) {
								int buildInfoIndex = causeMsg.indexOf("Build info:");
								if (buildInfoIndex > 0) {
									causeMsg = causeMsg.substring(0, buildInfoIndex).trim();
								}
								errorDetails.append("ℹ️ CAUSE: ").append(causeMsg).append("\n");
							}
						}
						errorDetails.append("⚠️ TYPE: ").append(e.getClass().getSimpleName());

						reportGenerator.logStep(stepNumber, step, "FAILED", errorDetails.toString(), driver);
					}

					log("❌ Aborting current test case due to failure...");
					log("");
					break;
				}

				totalStepsExecuted++;
			}

			if (reportGenerator != null) {
				reportGenerator.endTestCase(failedSteps == 0);
			}

			long testDuration = System.currentTimeMillis() - testStartTime;
			logTestSummary(testCaseName, testDuration);

			return failedSteps == 0;

		} catch (Exception e) {
			long testDuration = System.currentTimeMillis() - testStartTime;
			logCriticalFailure(testCaseName, testDuration, e);

			if (reportGenerator != null) {
				reportGenerator.endTestCase(false);
			}

			return false;
		}
	}
	/**
	 * Execute list of test steps WITHOUT test case name
	 */
	public boolean run(List<TestStep> steps) {
		return run("Default", steps, "Unnamed Test Case"); // Added "Default" as the first argument
	}

	/**
	 * Switches the active driver and wait objects to the requested role.
	 * Role can be 'web', 'user', or 'driver'.
	 */
	public void switchSession(String role) {
		String targetRole = role.toLowerCase();

		if (driverPool.containsKey(targetRole)) {
			log("🔄 SWITCHING CONTEXT: Moving focus to [" + targetRole.toUpperCase() + "]");

			// Update the active pointers
			this.driver = driverPool.get(targetRole);
			this.wait = waitPool.get(targetRole);
			this.currentSessionRole = targetRole;

			// Re-attach all action handlers to the new driver
			refreshActionHandlers();

			log("✅ Context switched successfully.");
		} else {
			log("❌ ERROR: Session '" + targetRole + "' has not been initialized!");
			throw new RuntimeException("Session Role '" + targetRole + "' not found in Driver Pool. " +
					"Ensure you initialized it using setupWebDriver() or setupMobileDriver().");
		}
	}

	/**
	 * Re-initializes all action classes with the current active driver.
	 * This ensures 'clickActions', 'inputActions', etc., are talking to the right device.
	 */
	/**
	 * Execute single test step - NEVER THROWS, JUST LOGS ERRORS
	 */
	private void executeStep(TestStep step) throws Exception {
		String action = step.getAction().toLowerCase();
		String value = step.getValue();
		String xpath = step.getXpath();
		String context = step.getContext();

		log("  ⚙ Action: " + action.toUpperCase());

		/*// HOTFIX: Override file upload path with user provided path
		if ((action.equals("uploadfile") || action.equals("robotupload")) && value != null) {
			log("  ⚠ HOTFIX: Overriding file path with 'C:\\Vehicle Image\\Auto.jpg'");
			value = "C:\\Vehicle Image\\Auto.jpg";
		}*/

		// 1. PAGEFACTORY LOOKUP (If XPath is empty, try to match value/context to a
		// Page Object field)
		if ((xpath == null || xpath.isEmpty()) && (value != null && !value.isEmpty())) {
			if (pageObjectManager != null) {

				WebElement element = pageObjectManager.findElementByName(value);
				if (element != null) {
					log("  → Found PageFactory Element: " + value);
				}
			}
		}

		// Auto-generate XPath if empty (Legacy fallback)
		if (xpath == null || xpath.isEmpty()) {
			if (value != null && !value.isEmpty()) {

				// --- ADDED THIS CHECK ---
				// Detect if the value is actually an XPath string instead of a label
				boolean isDirectXPath = value.startsWith("//") || value.startsWith("(");

				if (isDirectXPath) {
					xpath = value; // Use the value as the XPath directly
					log("  → Using direct XPath from Value column: " + xpath);
				}
				else if (!(driver instanceof io.appium.java_client.AppiumDriver) &&
						!action.startsWith("verifytoast") &&
						!action.equals("robotupload")) {

					// Only auto-generate complex Web XPaths if we are NOT on Mobile
					xpath = generateXPathFromValue(value, context);
					log("  → Auto-generated Web XPath: " + xpath);
				}
				else if (driver instanceof io.appium.java_client.AppiumDriver) {
					// For mobile, we often use the 'Value' directly as an Accessibility ID or ID
					// We will handle the specific finding logic inside MobileActions/ClickActions
					xpath = value;
					log("  → Using Mobile Locator: " + xpath);
				}
				else {
					log("  → Using auto-detection for toast/alert");
				}
			}
		}

		if ((xpath == null || xpath.isEmpty()) && (value == null || value.isEmpty())) {
			log("  ⚠ Both XPath and Value empty - skipping");
		}
		switch (action) {
			case "openurl":
			case "navigate":
				if (driver instanceof io.appium.java_client.AppiumDriver) {
					log("  → Mobile Context: App is already launched via Capabilities");
				} else {
					log("  → URL: " + value);
					driver.get(value);
					waitActions.waitForPageLoad();
					log("  ✓ Page loaded: " + driver.getCurrentUrl());
				}
				break;
			// ... (Removing migrated cases to clean up? Or keeping as fallback?
			// For safety, I'll keep the switch case logic for now, but the Registry check
			// above protects us.)
			case "scrolltobottom":
				log("  → Scrolling to bottom");
				scrollActions.scrollToBottom();
				log("  ✓ Scrolled to bottom");
				break;

			case "scrolltotop":
				log("  → Scrolling to top");
				scrollActions.scrollToTop();
				log("  ✓ Scrolled to top");
				break;

			case "scrolltoelement":
				log("  → XPath: " + xpath);
				WebElement elementToScroll = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
				scrollActions.scrollToElement(elementToScroll);
				log("  ✓ Scrolled to element");
				break;

			case "scrollby":
				log("  → Scroll Amount: " + value);
				String[] coords = value.split(",");
				if (coords.length >= 2) {
					int x = Integer.parseInt(coords[0].trim());
					int y = Integer.parseInt(coords[1].trim());
					scrollActions.scrollBy(x, y);
					log("  ✓ Scrolled by " + x + ", " + y);
				} else {
					log("  ⚠ Invalid scroll amount format (expected 'x,y'): " + value);
				}
				break;

			case "click":
				log("  → XPath/Locator: " + xpath);

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					// MOBILE LOGIC: Use the MobileActions tap method
					// This uses the parseLocator logic (ID or XPath) we added to MobileActions
					mobileActions.tap(xpath);
				} else {
					// WEB LOGIC: Preserve your original ClickActions logic
					// If we have a direct XPath, we tell the action handler NOT to use 'Value'
					if (xpath != null && !xpath.isEmpty()) {
						clickActions.clickElementWithRetry(xpath, null);
					} else {
						clickActions.clickElementWithRetry(xpath, value);
					}
				}

				log("  ✓ Clicked");
				break;
			case "select":
				log("  → XPath/Locator: " + xpath);
				log("  → Value to Select: " + value);

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					// MOBILE LOGIC
					// 1. First, tap the element to open the selection list/picker
					mobileActions.tap(xpath);
					log("  → Opened mobile picker, searching for: " + value);

					// 2. Locate the specific text option.
					// We use a dynamic XPath to find the text on the screen.
					String itemLocator = "//*[@text='" + value + "' or @content-desc='" + value + "']";
					WebElement item = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(itemLocator)));
					item.click();
				} else {
					// WEB LOGIC: Preserve your original ClickActions logic
					clickActions.selectElementWithRetry(xpath, value);
				}

				log("  ✓ Selected");
				break;

			case "type":
			case "enter":
				log("  → XPath/Locator: " + xpath);

				// --- PRESERVED: Security Masking for Logging ---
				if (xpath != null && (xpath.toLowerCase().contains("password") || xpath.toLowerCase().contains("pwd")
						|| xpath.toLowerCase().contains("pass"))) {
					log("  → Value: ********** (hidden)");
				} else {
					log("  → Value: "
							+ (value != null && value.length() > 60 ? value.substring(0, 60) + "..." : value));
				}

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					// MOBILE LOGIC
					// We find the element using the locator (ID or XPath) and enter text
					WebElement mobileElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
							(xpath.startsWith("//") || xpath.startsWith("(//")) ? By.xpath(xpath) : By.id(xpath)
					));
					mobileElement.sendKeys(value);

					// Essential for Mobile: Hide keyboard to keep the screen clear for the next step
					mobileActions.hideKeyboard();
				} else {
					// WEB LOGIC: Preserve your original InputActions logic
					inputActions.typeText(xpath, value);
				}

				log("  ✓ Text entered");
				break;

			case "clear":
				log("  → XPath: " + xpath);
				inputActions.clearField(xpath);
				log("  ✓ Field cleared");
				break;

			case "arrow_down":
				log("  → XPath: " + xpath);
				driver.findElement(By.xpath(xpath)).sendKeys(org.openqa.selenium.Keys.ARROW_DOWN);
				log("  ✓ Sent Key: ARROW_DOWN");
				break;

			case "arrow_up":
				log("  → XPath: " + xpath);
				driver.findElement(By.xpath(xpath)).sendKeys(org.openqa.selenium.Keys.ARROW_UP);
				log("  ✓ Sent Key: ARROW_UP");
				break;

			case "press_enter":
				log("  → XPath: " + xpath);
				driver.findElement(By.xpath(xpath)).sendKeys(org.openqa.selenium.Keys.ENTER);
				log("  ✓ Sent Key: ENTER");
				break;

			case "tab":
				log("  → Starting XPath: " + xpath);
				// 1. Find the starting point
				WebElement currentElement = driver.findElement(By.xpath(xpath));

				int repeat = 1;
				try {
					if (value != null && !value.isEmpty()) {
						repeat = Integer.parseInt(value.trim());
					}
				} catch (NumberFormatException e) {
					repeat = 1;
				}

				// 2. Loop through the tabs
				for (int i = 0; i < repeat; i++) {
					// Send TAB to whoever currently has the focus
					currentElement.sendKeys(org.openqa.selenium.Keys.TAB);

					// Switch the "anchor" to the new active element for the next loop
					currentElement = driver.switchTo().activeElement();

					// Small pause to let the grid UI catch up and scroll
					try { Thread.sleep(150); } catch (InterruptedException e) {}
				}
				log("  ✓ Finished " + repeat + " tabs. Final focus is now on the right.");
				break;

			case "uploadfile":
			case "selectfile":
			case "attachfile":
				log("  → File: " + value);
				log("  → XPath/Locator: " + xpath);

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					// MOBILE GUARD
					log("  ⚠ Mobile Upload: Ensure the app's permission dialogs are handled.");
					// On mobile, we usually just use a standard click/tap on the 'Upload' button
					// then manually automate the Gallery steps.
					mobileActions.tap(xpath);
				} else {
					// WEB LOGIC: Original FileActions
					fileActions.uploadFile(value, xpath);
				}
				log("  ✓ File upload action initiated");
				break;

			case "robotupload":
				if (driver instanceof io.appium.java_client.AppiumDriver) {
					log("  ❌ Critical: 'robotupload' is not supported on Mobile Devices.");
					throw new RuntimeException("RobotUpload is a Windows-only feature and cannot be used on Mobile.");
				}

				log("  → File: " + value);
				if (xpath != null && !xpath.isEmpty()) {
					log("  → Clicking upload button: " + xpath);
					driver.findElement(By.xpath(xpath)).click();
					waitActions.waitFor(1000);
				}
				fileActions.uploadFileWithRobot(value);
				log("  ✓ File uploaded via Robot");
				break;

			case "waitforupload":
				log("  → Waiting for upload completion");
				log("  → XPath/Locator: " + xpath);

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					// Mobile usually waits for a specific element like 'Upload Successful'
					waitActions.waitForElementVisible(xpath);
				} else {
					// WEB LOGIC: Original FileActions
					fileActions.waitForUploadComplete(xpath);
				}
				log("  ✓ Upload wait complete");
				break;


			case "element_present":
				log("  🔍 Checking if element is present: " + xpath);
				try {
					// Use a short explicit wait to see if it appears
					WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
					shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
					log("  ✅ Element found (as expected).");
				} catch (Exception e) {
					throw new RuntimeException("Validation Failed: Element was expected but NOT found: " + xpath);
				}
				break;

			case "element_absent":
				log("  🚫 Checking if element is absent: " + xpath);
				// 1. Temporarily disable implicit wait to check immediately
				driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
				try {
					List<WebElement> elements = driver.findElements(By.xpath(xpath));
					if (elements.isEmpty()) {
						log("  ✅ Element is absent (as expected).");
					} else {
						throw new RuntimeException("Validation Failed: Element was found but should be ABSENT: " + xpath);
					}
				} finally {
					// 2. ALWAYS restore the implicit wait
					driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
				}
				break;

			case "verifytoast":
			case "verifytoastmessage":
			case "verifysuccesstoast":
			case "verifyerrortoast":
				log("  → Expected: " + value);
				if (xpath != null && !xpath.isEmpty()) {
					log("  → XPath: " + xpath);
					toastActions.verifyToastMessage(value, xpath);
				} else {
					log("  → Auto-detecting toast");
					toastActions.verifyToastMessageByText(value);
				}
				log("  ✓ Toast verified");
				break;

			// case "verifysuccesstoast":
			// log(" → Expected: " + value);
			// toastActions.verifySuccessToast(value);
			// log(" ✓ Success toast verified");
			// break;

			// case "verifyerrortoast":
			// log(" → Expected: " + value);
			// toastActions.verifyErrorToast(value);
			// log(" ✓ Error toast verified");
			// break;

			case "verifyalert":
			case "verifyalertmessage":
				log("  → Expected: " + (value != null && !value.isEmpty() ? value : "(just verify presence)"));
				log("  → XPath: " + xpath);
				toastActions.verifyToastMessage(value, xpath);
				log("  ✓ Alert verified");
				break;

			case "waitfortoast":
				log("  → XPath: " + xpath);
				toastActions.waitForToastToAppearAndDisappear(xpath);
				log("  ✓ Toast lifecycle complete");
				break;

			case "verify":
			case "verifyvisible":
			case "verifydisplayed":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementVisible(xpath);
				log("  ✓ Element visible");
				break;

			case "verifytext":
				log("  → XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValueOrText(xpath, value);
				log("  ✓ Text verified");
				break;

			case "verifyvalue":
				log("  → XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValue(xpath, value);
				log("  ✓ Value verified");
				break;

			case "drawpolygon":
				log("  → Canvas XPath: " + xpath);
				log("  → Points: " + value);
				drawPolygon(xpath, value);
				log("  ✓ Polygon drawn");
				break;

			case "verifydate":
				log("  → XPath: " + xpath);
				if (value != null && !value.isEmpty()) {
					log("  → Expected Date: " + value);
					verificationActions.verifyElementDate(xpath, value);
				} else {
					verificationActions.verifyDateFieldHasValue(xpath);
				}
				log("  ✓ Date verified");
				break;

			case "verifycurrentdate":
			case "verifytodaydate":
				log("  → XPath: " + xpath);
				verificationActions.verifyDateFieldIsToday(xpath);
				log("  ✓ Date is today");
				break;

			case "verifyenabled":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementEnabled(xpath);
				log("  ✓ Element enabled");
				break;

			case "verifydisabled":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementDisabled(xpath);
				log("  ✓ Element disabled");
				break;

			case "verifyselected":
			case "verifychecked":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementSelected(xpath);
				log("  ✓ Element selected");
				break;

			case "verifyexists":
			case "verifypresent":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementExists(xpath);
				log("  ✓ Element exists");
				break;

			case "verifyhidden":
			case "verifynotvisible":
				log("  → XPath: " + xpath);
				verificationActions.verifyElementNotVisible(xpath);
				log("  ✓ Element hidden");
				break;

			case "verifycontains":
				log("  → XPath: " + xpath);
				log("  → Expected to contain: " + value);
				verificationActions.verifyElementContainsText(xpath, value);
				log("  ✓ Text contains expected");
				break;

			case "verifycount":
				int count = Integer.parseInt(value);
				log("  → XPath: " + xpath);
				log("  → Expected count: " + count);
				verificationActions.verifyElementCount(xpath, count);
				log("  ✓ Count verified");
				break;

			case "verifyattribute":
				String[] parts = value.split("=", 2);
				log("  → XPath: " + xpath);
				log("  → Attribute: " + parts[0] + " = " + parts[1]);
				verificationActions.verifyElementAttribute(xpath, parts[0], parts[1]);
				log("  ✓ Attribute verified");
				break;

			case "verifypagetitle":
			case "verifytitle":
				log("  → Expected Title: " + value);
				verificationActions.verifyPageTitle(value);

				// Dual verification: If xpath is matched, verify that too
				if (xpath != null && !xpath.isEmpty()) {
					log("  → Also verifying element visibility: " + xpath);
					verificationActions.verifyElementVisible(xpath);
				}

				log("  ✓ Page title verified");
				break;

			case "verifypagetitlecontains":
			case "verifytitlecontains":
				log("  → Expected Title to contain: " + value);
				verificationActions.verifyPageTitleContains(value);
				log("  ✓ Page title verified");
				break;

			case "verifyurl":
			case "verifycurrenturl":
				log("  → Expected URL: " + value);
				verificationActions.verifyCurrentUrl(value);
				log("  ✓ URL verified");
				break;

			case "verifyurlcontains":
				log("  → Expected URL to contain: " + value);
				verificationActions.verifyUrlContains(value);
				log("  ✓ URL verified");
				break;

			case "verifymapshape":
			case "verifypolygon":
			case "verifymapelement":
				log("  → Map XPath: " + xpath);
				verificationActions.verifyMapShapePresent(xpath);
				log("  ✓ Map shape verified");
				break;

			case "verifygridvalue":
				log("  → Grid XPath: " + xpath);
				String[] gridParts = value.split("=", 2);
				if (gridParts.length < 2) {
					throw new RuntimeException(
							"Invalid format for verifygridvalue. Expected 'ColumnName=ExpectedValue', got: " + value);
				}
				String colName = gridParts[0].trim();
				String expectedVal = gridParts[1].trim();

				log("  → Column: " + colName);
				log("  → Expected Value: " + expectedVal);

				verificationActions.verifyGridCellValue(xpath, colName, expectedVal);
				log("  ✓ Grid value verified");
				break;

			case "autoit":
			case "executeautoit":
			case "runautoit":
				log("  → Script Path: " + value);
				// Arguments can be in XPath (if starts with //) or Context (if just string)
				String scriptArgs = "";
				if (context != null && !context.isEmpty()) {
					scriptArgs = context;
				} else if (xpath != null && !xpath.isEmpty()) {
					scriptArgs = xpath;
				}

				if (!scriptArgs.isEmpty()) {
					log("  → Arguments: " + scriptArgs);
				}
				autoItActions.executeScript(value, scriptArgs);
				log("  ✓ AutoIT script executed");
				break;

			case "wait":
				if (value != null && value.matches("\\d+")) {
					// Static Wait (Same for Web and Mobile)
					waitActions.waitFor(Long.parseLong(value));
					log("  ✓ Waited " + value + "ms");
				} else if (xpath != null && !xpath.isEmpty()) {
					// Dynamic Wait for Element
					log("  → Waiting for visibility: " + xpath);

					if (driver instanceof io.appium.java_client.AppiumDriver) {
						// MOBILE LOGIC: Uses the Visibility check which is reliable on both platforms
						waitActions.waitForElementVisible(xpath);
					} else {
						// WEB LOGIC: Original fallback
						waitActions.waitForElementVisible(xpath);
					}
					log("  ✓ Element is now visible");
				} else {
					log("  ⚠ Wait action with no value or xpath - default 1s wait");
					waitActions.waitFor(1000);
				}
				break;

			case "waitforvisible":
			case "wait for visible":
				log("  → XPath: " + xpath);
				waitActions.waitForElementVisible(xpath);
				log("  ✓ Element is visible");
				break;

			case "waitforclickable":
			case "wait for clickable":
				log("  → XPath: " + xpath);
				waitActions.waitForElementClickable(xpath);
				log("  ✓ Element is clickable");
				break;

			case "sql_cleanup":
				try {
					// --- NEW UPDATE: Show 'Trash Bin' screen if in cleanup mode ---
					if (this.isCleanupMode) {
						showCleanupOverlay();
					}

					log("  → SQL Query: " + value);
					com.eit.automation.utils.DatabaseUtils.executeCleanup(value, this.config);
					log("  ✓ SQL Cleanup executed successfully");
				} catch (Exception e) {
					log("  ❌ SQL Cleanup Failed: " + e.getMessage());
					throw e;
				}
				break;

			case "swipe_up":
				log("  → Swiping Up");
				mobileActions.swipe("up");
				log("  ✓ Swiped Up");
				break;

			case "swipe_down":
				log("  → Swiping Down");
				mobileActions.swipe("down");
				log("  ✓ Swiped Down");
				break;

			case "hide_keyboard":
				log("  → Hiding Mobile Keyboard");
				mobileActions.hideKeyboard();
				log("  ✓ Keyboard hidden");
				break;

			case "tap":
				log("  → Tapping Mobile Element: " + xpath);
				mobileActions.tap(xpath);
				log("  ✓ Tapped");
				break;

			default:
				throw new RuntimeException("Unknown action: " + action);
		}
	}

	private String generateXPathFromValue(String value, String context) {
		// If we are on Mobile, do not use complex Web XPaths
		if (driver instanceof io.appium.java_client.AppiumDriver) {
			return value; // Return raw value to be used as ID/Accessibility ID
		}

		if (context != null && !context.isEmpty()) {
			return String.format(
					"//tr[contains(., '%2$s')]//*[contains(text(), '%1$s') or @title='%1$s' or @alt='%1$s' or @aria-label='%1$s' or contains(@class, '%1$s')]",
					value, context);
		}

		return String.format(
				"//*[normalize-space()='%1$s' or @placeholder='%1$s' or @value='%1$s' or @title='%1$s' or @name='%1$s' or @id='%1$s' or @aria-label='%1$s' or @data-testid='%1$s' or contains(text(), '%1$s')]",
				value);
	}

	private void drawPolygon(String xpath, String value) {
		// GUARD: Mobile apps usually don't support this type of coordinate-based Actions drawing
		if (driver instanceof io.appium.java_client.AppiumDriver) {
			log("  ⚠ Skipping drawPolygon: Not supported on Mobile devices.");
			return;
		}

		WebElement map = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));

		// This line would crash on Mobile
		((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", map);

		int width = map.getSize().getWidth();
		int height = map.getSize().getHeight();
		int maxX = (width / 2) - 20;
		int maxY = (height / 2) - 20;

		Actions actions = new Actions(driver);
		String[] points = value.split("\\s*:\\s*");

		int[] first = null;
		for (int i = 0; i < points.length; i++) {
			int[] xy = parsePoint(points[i], maxX, maxY);
			if (i == 0) first = xy;

			actions.moveToElement(map, xy[0], xy[1])
					.click()
					.pause(Duration.ofMillis(700))
					.perform();
		}

		if (first != null) {
			actions.moveToElement(map, first[0], first[1]).perform();

			actions.click().pause(Duration.ofMillis(100))
					.click().pause(Duration.ofMillis(100))
					.click().pause(Duration.ofMillis(200))
					.perform();

			// Keys.TAB/ENTER are browser-specific
			actions.sendKeys(org.openqa.selenium.Keys.TAB).pause(Duration.ofMillis(200))
					.sendKeys(org.openqa.selenium.Keys.ENTER).perform();
		}

		log("  ✓ Polygon committed. Sending TAB to update form state.");
	}
	private int[] parsePoint(String point, int maxX, int maxY) {
		String[] xy = point.split(";");
		if (xy.length < 2) {
			throw new RuntimeException("Invalid point format: '" + point + "'. Expected 'X;Y'");
		}
		int x = Integer.parseInt(xy[0].trim());
		int y = Integer.parseInt(xy[1].trim());
		x = Math.max(-maxX, Math.min(maxX, x));
		y = Math.max(-maxY, Math.min(maxY, y));
		return new int[] { x, y };
	}

	public void close() {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                       CLOSING ALL ACTIVE SESSIONS                              ║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");

		if (driverPool != null && !driverPool.isEmpty()) {
			driverPool.forEach((role, sessionDriver) -> {
				try {
					if (sessionDriver != null) {
						sessionDriver.quit();
						log("✓ Session [" + role.toUpperCase() + "] closed successfully");
					}
				} catch (Exception e) {
					log("⚠ Error closing session [" + role.toUpperCase() + "]: " + e.getMessage());
				}
			});
			driverPool.clear();
			waitPool.clear();
			driver = null;
			wait = null;
		} else if (driver != null) {
			// Fallback for single driver cases
			driver.quit();
			log("✓ Driver closed");
		}

		log("");
	}

	public WebDriver getDriver() {
		return driver;
	}

	// ========================================
	// LOGGING METHODS - CLEAN & READABLE
	// ========================================

	private void log(String message) {
		if (detailedLogging) {
			System.out.println("[" + getCurrentTime() + "] " + message);
		}
	}

	private void logStepHeader(int stepNumber, int totalSteps, TestStep step) {
		log("");
		log("┌────────────────────────────────────────────────────────────────────────────────┐");
		log("│ STEP " + stepNumber + "/" + totalSteps + " │ " + step.getAction().toUpperCase()
				+ " ".repeat(Math.max(1, 68 - step.getAction().length() - String.valueOf(stepNumber).length()
						- String.valueOf(totalSteps).length()))
				+ "│");
		log("├────────────────────────────────────────────────────────────────────────────────┤");

		String value = step.getValue() != null ? step.getValue() : "";
		if (value.length() > 70)
			value = value.substring(0, 67) + "...";
		if (!value.isEmpty()) {
			log("│ Value: " + value + " ".repeat(Math.max(1, 73 - value.length())) + "│");
		}

		String xpath = step.getXpath() != null ? step.getXpath() : "";
		if (xpath.length() > 70)
			xpath = xpath.substring(0, 67) + "...";
		if (!xpath.isEmpty()) {
			log("│ XPath: " + xpath + " ".repeat(Math.max(1, 73 - xpath.length())) + "│");
		}

		log("└────────────────────────────────────────────────────────────────────────────────┘");
	}

	private void logStepSuccess(int stepNumber, long duration) {
		log("");
		log("  ✅ STEP " + stepNumber + " PASSED [" + duration + "ms]");
		log("");
	}

	private void logStepFailure(int stepNumber, long duration, Exception e) {
		log("");
		System.err.println("  ❌ STEP " + stepNumber + " FAILED [" + duration + "ms]");
		System.err.println("  ┌─ Error Details ─────────────────────────────────────────────────────────");
		System.err.println("  │ Must Fix: " + (e.getMessage() != null ? e.getMessage() : "Unknown Error"));
		if (e.getCause() != null) {
			System.err.println("  │ Cause: " + e.getCause().getMessage());
		}
		System.err.println("  └─────────────────────────────────────────────────────────────────────────");
		log("");
	}

	private void logTestSummary(String testName, long duration) {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                           TEST CASE SUMMARY                                    ║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		log("║  Test Case: " + padRight(testName, 66) + "║");
		log("║  Total Steps: " + padRight(String.valueOf(totalStepsExecuted), 63) + "║");
		log("║  Passed: " + padRight(String.valueOf(passedSteps), 68) + "║");
		log("║  Failed: " + padRight(String.valueOf(failedSteps), 68) + "║");
		log("║  Duration: " + padRight(formatDuration(duration), 66) + "║");
		log("║  End Time: " + padRight(getCurrentTime(), 66) + "║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		if (failedSteps == 0) {
			log("║  STATUS: ✓ ALL STEPS PASSED                                                   ║");
		} else {
			log("║  STATUS: ✗ " + failedSteps + " STEP(S) FAILED                                            ║");
		}
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		log("");
		log("→ Browser remains open for inspection");
		log("→ Call executor.close() when done");
		log("");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		log("");
		log("→ All sessions (Web/Mobile) remain active for inspection");
		log("→ Call executor.close() to terminate all sessions");
		log("");
	}

	private void logCriticalFailure(String testName, long duration, Exception e) {
		log("");
		log("╔════════════════════════════════════════════════════════════════════════════════╗");
		log("║                         CRITICAL TEST FAILURE                                  ║");
		log("╠════════════════════════════════════════════════════════════════════════════════╣");
		log("║  Test Case: " + padRight(testName, 66) + "║");
		log("║  Duration: " + padRight(formatDuration(duration), 67) + "║");
		log("║  Error: " + padRight(e.getClass().getSimpleName(), 70) + "║");
		log("╚════════════════════════════════════════════════════════════════════════════════╝");
		System.err.println("Full Stack Trace:");
		e.printStackTrace();
	}

	private String getCurrentTime() {
		return LocalDateTime.now().format(timeFormatter);
	}

	private String formatDuration(long milliseconds) {
		long seconds = milliseconds / 1000;
		long ms = milliseconds % 1000;
		if (seconds > 60) {
			long minutes = seconds / 60;
			seconds = seconds % 60;
			return String.format("%dm %ds %dms", minutes, seconds, ms);
		} else {
			return String.format("%ds %dms", seconds, ms);
		}
	}

	// Method to enable/disable cleanup mode from Main.java
	public void setCleanupMode(boolean mode) {
		this.isCleanupMode = mode;
	}

	private void showCleanupOverlay() {
		try {
			if (driver == null) return;
			org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

			String cleanupScript =
					"var cleanOverlay = document.getElementById('cleanup-screen');" +
							"if(!cleanOverlay) {" +
							"  cleanOverlay = document.createElement('div');" +
							"  cleanOverlay.id = 'cleanup-screen';" +
							"  cleanOverlay.style.cssText = 'position:fixed; top:52px; left:0; width:100%; height:100%; " +
							"                               background:#0b0b0f; z-index:999998; " +
							"                               display:flex; flex-direction:column; align-items:center; " +
							"                               justify-content:center; color:white; " +
							"                               font-family:\"Segoe UI\", Tahoma, sans-serif;';" +
							"  " +
							"  cleanOverlay.innerHTML = " +
							"    '<div style=\"display:flex; flex-direction:column; align-items:center; margin-bottom:40px;\">' + " +
							"    '  <div style=\"font-weight:900; font-size:60px; letter-spacing:4px; color:#fff; margin-bottom:0;\">ABCD</div>' + " +
							"    '  <div style=\"font-size:14px; color:#00d4ff; font-weight:bold; text-transform:uppercase; letter-spacing:2px;\">Test Data Cleanup</div>' + " +
							"    '</div>' + " +
							"    '<div style=\"position:relative; margin-bottom:30px;\">' + " +
							"    '  <div style=\"font-size:100px; filter: drop-shadow(0 0 15px #00d4ff); animation: pulse 2s infinite;\">🗄️</div>' + " +
							"    '</div>' + " +
							"    '<div style=\"text-align:center; border: 1px solid #1a1a24; padding: 40px 60px; border-radius:15px; " +
							"                 background: #12121a; box-shadow: 0 15px 40px rgba(0,0,0,0.7);\">' + " +
							"    '  <h2 style=\"color:#ff4444; margin:0; font-size:18px; letter-spacing:1px; font-weight:bold;\">CLEANUP IN PROGRESS</h2>' + " +
							"    '  <div style=\"width:250px; background:#1a1a1a; height:4px; margin:25px auto; border-radius:10px; overflow:hidden;\">' + " +
							"    '    <div style=\"width:40%; background:#00d4ff; height:100%; animation: scanLine 1.5s infinite ease-in-out;\"></div>' + " +
							"    '  </div>' + " +
							"    '  <p style=\"font-size:15px; color:#999; margin:0; line-height:1.8;\">' + " +
							"    '    System is securely removing test data records from the database.<br>' + " +
							"    '    <span style=\"color:#fff; font-weight:bold; background: rgba(255,68,68,0.2); padding: 2px 6px; border-radius:3px;\">DO NOT CLOSE THE BROWSER</span><br>' + " +
							"    '    <span style=\"color:#fff; font-weight:bold; background: rgba(255,68,68,0.2); padding: 2px 6px; border-radius:3px;\">DO NOT STOP TEST EXECUTION</span>' + " +
							"    '  </p>' + " +
							"    '</div>' + " +
							"    '<style>' + " +
							"    '  @keyframes pulse { 0% { transform: scale(1); opacity: 0.8; } 50% { transform: scale(1.08); opacity: 1; } 100% { transform: scale(1); opacity: 0.8; } }' + " +
							"    '  @keyframes scanLine { 0% { width: 0%; margin-left: 0%; } 50% { width: 50%; margin-left: 25%; } 100% { width: 0%; margin-left: 100%; } }' + " +
							"    '</style>';" +
							"  document.body.appendChild(cleanOverlay);" +
							"  document.body.style.overflow = 'hidden';" +
							"}";

			js.executeScript(cleanupScript);
		} catch (Exception e) {
			// Fail silently
		}
	}

	private void updateBrowserOverlay(String sheet, String test, int stepNum, TestStep step) {
		try {
			// --- UNIVERSAL DASHBOARD LOGIC ---
			// We look for the "web" session in our pool to act as the display monitor
			WebDriver webDisplay = driverPool.get("web");

			// If the web browser isn't open, we can't show the overlay, so we exit
			if (webDisplay == null) return;

			// We use the webDisplay specifically for the JS injection
			org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) webDisplay;

			String action = step.getAction().toUpperCase();

			// Detect current platform to add a visual indicator to the overlay
			String platformIcon = (driver instanceof io.appium.java_client.AppiumDriver) ? "📱 MOBILE: " : "💻 WEB: ";

			String rawDetail = (step.getValue() != null && !step.getValue().isEmpty()) ? step.getValue() :
					(step.getXpath() != null ? step.getXpath() : "");

			// Combine the platform icon with the detail
			String detail = platformIcon + rawDetail;

			String script =
					"var overlay = document.getElementById('automation-overlay');" +
							"if(!overlay) {" +
							"  document.body.style.marginTop = '50px';" +
							"  overlay = document.createElement('div');" +
							"  overlay.id = 'automation-overlay';" +
							"  overlay.style.cssText = 'position:fixed; top:0; left:0; width:100%; height:52px; " +
							"                           background:rgba(15, 15, 20, 0.98); color:#00d4ff; " +
							"                           padding:0 20px; z-index:999999; " +
							"                           font-family:Segoe UI, Tahoma, sans-serif; " +
							"                           border-bottom:3px solid #00d4ff; " +
							"                           display:grid; grid-template-columns: auto auto auto 1fr; " +
							"                           align-items:center; gap:25px; box-shadow:0 4px 12px rgba(0,0,0,0.5); " +
							"                           pointer-events:none; opacity:1.0;';" +
							"  " +
							"  overlay.innerHTML = " +
							"    '<div id=\"brand-container\" style=\"display:flex; flex-direction:column; align-items:center; line-height:1; min-width:80px\">' + " +
							"    '  <span style=\"font-weight:900; font-size:18px; letter-spacing:1px; color:#fff\">ABCD</span>' + " +
							"    '  <span style=\"font-size:9px; color:#00d4ff; font-weight:bold; margin-top:2px; text-transform:uppercase\">Test Suite</span>' + " +
							"    '</div>' + " +
							"    '<div id=\"overlay-timer\" style=\"color:#fff; background:#222; padding:4px 12px; border-radius:20px; border:1px solid #444; font-weight:bold; min-width:75px; text-align:center; font-size:13px\">⏱️ 00:00</div>' + " +
							"    '<div id=\"overlay-left\" style=\"font-size:13px; white-space:nowrap;\"></div>' + " +
							"    '<div id=\"overlay-right\" style=\"text-align:right; font-size:13px; white-space:nowrap;\"></div>';" +
							"  document.body.appendChild(overlay);" +
							"}" +
							"" +
							"/* Update text containers */" +
							"document.getElementById('overlay-left').innerHTML = \"<span style='color:#666'>📄</span> \" + arguments[0] + \" <span style='color:#444;margin:0 5px'>|</span> <span style='color:#666'>🧪</span> \" + arguments[1];" +
							"document.getElementById('overlay-right').innerHTML = \"<b style='color:#00d4ff'>🔢 STEP \" + arguments[2] + \":</b> <span style='color:#fff; background:#333; padding:3px 8px; border-radius:4px; margin:0 5px'>\" + arguments[3] + \"</span> <span style='color:#bbb; font-size:11px'>\" + arguments[4] + \"</span>\";" +
							"" +
							"if (!window.automationStartTime) {" +
							"  window.automationStartTime = Date.now();" +
							"}" +
							"if (!window.automationInterval) {" +
							"  window.automationInterval = setInterval(function() {" +
							"    var timerElem = document.getElementById('overlay-timer');" +
							"    if(timerElem) {" +
							"      var diff = Math.floor((Date.now() - window.automationStartTime) / 1000);" +
							"      var mins = Math.floor(diff / 60); var secs = diff % 60;" +
							"      timerElem.innerHTML = '⏱️ ' + (mins < 10 ? '0' + mins : mins) + ':' + (secs < 10 ? '0' + secs : secs);" +
							"    }" +
							"  }, 1000);" +
							"}";

			js.executeScript(script, this.excelName, test, stepNum, action, detail);
		} catch (Exception e) {
			// Fail silently - if the web window is navigated away or closed, we don't want to stop the mobile test
		}
	}

	public WebDriverWait getWait() {
		return this.wait;
	}

	/**
	 * Updates the active wait object and ensures the pool is updated.
	 */
	public void setWait(WebDriverWait wait) {
		this.wait = wait;
		if (currentSessionRole != null) {
			waitPool.put(currentSessionRole, wait);
		}
	}

	/**
	 * Updates the active driver and ensures the pool is updated.
	 * Also triggers a refresh of action handlers (click, input, etc.)
	 */
	public void setDriver(WebDriver driver) {
		this.driver = driver;
		if (currentSessionRole != null) {
			driverPool.put(currentSessionRole, driver);
			// Crucial: Update handlers so they use the new driver immediately
			refreshActionHandlers();
		}
	}

	private String padRight(String s, int n) {
		// Handle null strings to prevent formatting errors
		String val = (s == null) ? "" : s;
		return String.format("%-" + n + "s", val);
	}

	public void setDetailedLogging(boolean enabled) {
		this.detailedLogging = enabled;
	}

	// Getters for reporting remain the same
	public int getTotalStepsExecuted() { return totalStepsExecuted; }
	public int getPassedSteps() { return passedSteps; }
	public int getFailedSteps() { return failedSteps; }
}
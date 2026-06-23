package com.eit.automation.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.Point;
import org.openqa.selenium.Dimension;
import java.time.Duration;
import java.util.Collections;
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

	private boolean isHybridFlow = false;
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

		log("✓ Report generator configured");
		log("");
	}

	/**
	 * Preserves all your existing Chrome functionalities while adding them to the Pool
	 */

	public void setHybridFlow(boolean isHybrid) {
		this.isHybridFlow = isHybrid;
	}

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

		// --- FIXED: Removed "--start-maximized" to prevent hard OS maximization lock at boot ---
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
	 * Cleaned: Responsible only for instantiating the session cleanly.
	 * Dimension changes are deferred to runtime step evaluation.
	 */
	public void setupWebDriver() {
		log("🌐 Initializing Chrome Browser Framework Context...");

		// Initialize the driver session
		initializeWebDriver("web");

		log("✓ Web Session [web] successfully registered in universal context pools.");
	}
	/**
	 * Initializes a Mobile (Android/Appium) session for a specific role.
	 * @param role 'user' or 'driver'
	 */
	public void setupMobileDriver(String role) {
		// 🚀 FIX: Sanitize the role argument to lowercase to ensure it perfectly matches config.properties keys (e.g., "user" or "driver")
		String cleanRole = role.toLowerCase().trim();

		log("📱 Initializing Mobile Emulator for Role: [" + cleanRole.toUpperCase() + "]");

		try {
			// Use UiAutomator2Options instead of DesiredCapabilities to ensure W3C compliance
			io.appium.java_client.android.options.UiAutomator2Options options = new io.appium.java_client.android.options.UiAutomator2Options();

			options.setPlatformName("Android");
			options.setAutomationName("UiAutomator2");

			// Pull details dynamically from config using the sanitized lowercase role string
			String targetUdid = config.getProperty(cleanRole + ".device.id");
			String targetApk = config.getProperty(cleanRole + ".apk.path");
			String targetPackage = config.getProperty(cleanRole + ".app.package");
			String targetActivity = config.getProperty(cleanRole + ".app.activity");

			// Safety Guard: Fail early if properties are completely missing or misspelled in config.properties
			if (targetUdid == null || targetApk == null) {
				throw new IllegalArgumentException("❌ Configuration properties missing for role prefix: [" + cleanRole + "]. Please verify your config.properties file entries.");
			}

			options.setUdid(targetUdid);
			options.setApp(targetApk);
			options.setAppPackage(targetPackage);
			options.setAppActivity(targetActivity);

			// 🚀 TARGETED RESET SYSTEM: Wipes app data cache completely on the initial execution boot
			options.setNoReset(false);
			options.setCapability("fullReset", false);

			// 🚀 MULTI-SESSION FIX: Stops Appium from killing background instances during cross-device switching
			options.setCapability("shouldTerminateApp", false);
			options.setCapability("dontStopAppOnReset", true); // ← Forces Appium to keep the idle app running in its emulator viewport

			// 🚀 IDLE TIMEOUT FIX: Prevents Appium from silently killing this background session
			// while you are interacting with Web or Driver sessions (Increases limit from 60s to 10 minutes)
			options.setNewCommandTimeout(Duration.ofMinutes(10));

			// Dismisses any system dialogs (like "Android Setup isn't responding") automatically
			options.setCapability("autoDismissAlerts", true);



			// CRITICAL FIX: Set to false while noReset is false so UIAutomator2 can properly hook the app process
			options.setCapability("skipDeviceInitialization", false);
			options.setCapability("skipServerInstallation", false);

			URL url = new URL(config.getProperty("appium.url"));

			// Initialize the driver with options
			AndroidDriver mobileDriver = new AndroidDriver(url, options);

			// --- GUARD FOR MOBILE APP LAUNCH ---
			log("⏳ Waiting for app package [" + cleanRole + "] to launch...");
			WebDriverWait launchWait = new WebDriverWait(mobileDriver, Duration.ofSeconds(20));

			// This ensures the driver is fully initialized before returning
			launchWait.until(d -> ((AndroidDriver)d).getCurrentPackage() != null);

			// Add to our Universal Pool using clean lowercase key mapping
			driverPool.put(cleanRole, mobileDriver);

			WebDriverWait mobileWait = new WebDriverWait(mobileDriver, Duration.ofSeconds(30));
			waitPool.put(cleanRole, mobileWait);

			// If this is the first driver being created, set it as active active session pointer context
			if (this.driver == null) {
				this.wait = mobileWait;
				this.driver = mobileDriver;
				this.currentSessionRole = cleanRole;
			}

			log("✅ Mobile session started and stabilized for role: [" + cleanRole + "] on device: " + targetUdid);

		} catch (Exception e) {
			log("❌ Failed to start Mobile session for " + cleanRole + ": " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
	/**
	 * Refreshes handlers to point to the current active driver/wait objects
	 */
	private void refreshActionHandlers() {
		// 1. Common Actions (Both Web and Mobile)
		this.waitActions = new WaitActions(driver, wait);
		this.clickActions = new ClickActions(driver, wait, waitActions);
		this.inputActions = new InputActions(driver, wait, waitActions);
		this.verificationActions = new VerificationActions(driver, wait, waitActions);

		// 2. Web-Specific Actions
		if (!(driver instanceof io.appium.java_client.AppiumDriver)) {
			this.fileActions = new FileActions(driver, wait, waitActions);
			this.toastActions = new ToastActions(driver, wait, waitActions);
			this.scrollActions = new ScrollActions(driver, wait, waitActions);
			this.autoItActions = new AutoItActions(driver, wait, waitActions);

			// CRITICAL: Disable mobile actions when on Web
			this.mobileActions = null;
			log("  • Handlers refreshed for WEB context");
		}
		// 3. Mobile-Specific Actions
		else {
			this.mobileActions = new MobileActions((io.appium.java_client.AppiumDriver) driver, wait);

			// CRITICAL: Disable web-only actions when on Mobile
			this.fileActions = null;
			this.toastActions = null;
			this.scrollActions = null;
			this.autoItActions = null;
			log("  • Handlers refreshed for MOBILE context");
		}
	}
	/**
	 * Execute list of test steps - CONTINUES ON ERROR, DOESN'T CLOSE BROWSER
	 */
	public boolean run(String sheetName, List<TestStep> steps, String testCaseName) {
		long testStartTime = System.currentTimeMillis();

		// 🚀 1. LOCAL SIGNATURE PRE-SCAN: Scan ONLY the incoming sheet for hybrid execution signatures
		boolean currentSheetIsHybrid = false;
		if (steps != null) {
			for (TestStep step : steps) {
				if (step.getAction() != null) {
					String action = step.getAction().toLowerCase().trim();
					String value = step.getValue() != null ? step.getValue().toLowerCase().trim() : "";

					if (action.equals("switch_to") || action.equals("switchsession")) {
						// ✅ FIXED: Removed the duplicate "String value =" re-declaration line entirely
						if (!value.isEmpty() && !value.equals("web")) {
							currentSheetIsHybrid = true;
							break; // Found a dynamic mobile execution target (store, user, driver, etc.)!
						}
					}
				}
			}
		}

		// 🚀 2. STICKY FRAMEWORK LOCK: If this or any previously executed sheet triggered hybrid mode,
		// lock the flag to true so pure-web dependency sheets (add customer/driver) don't alter the layout.
		if (currentSheetIsHybrid) {
			this.isHybridFlow = true;
		}

		// 🚀 3. RUNTIME VIEWPORT ADJUSTMENT: Dynamically handle window constraints based on the sticky flag state
		WebDriver webDriverInstance = driverPool.get("web");
		if (webDriverInstance != null) {
			try {
				// ⏳ STABILIZATION PAUSE: Gives Chrome window rendering thread 500ms to settle down cleanly
				Thread.sleep(500);

				if (this.isHybridFlow) {
					log("📱 Hybrid Flow Context Active (Locked)! Retaining split-screen layout on the left side (X=0)...");
					webDriverInstance.manage().window().setSize(new org.openqa.selenium.Dimension(960, 1080));
					webDriverInstance.manage().window().setPosition(new org.openqa.selenium.Point(0, 0));
				} else {
					log("🖥️ Web-Only Flow Detected via Context Analysis! Maximizing browser workspace...");
					webDriverInstance.manage().window().maximize();
				}
			} catch (Exception e) {
				log("⚠️ Warning: Failed to apply dynamic browser window configuration changes: " + e.getMessage());
			}
		}

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
				String action = step.getAction().toLowerCase().trim();

				// --- 1. SESSION SWITCHING (With Strict Reporting & Pointer Synchronization Fix) ---
				if (action.equals("switch_to") || action.equals("switchsession")) {
					logStepHeader(stepNumber, steps.size(), step);
					try {
						// FIX: Sanitize the target role value to lowercase immediately
						String targetRole = (step.getValue() != null) ? step.getValue().toLowerCase().trim() : "";

						if (targetRole.isEmpty()) {
							throw new IllegalArgumentException("Switch action encountered but the target session role value is empty!");
						}

						// Execute context swap
						switchSession(targetRole);

						// REFRESH POINTERS: Force the run loop state to track the active objects explicitly
						this.driver = driverPool.get(targetRole);
						this.wait = waitPool.get(targetRole);

						// CRITICAL FIX: Re-wire your interaction engines (ClickActions, InputActions) mid-test loop!
						refreshActionHandlers();

						// MOBILE STABILIZATION: Give Appium 3 seconds to bring the app viewport to the front cleanly
						if (this.driver instanceof io.appium.java_client.AppiumDriver) {
							log("  ⏳ Stabilizing Mobile Session Viewport...");
							Thread.sleep(3000);
						}

						passedSteps++;
						totalStepsExecuted++;

						// Add this so the HTML execution report prints the switch progression clearly!
						if (reportGenerator != null) {
							reportGenerator.logStep(stepNumber, step, "PASSED", "Successfully switched context to: [" + targetRole.toUpperCase() + "]", this.driver);
						}
						continue;

					} catch (Exception e) {
						// If the switch fails (e.g. emulator not open), we MUST halt the execution stack
						log("❌ Session switch execution context failed: " + e.getMessage());
						if (reportGenerator != null) {
							reportGenerator.logStep(stepNumber, step, "FAILED", "Switch failed: " + e.getMessage(), this.driver);
						}
						break;
					}
				}

				// --- UNIVERSAL OVERLAY TRACKING ---
				// Always update the overlay as long as the 'web' session exists to display it
				if (driverPool.containsKey("web")) {
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
						reportGenerator.logStep(stepNumber, step, "PASSED", "", this.driver);
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

						reportGenerator.logStep(stepNumber, step, "FAILED", errorDetails.toString(), this.driver);
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
		return run("Default", steps, "Unnamed Test Case");
	}
	/**
	 * Switches the active driver and wait objects to the requested role.
	 * Role can be 'web', 'user', or 'driver'.
	 */
	public void switchSession(String role) {
		if (role == null || role.trim().isEmpty()) {
			throw new IllegalArgumentException("❌ Cannot switch session context: Role target name is completely blank!");
		}

		String targetRole = role.toLowerCase().trim();
		log("🔄 SWITCHING CONTEXT: Moving focus to [" + targetRole.toUpperCase() + "]");

		// 1. PROACTIVE CHECK: If session doesn't exist, handle dynamic initialization safely
		if (!driverPool.containsKey(targetRole)) {
			log("⚠️ Session [" + targetRole + "] not found in runtime pool. Attempting auto-initialization...");

			if (targetRole.equals("web")) {
				setupWebDriver();
			} else {
				// This calls your updated clean lowercase layout method
				setupMobileDriver(targetRole);
			}
		}

		// 2. ACTIVATE THE SESSION: Update the active framework pointers
		if (driverPool.containsKey(targetRole)) {
			this.driver = driverPool.get(targetRole);
			this.wait = waitPool.get(targetRole);
			this.currentSessionRole = targetRole;

			// 3. RE-WIRE HANDLERS: Attach UI action class wrappers directly to the active driver engine
			refreshActionHandlers();

			log("✅ Context switched successfully to [" + targetRole.toUpperCase() + "]");
		} else {
			log("❌ CRITICAL ERROR: Failed to initialize target session context [" + targetRole + "]");
			throw new RuntimeException("Could not switch to session: " + targetRole);
		}
	}

	/**
	 * Re-initializes all action classes with the current active driver.
	 * This ensures 'clickActions', 'inputActions', etc., are talking to the right device.
	 */
	/**
	 * Execute single test step - NEVER THROWS, JUST LOGS ERRORS
	 */
	/**
	 * Dynamically resolves the locator strategy based on the Excel target string.
	 * Bypasses XML parsing overhead by leveraging native matching engines.
	 */
	public org.openqa.selenium.By getDynamicLocator(String targetFromExcel) {
		if (targetFromExcel == null || targetFromExcel.trim().isEmpty()) {
			return null;
		}

		String cleanTarget = targetFromExcel.trim();

		// 1. Native Resource ID strategy
		if (cleanTarget.startsWith("id=")) {
			return org.openqa.selenium.By.id(cleanTarget.replace("id=", "").trim());
		}
		// 2. Appium Accessibility ID strategy (content-desc)
		else if (cleanTarget.startsWith("accessibility=")) {
			return io.appium.java_client.AppiumBy.accessibilityId(cleanTarget.replace("accessibility=", "").trim());
		}
		// 3. Native Android UIAutomator engine strategy (For text matches, indexing, states)
		else if (cleanTarget.startsWith("automator=")) {
			return io.appium.java_client.AppiumBy.androidUIAutomator(cleanTarget.replace("automator=", "").trim());
		}
		// 4. Default Fallback to standard XPath
		else {
			return org.openqa.selenium.By.xpath(cleanTarget);
		}
	}

	private void executeStep(TestStep step) throws Exception {
		if (step == null) return;

		// 🚀 MASTER CENTRAL INTERCEPTOR: Smart Safe Property Extraction
		if (step.getXpath() != null) {
			String xp = step.getXpath().trim();
			if (!xp.contains("automator=")) {
				if (xp.startsWith("\"") && xp.endsWith("\"") && xp.length() > 1) {
					xp = xp.substring(1, xp.length() - 1).trim();
				}
				step.setXpath(xp);
			} else {
				step.setXpath(xp);
			}
		}
		if (step.getValue() != null) {
			String val = step.getValue().trim();
			if (!val.contains("automator=")) {
				if (val.startsWith("\"") && val.endsWith("\"") && val.length() > 1) {
					val = val.substring(1, val.length() - 1).trim();
				}
				step.setValue(val);
			} else {
				step.setValue(val);
			}
		}
		if (step.getContext() != null) {
			String ctx = step.getContext().trim();
			if (!ctx.contains("automator=")) {
				if (ctx.startsWith("\"") && ctx.endsWith("\"") && ctx.length() > 1) {
					ctx = ctx.substring(1, ctx.length() - 1).trim();
				}
				step.setContext(ctx);
			} else {
				step.setContext(ctx);
			}
		}

		// 1. GENERIC STRUCTURE EXTRACTOR FOR UPLOAD ACTIONS
		if (step.getAction() != null && step.getAction().equalsIgnoreCase("uploadfile")) {
			String fileTarget = null;
			String locatorKeyTarget = null;

			List<String> textPool = new ArrayList<>();
			if (step.getValue() != null && !step.getValue().isEmpty()) textPool.add(step.getValue());
			if (step.getXpath() != null && !step.getXpath().isEmpty()) textPool.add(step.getXpath());
			if (step.getContext() != null && !step.getContext().isEmpty()) textPool.add(step.getContext());

			for (String text : textPool) {
				if (text.startsWith("//") || text.startsWith("(") ||
						text.startsWith("accessibility=") || text.startsWith("id=") || text.startsWith("automator=")) {
					locatorKeyTarget = text;
				}
				else if (text.contains("/") || text.contains("\\")) {
					fileTarget = text;
				}
				else {
					locatorKeyTarget = text;
				}
			}

			if (locatorKeyTarget != null) {
				step.setXpath(locatorKeyTarget);
			}
			if (fileTarget != null) {
				step.setValue(fileTarget);
			}
			log("  🔀 INTERCEPTOR: Structural assignment complete. Key: [" + step.getXpath() + "], Path: [" + step.getValue() + "]");
		}

		// 2. Resolve Custom Element Mappings from Properties Map files
		if (step.getXpath() != null && !step.getXpath().isEmpty()
				&& !step.getXpath().startsWith("//") && !step.getXpath().startsWith("(")
				&& !step.getXpath().startsWith("accessibility=") && !step.getXpath().startsWith("id=") && !step.getXpath().startsWith("automator=")) {

			String resolvedXpath = com.eit.automation.core.LocatorMapper.getXPath(step.getXpath());
			// If properties file returns a value, update the step target immediately
			if (resolvedXpath != null && !resolvedXpath.isEmpty()) {
				step.setXpath(resolvedXpath);
			}
		}

		// 3. Resolve Value parameters
		if (step.getValue() != null && !step.getValue().isEmpty()
				&& !step.getValue().contains("/") && !step.getValue().contains("\\")
				&& !step.getValue().startsWith("//") && !step.getValue().startsWith("(")
				&& !step.getValue().startsWith("accessibility=") && !step.getValue().startsWith("id=") && !step.getValue().startsWith("automator=")) {
			String resolvedValue = com.eit.automation.core.LocatorMapper.getXPath(step.getValue());
			if (resolvedValue != null && !resolvedValue.isEmpty()) {
				step.setValue(resolvedValue);
			}
		}

		// FIX: 🚀 LIVE VARIABLE INJECTOR (Solves Step 22 {areaName} issue)
		// Seamlessly resolves property file XPaths containing runtime template markers
		if (step.getXpath() != null && step.getXpath().contains("{") && step.getXpath().contains("}")) {
			String processedXpath = com.eit.automation.parser.StepParser.replaceSavedVariablesOnly(step.getXpath());
			step.setXpath(processedXpath);
		}
		// 🚀 MASTER CENTRAL INTERCEPTOR END

		// --- Continue with original framework extraction logic ---
		String action = step.getAction().toLowerCase();
		String value = step.getValue();
		String xpath = step.getXpath();
		String context = step.getContext();

		log("  ⚙ Action: " + action.toUpperCase());

		// 1. PAGEFACTORY LOOKUP
		if ((xpath == null || xpath.isEmpty()) && (value != null && !value.isEmpty())) {
			if (pageObjectManager != null) {
				WebElement element = pageObjectManager.findElementByName(value);
				if (element != null) {
					log("  → Found PageFactory Element: " + value);
				}
			}
		}

		// Auto-generate XPath if empty (Legacy fallback execution safety setup)
		if (xpath == null || xpath.isEmpty()) {
			if (value != null && !value.isEmpty()) {

				boolean isDirectXPath = value.startsWith("//") || value.startsWith("(");

				if (isDirectXPath) {
					xpath = value;
					log("  → Using direct XPath from Value column: " + xpath);
				}
				else if (!(driver instanceof io.appium.java_client.AppiumDriver) &&
						!action.startsWith("verifytoast") &&
						!action.equals("robotupload") &&
						!action.equalsIgnoreCase("drawpolygon")) { // Safety bypass preserved for canvas layout steps

					xpath = generateXPathFromValue(value, context);
					log("  → Auto-generated Web XPath: " + xpath);
				}
				else if (driver instanceof io.appium.java_client.AppiumDriver) {
					xpath = value;
					log("  → Using Prefixed Mobile Engine Selector: " + xpath);
				}
				else {
					log("  → Using auto-detection for toast/alert");
				}
			}
		}

		if ((xpath == null || xpath.isEmpty()) && (value == null || value.isEmpty())) {
			log("  ⚠ Both XPath and Value empty - skipping");
		}

		// Synchronize local tracking modifications back to master TestStep object reference
		step.setXpath(xpath);
		step.setValue(value);

		// --- Proceed to your framework's actual WebElement interaction and action logic below ---



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
				log("  → Locator Target: " + xpath);
				// UPDATED: Uses dynamic locator strategy instead of a hardcoded By.xpath
				org.openqa.selenium.By scrollLocator = getDynamicLocator(xpath);
				WebElement elementToScroll = wait.until(ExpectedConditions.presenceOfElementLocated(scrollLocator));
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
					mobileActions.tap(xpath);
				} else {
					// WEB LOGIC: Preserve your original ClickActions logic
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
					log("  → Opened mobile picker, searching for option text: " + value);

					// 2. Locate the specific text option natively using UIAutomator for maximum speed
					org.openqa.selenium.By mobileItemSelector = io.appium.java_client.AppiumBy.androidUIAutomator(
							"new UiSelector().text(\"" + value + "\").description(\"" + value + "\")"
					);
					WebElement item = wait.until(ExpectedConditions.elementToBeClickable(mobileItemSelector));
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

				// 🚀 DYNAMIC VARIABLE LOOKUP: Resolves configuration keys (e.g. regression.driver.phone) automatically!
				String resolvedValue = value;
				if (value != null && config != null && config.containsKey(value.trim())) {
					resolvedValue = config.getProperty(value.trim());
					log("  ⚙️ Dynamic Test Data Resolved: Replacing property key '" + value.trim() + "' with configured value.");
				}

				// --- PRESERVED: Security Masking for Logging ---
				if (xpath != null && (xpath.toLowerCase().contains("password") || xpath.toLowerCase().contains("pwd")
						|| xpath.toLowerCase().contains("pass"))) {
					log("  → Value: ** (hidden)");
				} else {
					log("  → Value: "
							+ (resolvedValue != null && resolvedValue.length() > 60 ? resolvedValue.substring(0, 60) + "..." : resolvedValue));
				}

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					WebElement mobileElement = null;
					int attempts = 0;

					while (attempts < 2) {
						try {
							// ⚡ UPDATED LOOKUP: Resolves prefix engine strategies natively
							org.openqa.selenium.By activeMobileLocator = getDynamicLocator(xpath);
							mobileElement = wait.until(ExpectedConditions.presenceOfElementLocated(activeMobileLocator));

							String lowerXpath = xpath != null ? xpath.toLowerCase() : "";

							// ⚡ PERFORMANCE BOOST: Clean matching recognizing the new native selectors
							boolean isOtpFieldXpath = lowerXpath.contains("verify") || lowerXpath.contains("otp") || lowerXpath.contains("instance") || lowerXpath.contains("automator");
							boolean isInputTarget = lowerXpath.contains("edittext") || lowerXpath.contains("descendant") || lowerXpath.contains("widget.view");

							// 🌍 SAFE LENGTH LIMITS: Targets strictly 4 or 6-digit verification codes using the resolvedValue.
							boolean isNumericOtpValue = resolvedValue != null && resolvedValue.matches("\\d+") && (resolvedValue.length() == 4 || resolvedValue.length() == 6);

							if ((isOtpFieldXpath || isInputTarget) && isNumericOtpValue) {
								log("  ⚠️ Universal Numeric OTP Sequence Detected. Executing hardware stream focus tap...");
								io.appium.java_client.android.AndroidDriver androidDriver = (io.appium.java_client.android.AndroidDriver) driver;

								org.openqa.selenium.Point location = mobileElement.getLocation();
								org.openqa.selenium.Dimension size = mobileElement.getSize();

								// Focus tap dynamically relative to the width of the target container to unlock native keyboard focus
								int targetX = location.getX() + (int)(size.getWidth() * 0.12);
								int targetY = location.getY() + (size.getHeight() / 2);

								log("    → Waking up focus bounds dynamically at: (" + targetX + ", " + targetY + ")");

								org.openqa.selenium.interactions.PointerInput initialFinger =
										new org.openqa.selenium.interactions.PointerInput(org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "initialFinger");
								org.openqa.selenium.interactions.Sequence baseTap = new org.openqa.selenium.interactions.Sequence(initialFinger, 1);

								baseTap.addAction(initialFinger.createPointerMove(java.time.Duration.ZERO, org.openqa.selenium.interactions.PointerInput.Origin.viewport(), targetX, targetY));
								baseTap.addAction(initialFinger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
								baseTap.addAction(initialFinger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
								androidDriver.perform(java.util.Collections.singletonList(baseTap));

								log("  ⚠️ Waiting for input engine surface to stabilize...");
								try { Thread.sleep(1200); } catch (InterruptedException e) {}

								log("  🚀 Injecting native hardware KeyEvents directly into focus stream...");
								for (char ch : resolvedValue.toCharArray()) {
									io.appium.java_client.android.nativekey.AndroidKey targetKey;

									switch (ch) {
										case '0': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_0; break;
										case '1': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_1; break;
										case '2': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_2; break;
										case '3': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_3; break;
										case '4': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_4; break;
										case '5': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_5; break;
										case '6': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_6; break;
										case '7': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_7; break;
										case '8': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_8; break;
										case '9': targetKey = io.appium.java_client.android.nativekey.AndroidKey.DIGIT_9; break;
										default: continue;
									}

									log("    → Sending Native Key Stream Event: " + targetKey.name());
									androidDriver.pressKey(new io.appium.java_client.android.nativekey.KeyEvent(targetKey));

									// Smooth focus advancement delay
									try { Thread.sleep(350); } catch (InterruptedException e) {}
								}

								log("  🏁 All digits sent. Letting the application process the auto-submit hook...");
								try { Thread.sleep(3000); } catch (InterruptedException e) {}

							} else {
								// Standard text input execution (Runs instantly for phone numbers, checkboxes, names) using resolvedValue
								log("  → Focusing standard element via default click interaction...");
								mobileElement.click();
								try { Thread.sleep(200); } catch (InterruptedException e) {}

								try {
									mobileElement.clear();
								} catch (Exception e) {
									log("  ⚠️ Notice: Clear unsupported on standard view structure.");
								}
								mobileElement.sendKeys(resolvedValue);
							}

							break;

						} catch (org.openqa.selenium.StaleElementReferenceException e) {
							log("  ⚠️ Caught Stale Element! Forcing framework to re-locate element on DOM...");
							attempts++;
							if (attempts == 2) throw e;
						}
					}

					try { mobileActions.hideKeyboard(); } catch (Exception e) {}
				} else {
					inputActions.typeText(xpath, resolvedValue);
				}

				log("  ✓ Text entered successfully");
				break;

			case "clear":
				log("  → XPath/Locator: " + xpath);
				if (driver instanceof io.appium.java_client.AppiumDriver) {
					org.openqa.selenium.By activeMobileLocator = getDynamicLocator(xpath);
					wait.until(ExpectedConditions.presenceOfElementLocated(activeMobileLocator)).clear();
				} else {
					inputActions.clearField(xpath);
				}
				log("  ✓ Field cleared");
				break;

			case "arrow_down":
				log("  → Locator Target: " + xpath);
				// UPDATED: Dynamic lookup support for modern selectors
				driver.findElement(getDynamicLocator(xpath)).sendKeys(org.openqa.selenium.Keys.ARROW_DOWN);
				log("  ✓ Sent Key: ARROW_DOWN");
				break;

			case "arrow_up":
				log("  → Locator Target: " + xpath);
				// UPDATED: Dynamic lookup support for modern selectors
				driver.findElement(getDynamicLocator(xpath)).sendKeys(org.openqa.selenium.Keys.ARROW_UP);
				log("  ✓ Sent Key: ARROW_UP");
				break;

			case "press_enter":
				log("  → Locator Target: " + xpath);
				// UPDATED: Dynamic lookup support for modern selectors
				driver.findElement(getDynamicLocator(xpath)).sendKeys(org.openqa.selenium.Keys.ENTER);
				log("  ✓ Sent Key: ENTER");
				break;

			case "tab":
				log("  → Starting Locator Target: " + xpath);
				// 1. Find the starting point dynamically
				WebElement currentElement = driver.findElement(getDynamicLocator(xpath));

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
				log("  → Local PC File Path: " + value);
				log("  → XPath/Locator: " + xpath);

				if (driver instanceof io.appium.java_client.AppiumDriver) {
					log("📱 Initiating Mobile Native Upload Flow...");

					java.io.File localFile = new java.io.File(value);
					String fileName = localFile.getName();

					String remotePath = "/sdcard/Download/" + fileName;
					log("  → Dynamic Emulator Target Path: " + remotePath);

					// 1. Push the exact file from your Excel sheet to the device
					mobileActions.pushFileToDevice(value, remotePath);

					// 2. Open the main upload trigger dialog using standard wait
					// UPDATED: Dynamic lookup support for modern selectors
					org.openqa.selenium.By activeUploadLocator = getDynamicLocator(xpath);
					WebElement uploadBtn = wait.until(ExpectedConditions.elementToBeClickable(activeUploadLocator));
					uploadBtn.click();
					Thread.sleep(2000); // Short stabilization wait instead of heavy 3.5s page source loop

					// 3. Handle the Bottom Sheet: Direct raw click on GALLERY
					String galleryButtonXpath = "//*[@content-desc='GALLERY'] | //*[contains(@text,'GALLERY')]";
					WebElement galleryBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(galleryButtonXpath)));
					galleryBtn.click();
					Thread.sleep(3000); // Allow Android System File Picker UI to cleanly establish foreground window

					// 4. Handle System Permission if it pops up
					try {
						String permissionBtn = "//android.widget.Button[@resource-id='com.android.permissioncontroller:id/permission_allow_button']";
						driver.findElement(By.xpath(permissionBtn)).click();
					} catch (Exception e) {
						log("ℹ️ Permission dialog did not appear, proceeding...");
					}

					// 5. Navigate the native Android System Picker to select your image
					log("⏳ Selecting the pushed image from system downloads...");

					// Target the element natively to prevent triggering the 4-attempt self-healing retry logic here
					String firstPhotoInGrid = "//android.widget.ImageView[1] | //android.view.ViewGroup[contains(@content-desc,'Photo taken')][1]";
					WebElement nativePhoto = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(firstPhotoInGrid)));
					nativePhoto.click();
					log("  ✓ Native touch coordinate dispatched to target photo asset.");

				} else {
					// WEB LOGIC: Original FileActions
					fileActions.uploadFile(value, xpath);
				}
				log("  ✓ File upload action completed");
				break;

			case "robotupload":
				if (driver instanceof io.appium.java_client.AppiumDriver) {
					log("  ❌ Critical: 'robotupload' is not supported on Mobile Devices.");
					throw new RuntimeException("RobotUpload is a Windows-only feature and cannot be used on Mobile.");
				}

				log("  → File: " + value);
				if (xpath != null && !xpath.isEmpty()) {
					log("  → Clicking upload button: " + xpath);
					// UPDATED: Dynamic lookup support for modern selectors
					driver.findElement(getDynamicLocator(xpath)).click();
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
					// UPDATED: Leverages your framework's visible wait via dynamic strategy mapping
					org.openqa.selenium.By targetUploadLocator = getDynamicLocator(xpath);
					wait.until(ExpectedConditions.visibilityOfElementLocated(targetUploadLocator));
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
					// UPDATED: Dynamic lookup support for modern selectors
					shortWait.until(ExpectedConditions.presenceOfElementLocated(getDynamicLocator(xpath)));
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
					// UPDATED: Dynamic lookup support for modern selectors
					List<WebElement> elements = driver.findElements(getDynamicLocator(xpath));
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
					log("  → Locator/XPath: " + xpath);
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
				log("  → Locator/XPath: " + xpath);
				toastActions.verifyToastMessage(value, xpath);
				log("  ✓ Alert verified");
				break;

			case "waitfortoast":
				log("  → Locator/XPath: " + xpath);
				toastActions.waitForToastToAppearAndDisappear(xpath);
				log("  ✓ Toast lifecycle complete");
				break;

			case "verify":
			case "verifyvisible":
			case "verifydisplayed":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementVisible(xpath);
				log("  ✓ Element visible");
				break;

			case "verifytext":
				log("  → Locator/XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValueOrText(xpath, value);
				log("  ✓ Text verified");
				break;

			case "verifyvalue":
				log("  → Locator/XPath: " + xpath);
				log("  → Expected: " + value);
				verificationActions.verifyElementValue(xpath, value);
				log("  ✓ Value verified");
				break;

			case "drawpolygon":
				log("  → Canvas Locator/XPath: " + xpath);
				log("  → Points: " + value);
				drawPolygon(xpath, value);
				log("  ✓ Polygon drawn");
				break;

			case "verifydate":
				log("  → Locator/XPath: " + xpath);
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
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyDateFieldIsToday(xpath);
				log("  ✓ Date is today");
				break;

			case "verifyenabled":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementEnabled(xpath);
				log("  ✓ Element enabled");
				break;

			case "verifydisabled":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementDisabled(xpath);
				log("  ✓ Element disabled");
				break;

			case "verifyselected":
			case "verifychecked":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementSelected(xpath);
				log("  ✓ Element selected");
				break;

			case "verifyexists":
			case "verifypresent":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementExists(xpath);
				log("  ✓ Element exists");
				break;

			case "verifyhidden":
			case "verifynotvisible":
				log("  → Locator/XPath: " + xpath);
				verificationActions.verifyElementNotVisible(xpath);
				log("  ✓ Element hidden");
				break;

			case "verifycontains":
				log("  → Locator/XPath: " + xpath);
				log("  → Expected to contain: " + value);
				verificationActions.verifyElementContainsText(xpath, value);
				log("  ✓ Text contains expected");
				break;

			case "verifycount":
				int count = Integer.parseInt(value);
				log("  → Locator/XPath: " + xpath);
				log("  → Expected count: " + count);
				verificationActions.verifyElementCount(xpath, count);
				log("  ✓ Count verified");
				break;

			case "verifyattribute":
				String[] parts = value.split("=", 2);
				log("  → Locator/XPath: " + xpath);
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
				log("  → Map Locator/XPath: " + xpath);
				verificationActions.verifyMapShapePresent(xpath);
				log("  ✓ Map shape verified");
				break;

			case "verifygridvalue":
				log("  → Grid Locator/XPath: " + xpath);
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
					log("  → Waiting for visibility of locator: " + xpath);

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
				log("  → Locator/XPath: " + xpath);
				waitActions.waitForElementVisible(xpath);
				log("  ✓ Element is visible");
				break;

			case "waitforclickable":
			case "wait for clickable":
				log("  → Locator/XPath: " + xpath);
				waitActions.waitForElementClickable(xpath);
				log("  ✓ Element is clickable");
				break;

			case "wait_until_visible":
			case "wait_visible":
				log("⏳ Initiating Explicit Structural Checkpoint for Locator Target: " + xpath);
				try {
					// Create a dynamic wait bounding handle (Max 60 seconds)
					WebDriverWait structuralCheck = new WebDriverWait(driver, Duration.ofSeconds(90));

					// UPDATED: Dynamically resolves your native selectors (accessibility, id, automator, xpath)
					org.openqa.selenium.By dynamicWaitLocator = getDynamicLocator(xpath);
					structuralCheck.until(ExpectedConditions.visibilityOfElementLocated(dynamicWaitLocator));
					log("✅ Structure fully rendered! Proceeding to next automated action path.");
				} catch (Exception e) {
					log("❌ Structural Checkpoint Failed! Next page container did not load within 60 seconds.");
					throw new RuntimeException("Page load timeout on locator target: " + xpath, e);
				}
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

			case "swipe":
			case "scroll_mobile":
				// Read the parameter value from your spreadsheet row (e.g., "up" or "down")
				String swipeDirection = (value != null && !value.isEmpty()) ? value.trim().toLowerCase() : "up";

				log("  → Initiating Mobile Swipe Gesture Direction: " + swipeDirection);
				mobileActions.swipe(swipeDirection);
				log("  ✓ Swipe sequence completed successfully");
				break;

			case "hide_keyboard":
				log("  → Hiding Mobile Keyboard");
				mobileActions.hideKeyboard();
				log("  ✓ Keyboard hidden");
				break;

			case "tap":
				// Changed logs to say Locator instead of XPath to match your strategy!
				System.out.println("  → Tapping Mobile Locator Target: " + xpath);

				try {
					// 1. Locate the element using your dynamic strategy (which handles accessibility id)
					org.openqa.selenium.By dynamicTapLocator = getDynamicLocator(xpath);
					WebElement element = wait.until(ExpectedConditions.elementToBeClickable(dynamicTapLocator));

					// 2. Direct click on the mobile element
					element.click();

					// 3. Give the app 2 seconds to transition without checking heavy layouts
					Thread.sleep(2000);
					System.out.println("  ✓ Step Completed Successfully");

				} catch (org.openqa.selenium.StaleElementReferenceException staleEx) {
					// If it goes stale instantly, it means the page moved right after clicking. Pass safely!
					System.out.println("  ✓ Element went stale post-click. Transition successful.");
				} catch (Exception e) {
					System.out.println("  ❌ Tap action failed: " + e.getMessage());
					throw new RuntimeException("Failed to execute tap on locator: " + xpath, e);
				}
				break;

			case "tap_coordinate":
				// Expects value format from Excel: "978:2224"
				if (value == null || !value.contains(":")) {
					log("  ❌ Error: Invalid coordinate format in Excel. Expected 'X:Y' (e.g., 978:2224)");
					throw new IllegalArgumentException("Invalid coordinate format: " + value);
				}

				String[] coordinates = value.split(":");
				int absoluteX = Integer.parseInt(coordinates[0].trim());
				int absoluteY = Integer.parseInt(coordinates[1].trim());

				log("  → Tapping Absolute Screen Coordinates: (" + absoluteX + ", " + absoluteY + ")");

				try {
					// Correct, compliant absolute coordinate tap
					new Actions(driver)
							.moveToLocation(absoluteX, absoluteY) // Targets exact physical pixels from top-left (0,0)
							.click()
							.perform();

					log("  ✓ Tapped Absolute Coordinates Successfully");
				} catch (Exception e) {
					log("  ❌ Failed to execute coordinate tap: " + e.getMessage());
					throw e;
				}
				break;

			case "reload_app":
				log("🔄 Custom Keyword Triggered: [reload_app] for target role: [" + value.toUpperCase() + "]");

				// 1. Verify if the driver exists in our universal pool
				if (getDriverPool() != null && getDriverPool().containsKey(value)) {
					org.openqa.selenium.WebDriver mobileDriverInstance = getDriverPool().get(value);

					if (mobileDriverInstance instanceof io.appium.java_client.android.AndroidDriver) {
						io.appium.java_client.android.AndroidDriver androidDriver = (io.appium.java_client.android.AndroidDriver) mobileDriverInstance;

						// 2. Fetch the unique package name using the target configuration property key
						String appPackage = config.getProperty(value + ".app.package");

						if (appPackage != null && !appPackage.isEmpty()) {
							try {
								log("   • Closing process: " + appPackage);
								androidDriver.terminateApp(appPackage);

								log("   • Clearing all storage caches & user sessions...");
								androidDriver.executeScript("mobile: clearApp", java.util.Map.of("appId", appPackage));

								log("   • Launching completely clean slate app instance...");
								androidDriver.activateApp(appPackage);

								// 3. Keep the active executor pointer context focused on the reloaded app
								switchSession(value);
								log("✓ App [" + value.toUpperCase() + "] successfully reloaded to its onboarding screens!");

							} catch (Exception e) {
								log("❌ Error executing reload_app keyword: " + e.getMessage());
								throw new RuntimeException(e);
							}
						} else {
							log("❌ Error: Missing property mapping configuration for key: '" + value + ".app.package'");
						}
					} else {
						log("❌ Error: Target session [" + value + "] is not an active Appium AndroidDriver instance.");
					}
				} else {
					log("❌ Error: Cannot reload app. No active session found in driver pool for role: [" + value + "]");
				}
				break;

			case "set_location":
				try {
					// 🚀 UPDATED: Splitting by semicolon (;) to handle your single text string format smoothly
					String[] geoPoints = step.getValue().split(";");
					double latitude = Double.parseDouble(geoPoints[0].trim());
					double longitude = Double.parseDouble(geoPoints[1].trim());

					if (this.driver instanceof io.appium.java_client.android.AndroidDriver) {
						System.out.println("  📍 Injecting GPS Coordinates: " + latitude + ", " + longitude);

						// Using standard instantiation for stable data alignment
						io.appium.java_client.android.geolocation.AndroidGeoLocation geo =
								new io.appium.java_client.android.geolocation.AndroidGeoLocation();

						// Map inputs containing the geometric coordinates
						java.util.Map<String, Object> coordinatesMap = new java.util.HashMap<>();
						coordinatesMap.put("latitude", latitude);
						coordinatesMap.put("longitude", longitude);
						coordinatesMap.put("altitude", 0.0);

						// 🚀 FIX FOR CURRENT DRIVER: Using "mobile: setGeolocation" to match your active Appium package version
						((io.appium.java_client.android.AndroidDriver) this.driver).executeScript("mobile: setGeolocation", coordinatesMap);

						// ⏳ Stabilization pause to prevent the Android System UI from crashing
						System.out.println("  ⏳ Pausing to allow Android System UI to process coordinates safely...");
						Thread.sleep(2000);
					}
				} catch (Exception e) {
					System.out.println("  ❌ Failed to set emulator GPS location: " + e.getMessage());
				}
				break;

			default:
				throw new RuntimeException("Unknown action: " + action);
		}
	}

	private String generateXPathFromValue(String value, String context) {
		// If we are on Mobile, do not use complex Web XPaths
		if (driver instanceof io.appium.java_client.AppiumDriver) {
			return value; // Return raw value to be used as ID/Accessibility ID/Prefixed Selector
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

		// UPDATED: Dynamic lookup support for modern web selectors
		org.openqa.selenium.By canvasLocator = getDynamicLocator(xpath);
		WebElement map = wait.until(ExpectedConditions.presenceOfElementLocated(canvasLocator));

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
			// UPDATED: Changed label from "XPath:" to "Locator:" to accommodate multi-locator mobile strategies
			log("│ Locator: " + xpath + " ".repeat(Math.max(1, 71 - xpath.length())) + "│");
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

			// --- DYNAMIC ROLE DETECTION ---
			String roleName = "UNKNOWN";

			// Loop through the pool to find which role name matches the current active driver
			for (java.util.Map.Entry<String, WebDriver> entry : driverPool.entrySet()) {
				if (entry.getValue().equals(this.driver)) {
					roleName = entry.getKey().toUpperCase();
					break;
				}
			}

			// Pick the icon based on whether the active driver is Mobile (Appium) or Web
			String icon = (this.driver instanceof io.appium.java_client.AppiumDriver) ? "📱 " : "💻 ";

			// --- HIGHLIGHTING UPDATE ---
			// Wraps the role (USER/DRIVER/WEB) in a bright Cyan badge to make it stand out
			String highlightedRole = "<span style='background:#00d4ff; color:#000; padding:2px 8px; border-radius:4px; font-weight:900; margin-right:5px; box-shadow: 0 0 5px rgba(0,212,255,0.5);'>" + roleName + "</span>";

			String platformLabel = icon + highlightedRole + ": ";

			// --- UPDATED LOCATOR CLEANER FOR DASHBOARD ---
			String rawDetail = "";
			if (step.getValue() != null && !step.getValue().isEmpty()) {
				rawDetail = step.getValue();
			} else if (step.getXpath() != null && !step.getXpath().isEmpty()) {
				String rawXpath = step.getXpath();
				// Clean up technical prefixes for cleaner dashboard display visibility
				if (rawXpath.startsWith("accessibility=")) {
					rawDetail = "Accessibility ID: " + rawXpath.replace("accessibility=", "");
				} else if (rawXpath.startsWith("id=")) {
					rawDetail = "Resource ID: " + rawXpath.replace("id=", "");
				} else if (rawXpath.startsWith("automator=")) {
					rawDetail = "UIAutomator Engine";
				} else {
					rawDetail = rawXpath;
				}
			}

			// Combine the role label with the step details
			String detail = platformLabel + rawDetail;

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
	/**
	 * Initializes a standard Web (Chrome) session and adds it to the pool.
	 */


	public Map<String, WebDriver> getDriverPool() {
		return this.driverPool;
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
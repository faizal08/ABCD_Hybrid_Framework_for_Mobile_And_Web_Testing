package com.eit.automation.core;

import com.eit.automation.pages.LoginPage;
import com.eit.automation.parser.StepParser;
import com.eit.automation.parser.TestStep;
import com.eit.automation.utils.CSVTestCaseReader;
import com.eit.automation.utils.ReportGenerator;
import com.eit.automation.utils.VideoRecorder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import java.net.URL;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.time.Duration;

public class Main {
    public static Properties config;
    public static TestExecutor executor;
    private static VideoRecorder videoRecorder;

    // Track sheets already completed to avoid infinite loops and double runs
    private static Set<String> executedSheets = new HashSet<>();

    // --- NEW: UNIVERSAL SESSION TRACKING ---
// Tracks which sessions from the DriverPool have been initialized
    public static Set<String> activeSessions = new HashSet<>();
    // Specifically tracks if the Web Admin login is done
    private static boolean isWebLoggedIn = false;

    static {
        try {
            videoRecorder = new VideoRecorder();
        } catch (Exception e) {
            System.err.println("❌ Critical: Failed to initialize Video Recorder: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ReportGenerator reportGenerator = new ReportGenerator();

        try {
            System.out.println("=== 🚀 EIT Universal Test Automation Started (Web + Mobile) ===\n");

            // --- IMPROVED DYNAMIC CONFIG LOADING ---
            config = new Properties();
            String env = System.getProperty("env");

            // System property check for environment-specific configs
            String configFileName = (env != null && !env.isEmpty()) ? env + ".properties" : "config.properties";
            File configFile = new File(configFileName);

            if (!configFile.exists()) {
                System.out.println("⚠️  Config '" + configFileName + "' not found. Falling back to 'config.properties'.");
                configFileName = "config.properties";
                configFile = new File(configFileName);
            }

            System.out.println("🔧 Loading Configuration: " + configFile.getAbsolutePath());
            try (FileInputStream configFis = new FileInputStream(configFile)) {
                config.load(configFis);
            }

            // Validate that we actually loaded data
            String testFilePath = config.getProperty("excel.name");
            if (testFilePath == null || testFilePath.isEmpty()) {
                throw new RuntimeException("❌ Error: 'excel.name' is missing or empty in " + configFileName);
            }
            // --- END CONFIG LOADING ---

            reportGenerator.setExcelFileName(testFilePath);
            reportGenerator.startTestExecution();

            System.out.println("📂 Reading test cases from: " + testFilePath);
            File testFile = new File(testFilePath);
            if (!testFile.exists()) {
                throw new RuntimeException("Test file not found at: " + testFile.getAbsolutePath());
            }

            // Initialize the Universal TestExecutor (It will manage Web, User App, and Driver App)
            executor = new TestExecutor(reportGenerator, config);

            // Detect file type and read accordingly
            if (testFilePath.toLowerCase().endsWith(".csv")) {
                System.out.println("📄 Detected CSV file format");
                readCSVTestCases(testFilePath, executor, reportGenerator);
            } else if (testFilePath.toLowerCase().endsWith(".xlsx") || testFilePath.toLowerCase().endsWith(".xls")) {
                System.out.println("📊 Detected Excel file format");
                readExcelTestCases(testFilePath, reportGenerator);
            } else {
                throw new RuntimeException("Unsupported file format. Please use .csv, .xlsx, or .xls files.");
            }

            reportGenerator.endTestExecution();

        } catch (Exception e) {
            System.err.println("❌ Execution failed: " + e.getMessage());
            e.printStackTrace();
            reportGenerator.endTestExecution();
        } finally {
            if (executor != null) {
                // Updated log message because we are closing Browsers AND Emulators
                System.out.println("🛑 Terminating all active automation sessions...");
                executor.close();
            }
        }
    }
    /**
     * Read test cases from Excel file - UPDATED FOR HYBRID WEB/MOBILE LOOP CONFIGURATIONS [X]
     */
    private static void readExcelTestCases(String excelPath, ReportGenerator reportGenerator) throws Exception {
        String sheetNameConfig = config.getProperty("sheets.name");
        String[] rawSheetTokens = (sheetNameConfig != null) ? sheetNameConfig.split(",") : new String[0];

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Executor is already initialized in main(), but we ensure safety here
            if (executor == null) {
                executor = new TestExecutor(reportGenerator, config);
            }

            for (String token : rawSheetTokens) {
                String cleanToken = token.trim();
                if (cleanToken.isEmpty()) continue;

                String sheetName = cleanToken;
                int repeatCount = 1; // Default to 1 if no bracket configuration is provided

                // Extract brackets pattern: e.g., "AddCustomer[50]" or "MobileProfileUpdate[5]"
                if (cleanToken.contains("[") && cleanToken.endsWith("]")) {
                    try {
                        sheetName = cleanToken.substring(0, cleanToken.indexOf("[")).trim();
                        String countStr = cleanToken.substring(cleanToken.indexOf("[") + 1, cleanToken.length() - 1).trim();
                        repeatCount = Integer.parseInt(countStr);
                    } catch (Exception e) {
                        System.err.println("⚠️ Warning: Failed to parse iteration count for token '" + cleanToken + "'. Falling back to 1 loop.");
                        sheetName = cleanToken.replace("[", "").replace("]", "").trim();
                        repeatCount = 1;
                    }
                }

                // Run the target hybrid sheet the exact number of times requested
                System.out.println("\n🎯 Target Configuration Set: Sheet [" + sheetName + "] will iterate " + repeatCount + " time(s).");
                for (int currentRun = 1; currentRun <= repeatCount; currentRun++) {
                    System.out.println("\n========================================================");
                    System.out.println("🔄 HYBRID STRESS LOOP ATTEMPT #" + currentRun + " OF " + repeatCount + " FOR SHEET: [" + sheetName + "]");
                    System.out.println("========================================================");

                    // Force-remove the target sheet from execution tracking for this iteration pass
                    // so it bypasses the recursion lock safely
                    executedSheets.remove(sheetName);

                    runSheetWithPrecondition(sheetName, workbook, reportGenerator);
                }
            }
        }
    }

    /**
     * Logic to handle the Precondition column dependency recursively.
     * Preserves hybrid loop pipelines while cleanly checking structural data setups.
     */
    private static void runSheetWithPrecondition(String sheetName, Workbook workbook, ReportGenerator reportGenerator) throws Exception {
        // If it's a structural background dependency sheet that ran already, do not re-run it
        if (executedSheets.contains(sheetName)) return;

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            System.err.println("⚠️ Warning: Sheet '" + sheetName + "' not found!");
            return;
        }

        // --- MULTI-PRECONDITION SEARCH ---
        Row firstRow = sheet.getRow(1);
        if (firstRow != null) {
            Cell preconditionCell = firstRow.getCell(5);
            if (preconditionCell != null) {
                String fullText = preconditionCell.getStringCellValue();

                if (fullText.contains("RunSheet:")) {
                    String[] parts = fullText.split("RunSheet:");
                    for (int j = 1; j < parts.length; j++) {
                        String rawNames = parts[j].split("\\n|\\r")[0].trim();
                        String[] dependencies = rawNames.split(",");

                        for (String dep : dependencies) {
                            String dependencySheet = dep.trim().split("\\s+")[0].replace(".", "");
                            if (!dependencySheet.isEmpty() && !executedSheets.contains(dependencySheet)) {
                                System.out.println("🔗 Multi-Dependency Found: [" + dependencySheet + "]");
                                runSheetWithPrecondition(dependencySheet, workbook, reportGenerator);
                            }
                        }
                    }
                }
            }
        }

        processSheetData(sheet, sheetName, reportGenerator);

        // Add to tracking list to prevent background dependency duplication loops
        executedSheets.add(sheetName);
    }

    /**
     * The actual loop that runs the test cases in the sheet.
     * UPDATED: Integrated with Smart Session Management and Multi-Iteration loop routines.
     */
    private static void processSheetData(Sheet sheet, String sheetName, ReportGenerator reportGenerator) {
        System.out.println("\n📖 Processing Sheet: [" + sheetName + "]");

        if (executor != null) {
            executor.setCleanupMode(sheetName.equalsIgnoreCase("DataCleanUpSheet"));
        }

        // Loop through rows in the Excel sheet
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell testCaseCell = row.getCell(2);
            Cell stepBlockCell = row.getCell(4);
            if (testCaseCell == null || stepBlockCell == null) continue;

            String testCaseName = testCaseCell.getStringCellValue().trim();
            String stepBlock = stepBlockCell.getStringCellValue().trim();

            // 1. Filter Check
            String filterName = config.getProperty("filter.name");
            if (filterName != null && !testCaseName.toLowerCase().contains(filterName.toLowerCase().trim())) {
                continue;
            }

            String videoFileName = testCaseName.replaceAll("[^a-zA-Z0-9]", "_") + ".mp4";

            try {
                // 2. Video Start (Null safe check)
                if (videoRecorder != null) {
                    System.out.println("🎥 Starting Video Recording: " + videoFileName);
                    videoRecorder.startRecording(reportGenerator.getReportDir(), videoFileName);
                }

                // 3. SMART SESSION INITIALIZATION WITH ITERATION STATE CONTROLS
                // Logic: Perform login if we aren't logged in yet,
                // EXCEPT if the test case is only for mobile (doesn't use the Web Admin at all)
                boolean isPurelyMobile = stepBlock.toLowerCase().startsWith("switch_to") &&
                        !stepBlock.toLowerCase().contains("switch_to: web");

                if (!isWebLoggedIn && !isPurelyMobile) {
                    performInitialLogin(executor);
                    isWebLoggedIn = true;
                } else if (isWebLoggedIn && executor.getDriverPool() != null && executor.getDriverPool().containsKey("web")) {
                    // If already logged in and using web, navigate back to dashboard to securely reset state for next loop pass
                    String dashboardUrl = config.getProperty("dashboard.url");
                    if (dashboardUrl != null && !dashboardUrl.isEmpty()) {
                        executor.getDriverPool().get("web").get(dashboardUrl);
                    }
                    try { Thread.sleep(1500); } catch (Exception ignored) {}
                }

                // 4. Run the actual test steps (Web or Mobile dynamically via the step parser)
                executeTestCase(sheetName, testCaseName, stepBlock, executor, reportGenerator);

            } catch (Exception e) {
                System.err.println("❌ Error in " + testCaseName + ": " + e.getMessage());
                e.printStackTrace(); // Retained for deep debugging
            } finally {
                // 5. Cleanup Video for this specific test case
                try {
                    if (videoRecorder != null) {
                        videoRecorder.stopRecording();
                        reportGenerator.addVideoToTestCase(videoFileName);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
    /**
     * Read test cases from CSV file
     */
    private static void readCSVTestCases(String csvPath, TestExecutor executor, ReportGenerator reportGenerator)
            throws Exception {
        List<CSVTestCaseReader.TestCaseData> testCases = CSVTestCaseReader.readTestCases(csvPath);

        System.out.println("✓ Found " + testCases.size() + " test case(s) in CSV file");

        for (CSVTestCaseReader.TestCaseData testCase : testCases) {
            String testCaseName = testCase.getTestCaseName();
            String stepBlock = testCase.getStepBlock();

            // Check for filter
            String filterName = config.getProperty("filter.name");
            boolean match = (filterName == null || filterName.isEmpty());

            if (!match) {
                String[] filters = filterName.split(",");
                for (String f : filters) {
                    if (testCaseName.toLowerCase().contains(f.trim().toLowerCase())) {
                        match = true;
                        break;
                    }
                }
            }

            if (!match) continue;

            // --- NEW: UNIVERSAL INTEGRATION FOR CSV ---
            String videoFileName = testCaseName.replaceAll("[^a-zA-Z0-9]", "_") + "_CSV.mp4";

            try {
                // Start recording the desktop (Captures Browser + Emulators)
                System.out.println("🎥 [CSV] Starting Video Recording: " + videoFileName);
                videoRecorder.startRecording(reportGenerator.getReportDir(), videoFileName);

                // Smart Session: Login only if this isn't a mobile-start test
                if (!isWebLoggedIn && !stepBlock.toLowerCase().contains("switch_to")) {
                    performInitialLogin(executor);
                    isWebLoggedIn = true;
                }

                executeTestCase("CSV_Data", testCaseName, stepBlock, executor, reportGenerator);

            } catch (Exception e) {
                System.err.println("❌ CSV Execution Error in " + testCaseName + ": " + e.getMessage());
            } finally {
                try {
                    videoRecorder.stopRecording();
                    reportGenerator.addVideoToTestCase(videoFileName);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Execute a single test case
     */
    private static void executeTestCase(String sheetName, String testCaseName, String stepBlock, TestExecutor executor, ReportGenerator reportGenerator) {
        List<TestStep> steps = StepParser.parseSteps(stepBlock);
        if (steps.isEmpty()) return;

        // --- 1. DYNAMIC STEP INITIALIZATION SCAN (LOOK-AHEAD) ---
        for (TestStep step : steps) {
            String action = step.getAction().toLowerCase().trim();
            // Clean the role name immediately
            String role = (step.getValue() != null) ? step.getValue().toLowerCase().trim() : "";

            if ((action.equals("switch_to") || action.equals("switchsession")) && !role.isEmpty()) {
                // Use the executor's pool as the ONLY source of truth
                if (!executor.getDriverPool().containsKey(role)) {
                    System.out.println("🚀 Universal Switch: Initializing [" + role.toUpperCase() + "]");
                    if (role.equals("web")) {
                        executor.setupWebDriver();
                    } else {
                        executor.setupMobileDriver(role);
                    }
                }
            }
        }

        // --- 2. SET CORRECT INITIAL RUNTIME FOCUS ---
        String firstAction = steps.get(0).getAction().toLowerCase().trim();
        String firstValue = (steps.get(0).getValue() != null) ? steps.get(0).getValue().toLowerCase().trim() : "";

        if ((firstAction.equals("switch_to") || firstAction.equals("switchsession")) && !firstValue.isEmpty()) {
            // 🚀 CRITICAL FIX: Explicitly shift active pointer context to the designated starting platform context
            System.out.println("🎯 Setting Initial Test Focus context directly to: [" + firstValue.toUpperCase() + "]");
            executor.switchSession(firstValue);
        } else {
            // If the first step is a regular interaction keyword, fall back safely to 'web'
            System.out.println("🌐 No initial switch step found. Setting default session context focus to: [WEB]");
            executor.switchSession("web");
        }

        // --- 3. HAND OFF TO EXECUTOR CORE RUNNER ---
        System.out.println("🚀 Handing over synchronized session execution block to TestExecutor engine...");
        executor.run(sheetName, steps, testCaseName);
    }
    /**
     * Perform initial login before test execution
     */
    private static void performInitialLogin(TestExecutor executor) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        PERFORMING INITIAL LOGIN (WEB)                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");

        try {
            // --- STEP 1: ENSURE WEB SESSION IS INITIALIZED ---
            if (!executor.getDriverPool().containsKey("web")) {
                System.out.println("🌐 Initializing Web Driver for Admin Login...");
                executor.setupWebDriver(); // This starts Chrome and adds it to the pool
                activeSessions.add("web");
            }

            // --- STEP 2: SWITCH TO WEB CONTEXT ---
            // We force the executor to focus on 'web' to ensure login happens in the browser
            executor.switchSession("web");

            String url = config.getProperty("base.url");
            String username = config.getProperty("admin.email");
            String password = config.getProperty("admin.password");

            System.out.println("→ Navigating to Admin Portal: " + url);
            System.out.println("→ Credentials: " + username);

            if (url != null && !url.isEmpty()) {
                // Using executor.getDriver() now safely returns the Chrome instance
                executor.getDriver().get(url);

                LoginPage loginPage = new LoginPage(executor.getDriver(), executor.getWait());
                loginPage.login(username, password);

                System.out.println("✓ Web Admin login successful.");
            }
        } catch (Exception e) {
            System.err.println("❌ Initial login failed: " + e.getMessage());
            // If login fails, we shouldn't proceed with other web-dependent tests
            isWebLoggedIn = false;
            e.printStackTrace();
        }
    }
}
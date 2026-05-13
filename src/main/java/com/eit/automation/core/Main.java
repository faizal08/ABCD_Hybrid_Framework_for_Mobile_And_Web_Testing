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
     * Read test cases from Excel file - UPDATED TO HANDLE MULTIPLE SHEETS CORRECTLY
     */
    /**
     * Read test cases from Excel file
     */
    private static void readExcelTestCases(String excelPath, ReportGenerator reportGenerator) throws Exception {
        String sheetNameConfig = config.getProperty("sheets.name");
        String[] sheetNames = (sheetNameConfig != null) ? sheetNameConfig.split(",") : new String[0];

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Executor is already initialized in main(), but we ensure safety here
            if (executor == null) {
                executor = new TestExecutor(reportGenerator, config);
            }

            for (String rawSheetName : sheetNames) {
                runSheetWithPrecondition(rawSheetName.trim(), workbook, reportGenerator);
            }
        }
    }

    /**
     * Logic to handle the Precondition column dependency recursively.
     */
    private static void runSheetWithPrecondition(String sheetName, Workbook workbook, ReportGenerator reportGenerator) throws Exception {
        if (executedSheets.contains(sheetName)) return;

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            System.err.println("⚠️ Warning: Sheet '" + sheetName + "' not found!");
            return;
        }

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
        executedSheets.add(sheetName);
    }

    /**
     * The actual loop that runs the test cases in the sheet.
     * UPDATED: Now supports Smart Session Management for Web and Mobile.
     */
    private static void processSheetData(Sheet sheet, String sheetName, ReportGenerator reportGenerator) {
        System.out.println("\n📖 Processing Sheet: [" + sheetName + "]");

        if (executor != null) {
            executor.setCleanupMode(sheetName.equalsIgnoreCase("DataCleanUpSheet"));
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell testCaseCell = row.getCell(2);
            Cell stepBlockCell = row.getCell(4);
            if (testCaseCell == null || stepBlockCell == null) continue;

            String testCaseName = testCaseCell.getStringCellValue().trim();
            String stepBlock = stepBlockCell.getStringCellValue().trim();

            String filterName = config.getProperty("filter.name");
            if (filterName != null && !testCaseName.toLowerCase().contains(filterName.toLowerCase().trim())) {
                continue;
            }

            String videoFileName = testCaseName.replaceAll("[^a-zA-Z0-9]", "_") + ".mp4";

            try {
                // Video recording starts for the whole desktop (Captures Browser + Emulators)
                System.out.println("🎥 Starting Video Recording: " + videoFileName);
                videoRecorder.startRecording(reportGenerator.getReportDir(), videoFileName);

                // --- SMART SESSION INITIALIZATION ---
                // We check if the test case is intended for Web or Mobile.
                // If it's a standard web test and not logged in yet, we perform login.
                if (!isWebLoggedIn && !stepBlock.toLowerCase().contains("switch_to")) {
                    performInitialLogin(executor);
                    isWebLoggedIn = true;
                } else if (isWebLoggedIn) {
                    // If already logged in, just refresh the dashboard to clean the state
                    String dashboardUrl = config.getProperty("dashboard.url");
                    if (dashboardUrl != null && !dashboardUrl.isEmpty() && activeSessions.contains("web")) {
                        executor.getDriverPool().get("web").get(dashboardUrl);
                    }
                }

                executeTestCase(sheetName, testCaseName, stepBlock, executor, reportGenerator);

            } catch (Exception e) {
                System.err.println("❌ Error in " + testCaseName + ": " + e.getMessage());
            } finally {
                try {
                    videoRecorder.stopRecording();
                    reportGenerator.addVideoToTestCase(videoFileName);
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
    private static void executeTestCase(String sheetName, String testCaseName, String stepBlock, TestExecutor executor,
                                        ReportGenerator reportGenerator) {
        System.out.println("\n=== 🧪 Running: " + testCaseName + " ===");

        // Parse steps
        List<TestStep> steps = StepParser.parseSteps(stepBlock);

        if (steps.isEmpty()) {
            System.err.println("❌ No valid steps parsed!");
            reportGenerator.startTestCase(testCaseName);
            reportGenerator.endTestCase(false);
            return;
        }

        // --- NEW: UNIVERSAL SESSION AUTO-STARTER ---
        // Look at the first few steps. If we see a 'switch_to' for a session
        // that isn't active yet, we initialize it right now.
        for (TestStep step : steps) {
            String action = step.getAction().toLowerCase();
            String role = (step.getValue() != null) ? step.getValue().toLowerCase() : "";

            if ((action.equals("switch_to") || action.equals("switchsession")) && !role.isEmpty()) {
                if (!activeSessions.contains(role)) {
                    System.out.println("🚀 Auto-Initializing required session: [" + role.toUpperCase() + "]");

                    if (role.equals("web")) {
                        executor.setupWebDriver();
                    } else {
                        // This calls your Mobile setup for 'user' or 'driver'
                        executor.setupMobileDriver(role);
                    }
                    activeSessions.add(role);
                }
            }
        }

        // Execute the steps using the updated TestExecutor
        // It will now handle the switching seamlessly.
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
            if (!activeSessions.contains("web")) {
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
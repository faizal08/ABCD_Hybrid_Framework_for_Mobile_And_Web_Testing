package com.eit.automation.core;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ScreenshotHelper {
    public static void capture(WebDriver driver, String filename) {
        try {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), new File(filename).toPath());
            System.out.println("📸 Screenshot saved to " + filename);
        } catch (IOException e) {
            System.out.println("⚠️ Failed to capture screenshot: " + e.getMessage());
        }
    }
}


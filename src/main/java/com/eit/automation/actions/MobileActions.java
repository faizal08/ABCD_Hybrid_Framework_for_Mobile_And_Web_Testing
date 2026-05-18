package com.eit.automation.actions;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.PerformsTouchActions;
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.appium.java_client.HidesKeyboard;
import java.time.Duration;

public class MobileActions {

    private AppiumDriver driver;
    private WebDriverWait wait;

    public MobileActions(AppiumDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /**
     * Standard click for Mobile (handles Accessibility IDs or XPaths)
     */
    public void tap(String locator) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(parseLocator(locator)));
        element.click();
    }

    /**
     * Swipe logic: Up, Down, Left, Right
     */
    /**
     * Swipe logic: Up, Down, Left, Right using modern W3C Actions API
     */
    public void swipe(String direction) {
        Dimension size = driver.manage().window().getSize();
        int startX, startY, endX, endY;

        // NOTE: To scroll text "down into view", your finger must drag "up" from bottom to top!
        switch (direction.toLowerCase()) {
            case "up":
                startX = size.width / 2;
                startY = (int) (size.height * 0.75);
                endX = size.width / 2;
                endY = (int) (size.height * 0.25);
                break;
            case "down":
                startX = size.width / 2;
                startY = (int) (size.height * 0.25);
                endX = size.width / 2;
                endY = (int) (size.height * 0.75);
                break;
            default:
                System.out.println("  ⚠️ Warning: Unsupported swipe direction requested: " + direction);
                return;
        }

        // Modern W3C Pointer Input Sequence
        org.openqa.selenium.interactions.PointerInput finger =
                new org.openqa.selenium.interactions.PointerInput(org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "swipeFinger");
        org.openqa.selenium.interactions.Sequence swipeSeq = new org.openqa.selenium.interactions.Sequence(finger, 1);

        swipeSeq.addAction(finger.createPointerMove(Duration.ZERO, org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, startY));
        swipeSeq.addAction(finger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
        swipeSeq.addAction(finger.createPointerMove(Duration.ofMillis(800), org.openqa.selenium.interactions.PointerInput.Origin.viewport(), endX, endY));
        swipeSeq.addAction(finger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));

        try {
            driver.perform(java.util.Collections.singletonList(swipeSeq));
            try { Thread.sleep(600); } catch (InterruptedException e) {} // Stabilize screen layout physics
        } catch (Exception e) {
            System.out.println("  ❌ Failed to execute swipe sequence: " + e.getMessage());
        }
    }
    /**
     * Hide keyboard - essential for mobile forms
     */
    public void hideKeyboard() {
        try {
            if (driver instanceof HidesKeyboard) {
                ((HidesKeyboard) driver).hideKeyboard();
            }
        } catch (Exception e) {
            // Ignore if keyboard is already hidden or not supported
        }
    }

    /**
     * Helper to distinguish between XPath and Mobile Accessibility IDs
     */
    private By parseLocator(String locator) {
        if (locator.startsWith("//") || locator.startsWith("(//")) {
            return By.xpath(locator);
        }
        // Default to Accessibility ID for mobile-friendly strings
        return By.id(locator);
    }
}

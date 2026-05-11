package com.eit.automation.core;
import java.io.*;
import java.util.*;

public class LocatorMapper {
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("locators.properties")) {
            props.load(fis);
        } catch (IOException e) {
            System.out.println("⚠️ Locator file not found.");
        }
    }

    public static String getXPath(String label) {
        return props.getProperty(label, "");
    }
}

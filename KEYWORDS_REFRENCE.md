# 📱💻 ABCD Hybrid Framework: Full Keywords Dictionary

This document is the official reference for writing automated test cases. The `StepParser` uses natural language processing (Regex) to match your Excel steps to automation actions.

## 📊 Quick Start: How to Write Test Cases in Excel

To ensure the framework parses your automation suites correctly, you must follow the strict formatting structure outlined below when designing your Excel sheets.

![Excel Screenshot](Excel_screenshot.png)

### I. The Core Columns (Where to Write)

* **Column E (5th Column) - Test Step Description:** This is the core execution column where the `StepParser` reads your test actions line-by-line.
* **Column F (6th Column) - Precondition & Data Dependency:** This column manages structural dependencies. If your current sheet relies on data or a state created by another sheet, use the **`RunSheet:`** keyword followed immediately by the target sheet name (e.g., `RunSheet: SuperAdminLogin`). The framework will temporarily pause execution of the current sheet, execute the target dependency sheet completely to set up the context, and then seamlessly return to continue running your active test case.

---

### II. Column E Formatting & The 3-Comma Syntax Rule

Every automated instruction written inside **Column E** must follow a strict **3-comma layout structure** separating 4 parameters:

---

<br>

<div align="center">
  <p>⚠️ <b>CRITICAL RULES FOR WRITING TEST STEPS</b> ⚠️</p>
  <table>
    <tr>
      <td align="center" style="background-color: #2d3748; padding: 20px; border: 3px solid #e53e3e; border-radius: 8px;">
        <span style="font-size: 26px; font-weight: bold; color: #f7fafc; letter-spacing: 1px; font-family: monospace;">
          Test Step Description , Action , Value , "XPath/Locator"
        </span>
      </td>
    </tr>
  </table>
  <p><i>Every automated instruction written inside <b>Column E</b> must follow this exact 3-comma layout.</i></p>
</div>

<br>

---

1. **Test Step Description:** A clean human-readable note describing what the step is doing for validation purposes.
2. **Action:** The explicit system action keyword that the test executor will execute (e.g., `click`, `type`, `wait`, `uploadfile`, `switch_to`).
3. **Value:** The parameters or inputs needed for the action (such as text values, numbers, dynamic variables, or system file paths).
4. **Locator / XPath:** The selector value or properties key, which **must always be enclosed in double quotes (`""`)**.

---
### 📋 Formatting Syntax Reference Examples

Review these real execution scenarios to understand how empty parameters and quote structures change based on the action type:

#### 1. Standard Field Input (Text Entry)
`1.Enter Email, type, super_admin@gmail.com, "web.global.login.email"`

#### 2. Click Action (No Value Parameter)
`2.Click Submit, click, , "web.global.action.submit"`
> 📌 **Note:** Since submit has no value, the value place is left empty, which is why there are double commas (,,) there.

#### 3. Static Timeout Wait (No Locator Parameter)
`3.wait for upload, wait, 3000, `
> 📌 **Note:** In this case, the XPath is not there, so that field is left empty.

#### 4. Document Upload Paths
`4.Upload Profile Image, uploadfile, "src/main/resources/test-data/customer.jpg", "admin.customer.upload_profile_img"`
> 📌 **Note:** For photo upload, the value file path should also be in double quotes.

#### 5. Context Switch (No Locator Parameter)
`5.Load Driver App, switch_to, driver, `
> 📌 **Note:** Here, for switching to the driver mobile app, there is no XPath, so leave it empty. Always agree to the 3-comma rule.
---

## 🚀 1. Hybrid Orchestration (Multi-Session)
These keywords are the "brain" of the hybrid framework. They allow you to switch control between your browser and multiple mobile devices in a single test script.

| Action | Phrase Examples | Description |
| :--- | :--- | :--- |
| **`switch_to`** | switch_to, focus, load | Moves the execution focus to a different platform. Values: **web**, **user**, **driver**. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath/ID) |
| :--- | :--- | :--- | :--- |
| Switch to User App | **switch_to** | **user** | - |
| Switch to Driver App | **switch_to** | **driver** | - |
| Switch back to Admin | **switch_to** | **web** | - |

---

## 🚀 2. Centralized Element Mapper (Zero-Maintenance Sheets)

To eliminate test maintenance fatigue when development teams update UI elements or application layouts, **never hardcode raw XPaths, IDs, or Automator expressions inside your Excel test sheets.** Instead, assign elements a standardized key name inside your centralized properties configuration. The framework's internal `LocatorMapper` engine automatically intercepts these keys at runtime, matches them to the real selectors, and handles execution seamlessly.

> **💡 The Maintenance Advantage:** If a developer changes an element's XPath or Resource ID next week, you only modify it **once** in your `.properties` file. You do **not** have to edit or re-upload dozens of different Excel regression sheets.

### 📋 Step 1: Define Elements in `locator.properties`
Group your selectors cleanly by platform and module. You can mix Web XPaths, native Appium IDs, Accessibility IDs, and Android UI Automator expressions together:

```properties
# =========================================================================
# CENTRALIZED LOCATOR REPOSITORY (locator.properties)
# =========================================================================

# 💻 Web Admin Portal Elements
admin.drivers.menu.icon=//i[contains(@class,'fa-motorcycle fa-solid iconstyle')]
admin.drivers.fullname.input=//label[contains(.,'Full Name')]/following::input[1]
admin.drivers.phone.input=//input[@type='tel']
admin.drivers.profile_image.file=//input[@type='file']
admin.drivers.submit.button=//button[contains(.,'Submit')]
admin.drivers.success_toast.div=//div[@aria-label='Added Successfully']

# 📱 Mobile Driver App Elements (High-Speed Native Selectors)
mobile.driver.permission_allow.btn=id=com.android.permissioncontroller:id/permission_allow_button
mobile.driver.agree.btn=automator=new UiSelector().text("AGREE & CONTINUE")
mobile.driver.get_started.btn=accessibility=Get Started
mobile.driver.next.btn=id=com.we1.driver:id/btn_next
mobile.driver.mobile_number_field.btn=automator=new UiSelector().className("android.widget.EditText")
```
### 📊 Setup in Excel Sheet
Your Excel sheet stays clean, readable, and focused purely on business logic. Use the exact key names from your properties file in the **Target / Locator** column:

| Step | Test Step Description | Action | Value | Target (Property Key Only) |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Open Admin Panel | `openurl` | - | https://dev.we1.co/#/login |
| **2** | Click Drivers Menu | `click` | - | "**admin.drivers.menu.icon**" |
| **3** | Type Unique Name | `type` | Onboard_{randomAlpha} >> autoName | "**admin.drivers.fullname.input**" |
| **4** | Type Unique Phone | `type` | 98{timestamp} >> driverPhone | "**admin.drivers.phone.input**" |
| **5** | Attach Profile Photo | `uploadfile` | src/main/resources/test-data/driver.jpg | "**admin.drivers.profile_image.file**" |
| **6** | Attach Vehicle Photo | `uploadfile` | src/main/resources/test-data/auto.jpg | "**admin.drivers.vehicle_image.file**" |
| **7** | Click Save Record | `click` | - | "**admin.drivers.submit.button**" |

---

## 📱 3. Mobile Specific Actions
Handled by the `MobileActions.java` class. These keywords are optimized for Appium. To ensure maximum execution speed and stability, the framework explicitly prioritizes direct locators (**Accessibility ID**, **Resource ID**, and **Android UI Automator**) over slower XPath expressions.

| Action | Phrase Examples | Description |
| :--- | :--- | :--- |
| **`tap`** | tap, mobile click | Performs a touch interaction. Parses fast-execution locators like `accessibility=`, `id=`, or `automator=` before falling back to XPath. |
| **`type`** | type, enter text | Clears the field and inputs text into the targeted element using optimized locators. |
| **`wait_until_visible`** | wait for element, wait until visible | Pauses execution dynamically until the target element is visible on the screen. |
| **`swipe`** | swipe, scroll | Swipes the screen in the specified direction. Values: **up**, **down**. |
| **`hide_keyboard`** | hide keyboard, close keypad | Dismisses the mobile keyboard to prevent UI obstruction (essential for form flows). |
| **`set_location`** | set location, geo location | Inject custom GPS coordinates (Latitude;Longitude) into the active emulator mid-test. |

### High-Speed Locator Syntax Examples
When executing actions, use the prefix mapping below to bypass slow XPath parsing:
* **Accessibility ID:** `accessibility=Get Started`
* **Resource ID:** `id=com.we1.customer:id/et_email`
* **UI Automator:** `automator=new UiSelector().text("Submit")`

---

> ⚠️ **IMPORTANT ALERT ON WAITS:** > Always utilize the dynamic **`wait_until_visible`** action strategy. Relying on hardcoded thread sleeps or setting excessive execution timeout limits (e.g : wait,3000) can severely degrade emulator performance, exhaust system memory, and trigger network timeouts or ADB disconnections mid-run.

---

### Excel Test Sheet Example

| Test Step Description | Action | Value | Target (Locator Strategy) |
| :--- | :--- | :--- | :--- |
| Click Get Started Button | **tap** | - | "accessibility=Get Started" |
| Enter User Email Address | **type** | mohammed.faizal@example.com | "id=com.we1.customer:id/et_email" |
| Click Form Submit Button | **tap** | - | "automator=new UiSelector().text("Submit")" |
| Wait for Done | **wait_until_visible** | - | "accessibility=Done" |
| Hide Active Keyboard | **hide_keyboard** | - | - |
| Force Driver Location | **set_location** | 13.0533;80.2514 | - |
| Scroll Down App Screen | **swipe** | **down** | - |

---

## 🛠️ 4. Navigation & System

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **openurl** | `Maps to`, `open url`, `go to url`, `Maps` | Opens a specific website URL. |
| **back / forward** | `back`, `forward` | Browser navigation history. |
| **max / min** | `maximize`, `minimize` | Controls the browser window size. |
| **screenshot** | `screenshot`, `take screenshot` | Captures the current screen. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Open Login Page | openurl | - | https://dev.we1.co/#/login |
| Maximize Browser | maximize | - | - |

---

## ⌨️ 5. Interactions & Input

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **type** | `enter the`, `type`, `input`, `fill` | Enters text into a field. |
| **click** | `click`, `press` | Clicks a button, link, or element. |
| **clear** | `clear text`, `empty field`, `remove value` | Wipes the content of an input box. |
| **select** | `select [value]` | Selects from a dropdown (excludes file/radio). |
| **tab** | `tab key`, `tab` | Simulates the **TAB** key. |
| **press_enter** | `press enter`, `enter key` | Simulates the **ENTER** key. |
| **arrows** | `arrow_down`, `arrow_up` | Simulates keyboard arrow keys. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Enter SuperAdmin Email | type | admin@gmail.com | "//input[@placeholder='Enter email']" |
| Click Login Button | click | - | "//button[@type='submit']" |
| Scroll Grid Right | tab | 5 | "//div[@class='grid-body']" |

---

## 📁 6. File Uploads & System Tools

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **uploadfile** | `upload`, `attach`, `select file`, `choose file` | Handles standard web file uploads. |
| **waitforupload**| `wait for upload` | Pauses until a file finishes uploading. |
| **autoit** | `autoit`, `runautoit`, `executeautoit` | Triggers an AutoIt script for system dialogs. |
| **robotupload** | `robot`, `robotupload`, `uploadrobot` | Uses Java Robot class for OS-level uploads. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Upload License Image | uploadfile | "src/main/resources/test-data/license.jpg" | "//input[@type='file']" |

---

## 🔍 7. Verification & Assertions

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **verifyvisible** | `displayed`, `visible`, `appear`, `shown` | Confirms element is on screen. |
| **verifyhidden** | `not displayed`, `should not appear`, `hidden` | Confirms element is NOT visible. |
| **verifytext** | `verify text`, `label`, `verify exact text` | Checks if the text matches exactly. |
| **verifycontains**| `contains`, `text contains`, `label contains` | Checks if text exists within a string. |
| **verifyenabled** | `enabled`, `disabled` | Checks if a button is clickable or greyed out. |
| **verifyselected**| `selected`, `checked` | Checks state of checkboxes/radio buttons. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Verify Dashboard Load | verifyvisible | - | "//div[contains(text(),'Statistics')]" |
| Check Success Toast | verifytext | Added Successfully | "//div[@role='alert']" |

---

## 🛡️ 8. Advanced Presence Assertions (Negative Testing)

These keywords allow you to perform strict validation on whether an element should or should not exist in the DOM. This is particularly useful for verifying **Role-Based Access Control (RBAC)** where certain menus must be hidden from specific users.

| Action | Phrase Examples | Description |
| :--- | :--- | :--- |
| **element_present** | `element exists`, `present` | Confirms an element is in the DOM. Uses a **5-second explicit wait** before failing. |
| **element_absent** | `element not present`, `absent` | Confirms an element is **NOT** in the DOM. It temporarily disables implicit waits to perform an immediate check without slowing down the test. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Check Transport Menu exists | element_present | - | "//h2[normalize-space()='Transport Services']" |
| Check Delivery Service exists | element_absent | - | "//h2[normalize-space()='Delivery Services']" |
| Check Provider Service exists | element_absent | - | "//h2[normalize-space()='Provider Services']" |

> **💡 Technical Note:** When using `element_absent`, the framework automatically sets `implicitlyWait` to 0 seconds to ensure the check is instantaneous, then restores your default settings immediately after.
---


## ⏳ 9. Explicit Waits & Toasts

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **waitforvisible**| `wait until visible`, `wait for visible` | Pauses until element appears. |
| **waitforpageload**| `wait for page`, `wait for load` | Waits for the entire page to finish loading. |
| **waitfortoast** | `wait for toast` | Pauses for the success/error message popup. |
| **verifysuccesstoast**| `toast success` | Specifically checks for a green success toast. |
| **wait** | `wait 5` | A simple static pause (value is in seconds). |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Wait for Success | waitfortoast | - | - |
| Hard Pause | wait | 3 | - |

---

## 🖱️ 10. Scrolling, Frames & Maps

| Action | Phrase Examples (Natural Language) | Description |
| :--- | :--- | :--- |
| **scrolltoelement**| `scroll to element`, `scroll to` | Moves view to a specific element. |
| **scrolltotop** | `scroll up`, `scroll to top` | Moves to the top of the page. |
| **scrolltobottom**| `scroll down`, `scroll to bottom` | Moves to the bottom of the page. |
| **switchtoframe** | `switch to frame` | Moves driver focus inside an iFrame. |
| **drawpolygon** | `draw the polygon` | Specialized interaction for map elements. |
| **verifygridvalue**| `verify grid` | Checks values inside a data table/grid. |

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Map City Boundary | drawpolygon | -120;-120 : 120;-120 : 120;120 : -120;120 | "//div[@class='map-container']" |

---

## 🎲 11. Dynamic Placeholders (Value Column)

| Placeholder | Result Example | Best For |
| :--- | :--- | :--- |
| **`{timestamp}`** | `301715` | Unique IDs/Names (Numbers only). |
| **`{randomAlpha}`** | `QWERTZ` | **Plumber Names** (Letters only). |
| **`{randomPhone}`** | `9845123456` | Valid 10-digit Indian Mobile format. |

**Excel Example:**

| Test Step Description | Action | Value                | Target (XPath) |
| :--- | :--- |:---------------------| :--- |
| Enter Unique Area | type | Chennai_{timestamp}  | "//input[@id='areaName']" |
| Enter Plumber Name | type | Plumber{randomAlpha} | "//input[@id='providerName']" |

> **⚠️ STRICT VALIDATION RULE:** If a field (like Plumber Name) does not allow numbers or underscores, use **`Plumber{randomAlpha}`** directly (No spaces, no symbols).

---

## 💾 12. Save & Reuse Logic

Capture a value in one step to use it in a later step.

### **How to Save:**

Use the `>>` operator in the **Value** column.
- **Action:** `type`
- **Value:** `NewUser{randomAlpha} >> savedName`
- *Result:* Generates `NewUserBQK`, types it, and stores it as "savedName".

### **How to Reuse:**

Wrap the variable name in curly braces `{}`.
- **Action:** `type`
- **Value:** `{savedName}`
- *Result:* Types the exact same value generated previously.

**Excel Example:**

| Step | Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Create Store Name | type | KFC_{timestamp} >> storeName | "//input[@id='sname']" |
| 2 | ...Other Steps... | ... | ... | ... |
| 3 | Filter Created Store | type | {storeName} | "//input[@placeholder='Search']" |

---

## 📊 13. Reporting & Debugging
The framework is designed to make debugging easy:
1.  **Red Box Highlighting:** If a step fails, the report screenshot will show a **Red Border** around the specific element that failed.
2.  **Video Logs:** Check `test-outputs/videos` for a full recording of the execution.
3.  **Smart Errors:** The framework tells you if the error was a "Timeout" (Missing element) or a "Validation Error" (Form rejected).

---

## 🔗 14. Cross-Sheet Dependencies (Preconditions)

The framework supports **Recursive Dependencies**. If one test suite (Sheet) requires data or a state created in another sheet, you can link them directly within the Excel file.

### **How to Use**
In the **Precondition** column (Column 5) of the **very first test case row** (Row 2) of your sheet, use the keyword `RunSheet:` followed by the exact name of the required sheet.

**Excel Example (Inside "AddCityAdmin" sheet):**

| Test Case ID | ... | Precondition |
| :--- | :--- | :--- |
| TC_CA_02 | ... | **Data Dependency:** This test requires an existing area. <br><br> **RunSheet: AddCityArea** <br><br> **Authentication:** SuperAdmin session must be active. |

### **How It Works**
1.  **Detection:** The framework scans the Precondition cell for the `RunSheet:` trigger.
2.  **Recursion:** It automatically pauses the current sheet (`AddCityAdmin`), switches to the dependency sheet (`AddCityArea`), and executes all its steps.
3.  **Return:** Once the dependency sheet finishes, the framework automatically returns to the original sheet to continue the test.
4.  **Smart Execution:** To save time, if `AddCityArea` has already been completed earlier in the same execution run, the framework will recognize this and skip the redundant run.

> **💡 Pro-Tip:** You can include as much descriptive text as you like in the Precondition column (authentication steps, system requirements, etc.). The framework is smart enough to find the `RunSheet:` keyword hidden anywhere in that text.

---

## 🗄️ 15. Database Cleanup & Maintenance

This feature allows the framework to interact directly with the PostgreSQL database to remove test data after a suite finishes. This ensures your environment remains clean and prevents "Duplicate Entry" errors during repeated test runs.

> [!CAUTION]
> ### ⚠️ **WARNING: USE WITH EXTREME CAUTION**
> This tool interacts **directly** with the production/test database. Incorrect queries can result in permanent data loss.
> * **Double-check your syntax** before adding queries to Excel.
> * **Always use specific identifiers** (like `{areaName}`) to ensure you only delete the data you created.
> * **Never** attempt to run queries on tables you are not authorized to modify.

### **The Safety Firewall**
To prevent accidental data loss, the framework includes a **Safety Check**:
* Any `DELETE` or `UPDATE` query **must** contain a `WHERE` clause.
* If a `WHERE` clause is missing, the framework will block the execution and throw an error to protect the database integrity.

| Action | Phrase Examples | Description |
| :--- | :--- | :--- |
| **sql_cleanup** | `sql delete`, `sql cleanup`, `execute sql` | Executes a SQL query against the configured database. |

### **How to Use**
1.  **Action:** Use `sql delete` or `sql_cleanup` in the Action column.
2.  **Value:** Write the full SQL query.
3.  **Dynamic Variables:** You can use saved variables (e.g., `{areaName}`) inside your SQL query to target the specific data created during that test run.

**Excel Example:**

| Test Step Description | Action | Value | Target (XPath) |
| :--- | :--- | :--- | :--- |
| Remove Test Area | sql delete | "DELETE FROM we1.admin_area_list WHERE name = '{areaName}';" | - |
| Remove Auto Driver | sql delete | "DELETE FROM we1.providers WHERE first_name = '{autoName}';" | - |
| Remove Bank Details | sql delete | "DELETE FROM we1.provider_bank_details WHERE holder_name = '{autoName}';" | - |
| Hard Pause | wait | 2 | - |

### **Common Cleanup Queries**
Below are frequently used cleanup templates for various modules:

| Module | SQL Template |
| :--- | :--- |
| **Store/Hotel** | `DELETE FROM we1.store_details WHERE name = '{storeName}';` |
| **Users** | `DELETE FROM we1.users WHERE first_name = '{customerName}';` |
| **Documents** | `DELETE FROM we1.required_documents WHERE name = '{documentName}';` |
| **Promo Codes** | `DELETE FROM we1.promocode_details WHERE promo_code = '{promoCodeName}';` |

> **💡 Best Practice:** > Always place your cleanup steps at the **very end** of your Excel sheet in the test sheet named "DataCleanUpSheet" since some data is required for completion of other tests. It is also recommended to add a small `wait` (e.g., 2 seconds) after the final DB command to allow the system to refresh its state.

---

## 🔄 16. Sheet-Level Iteration (Stress & Loop Testing)

The framework supports **Dynamic Loop Execution** directly from your environment settings. If you need to stress-test a specific form, generate bulk test data, or repeatedly run a single test suite without restarting the browser or duplicating rows in Excel, you can define a repeat count using bracket notation `[X]`.

### **How to Use**
In your active `.properties` file, append the desired number of loops inside square brackets right next to the target sheet name in the `sheets.name` property.

* **Syntax:** `SheetName[NumberOfLoops]`
* **Default Behavior:** If no brackets are provided (e.g., `AddCityArea`), the framework automatically defaults to running the sheet exactly **1 time**.

### **Properties Configuration Example:**
```properties
# This configuration will run AddCustomer 5 times, AddCityArea 1 time, and AddCityAdmin 50 times
sheets.name=AddCustomer[5],AddCityArea,AddCityAdmin[50]
```

---

## 🌍 17. Multi-Environment Configuration (CLI Support)

The framework now supports **Dynamic Configuration Loading**. Instead of manually editing the `config.properties` file to switch between projects (e.g., ERP vs. WE1), you can maintain separate configuration files and trigger them via the command line.

### **How it Works**
The framework looks for a system property named `env`.
* If you pass `-Denv=erp`, it loads `erp.properties`.
* If you pass `-Denv=we1_superadmin`, it loads `we1_superadmin.properties`.
* **Fallback:** If no environment is specified, it defaults to `config.properties`.

### **Setup Guide**
Create separate `.properties` files in your root folder. Ensure each file has its own `base.url`, `excel.name`, and `sheets.name`.

#### **Sample Example: `hybrid.properties`**
```properties

admin.email=super_admin@gmail.com
admin.password=R1mep@321
base.url=https://dev.we1.co/#/login
dashboard.url=https://dev.we1.co/#/page-module/dashboard_dynamic
browser=chrome
headless=false
appium.url=http://127.0.0.1:4723
user.device.id=emulator-5554
driver.device.id=emulator-5556
user.apk.path=C:/EIT_PROJECTS/WE1_MOBILE_APPS/We1_User.apk
driver.apk.path=C:/EIT_PROJECTS/WE1_MOBILE_APPS/We1_Driver.apk
user.app.package=com.we1.customer
user.app.activity=com.we1.customer.MainActivity
driver.app.package=com.we1.driver
driver.app.activity=com.we1.driver.MainActivity
db.url=jdbc:postgresql://194.233.75.197:5432/we1
db.user=postgres
db.password=we12025
filter.name=TC_CA_
excel.name=we1_hybrid_regression.xlsx
sheets.name=HybridTestFlow

```
### **Execution Commands (CLI)**
To run your tests against a specific environment, open your terminal in the project root and use the following Maven commands:

| Target Site          | CLI Command                           |
|:---------------------|:--------------------------------------|
| **Default Fallback** | `mvn exec:java -Denv=config`          |
| **ERP Regression**   | `mvn exec:java -Denv=erp`             |
| **WE1 Super Admin**  | `mvn exec:java -Denv=we1_superadmin`  |
| **WE1 Hybrid**       | `mvn exec:java -Denv=we1_hybrid`      |
---

> **💡 Note:** The `-Denv` parameter tells the framework exactly which `.properties` file to load before starting the browser. Make sure your environment file (e.g., `erp.properties`) is located in the root folder of your project.
---

## ⚠️ CRITICAL: MONITOR SCREEN ALIGNMENT SETUP
> **🚨 MUST-READ BEFORE RUNNING HYBRID TESTS**
>
> The ABCD Framework uses an **Adaptive Initial Resizer Engine** for split-screen parallel execution.
> * When a hybrid test is detected, the **Web Browser automatically launches and locks itself to the LEFT side of the monitor** (Coordinates: `X=0, Width=960, Height=1080`).
> * **Action Required:** Before launching your tests, you **MUST manually move and align all Android Emulators (User, Driver, Store) to the RIGHT side of your monitor screen (Starting from X=960 onwards).**
>
> *Failure to arrange emulators to the right will cause the Chrome browser window to overlap your active mobile devices during the automation layout phase, blocking real-time visual tracking.*

---

## 💡 Best Practices
* **Relative Paths:** Use `src/main/resources/test-data/image.jpg` for uploads. Never use `C:\Users\...`.
* **Excel Locking:** **Always close your Excel file** before running a test to avoid file access errors.
* **Wait for Toasts:** Use a `wait for toast` or `wait 2` step after clicking "Save" to ensure the system processes the request.
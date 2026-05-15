<div align="center">

# ⚙️ ABCD HYBRID AUTOMATION FRAMEWORK FOR MOBILE AND WEB TESTING
### *Keyword-Driven Selenium & Appium Testing Engine*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Selenium](https://img.shields.io/badge/Selenium-4.x-43B02A?style=for-the-badge&logo=selenium&logoColor=white)](https://www.selenium.dev/)
[![Appium](https://img.shields.io/badge/Appium-3.x-662d91?style=for-the-badge&logo=appium&logoColor=white)](http://appium.io/)
[![JCodec](https://img.shields.io/badge/Video-JCodec--0.2.5-blue?style=for-the-badge)](https://jcodec.org/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Android Studio](https://img.shields.io/badge/Android%20Studio-3DDC84?style=for-the-badge&logo=android-studio&logoColor=white)](https://developer.android.com/studio)
[![Appium Inspector](https://img.shields.io/badge/Appium%20Inspector-662D91?style=for-the-badge&logo=appium&logoColor=white)](https://github.com/appium/appium-inspector)

**A Seamless Multi-Platform Testing Engine for Web, User Apps, and Driver Apps.**
</div>

---

## 📖 Overview
The **ABCD Hybrid Framework** is an advanced evolution of the keyword-driven approach, designed to handle complex ecosystems where **Web Portals** and **Mobile Applications** (Android/iOS) must work in sync. Whether you are validating an Admin dashboard on Chrome or a Driver's real-time response on an Emulator, this framework manages all sessions through a single Excel-based logic using selenium and appium.

---

## 📸 Test Reporting Preview
The framework generates rich and interactive HTML reports with video recordings for each tests. Each test step includes automatic failure screenshots for rapid debugging.

![Test Report Screenshot](report_screenshot.png)

---

## 🏗️ Hybrid Architecture
This framework operates on a **Multi-Session Driver Pool** logic:
* **Parallel Driver Management:** Maintains simultaneous connections to ChromeDriver and multiple Appium sessions (e.g., `web`, `user`, and `driver`).
* **Dynamic Context Switching:** Automatically switches focus between the browser and mobile devices based on the test step.
* **Live Browser Overlay:** A custom-built JavaScript dashboard injected into the Web session that provides real-time status updates of mobile actions.

---

## ✨ Core Features

* **🚀 Unified Keyword Engine:** Use the same `click`, `type`, and `verify` keywords for both Web and Mobile.
* **🖥️ Live Command Center:** Real-time overlay on the browser showing the current Step Number, Action, and Active Role (USER/DRIVER/WEB).
* **📱 Multi-App Orchestration:** Seamlessly test flows that start on Web (Super Admin), move to a User App, and end on a Driver App.
* **🎲 Intelligent Data Generation:** Built-in placeholders for `{timestamp}`, `{randomAlpha}`, and `{randomPhone}`.
* **💾 Data Persistence:** Capture values from one platform (e.g., an Order ID from Mobile) and use it on another (e.g., searching on Web) using the `>>` operator.
* **🎥 Synchronized Media Logs:** Automated video recording (MP4) and failure screenshots for every session.
* **🗄️ Database Integration:** Direct PostgreSQL connectivity for cleaning up test data or verifying backend records.

---

## 💻 Tech Stack
| Component | Technology |
| :--- | :--- |
| **Language** | Java 17 |
| **Web Engine** | Selenium WebDriver 4.27.0 |
| **Mobile Engine** | Appium Java Client 9.3.0 |
| **Automation Server** | Appium v3.4.2 |
| **Reporting** | Extent Reports / Custom HTML |
| **Video Encoding** | JCodec 0.2.5 |
| **Database** | PostgreSQL |

---

## 🚦 Getting Started

### 1. Prerequisites (Installation)
To run this framework, you must have the following installed on your local machine:
* **Java Development Kit (JDK) 17+**
* **Node.js & NPM** (For Appium)
* **Appium Server:** `npm install -g appium`
* **UiAutomator2 Driver:** `appium driver install uiautomator2`
* **Android SDK & Build Tools** (Set `ANDROID_HOME` environment variable)
* **Android Emulator** (Via Android Studio)
* **Appium Inspector** 

### 2. Framework Setup
1.  **Clone the Repository:**
    ```bash
    git clone [https://github.com/faizal08/ABCD_Hybrid_Framework.git](https://github.com/faizal08/ABCD_Hybrid_Framework.git)
    ```
2.  **Install Maven Dependencies:**
    ```bash
    mvn clean install
    ```

### 3. Running the Hybrid Suite
The framework uses system properties to point to specific configuration files:

| Environment          | CLI Command                          |
|:---------------------|:-------------------------------------|
| **WE1 Super Admin**  | `mvn exec:java -Denv=we1_superadmin` |
| **Hybrid Full Flow** | `mvn exec:java -Denv=we1_hybrid`     |

---

## 📊 Live Dashboard Preview
The framework injects a high-visibility overlay into your browser during execution. It dynamically detects the active role:
* **💻 WEB:** Active when interacting with the Admin Portal.
* **📱 USER:** Active when performing actions on the Customer App.
* **📱 DRIVER:** Active when performing actions on the Driver App.

---

## 🏗️ Architecture & Logic
The framework is built on a modular "Plug-and-Play" design:

* **Action Keywords:** A library of reusable functions (e.g., `click`, `type`, `verify`) that handle browser interactions.
* **Execution Engine:** The core logic that reads the test steps and invokes the corresponding action keywords.
* **Data Controller:** Manages the input from Excel/Property files using Apache POI.
* **Locator Repository:** Centralized storage for element locators (XPath, CSS) to ensure easy maintenance.
* **Media Controller:** Handles **Screen Recording (AVI/MP4)** of every test session for audit trails.

---

## 🎲 Dynamic Data Generation
The framework features a built-in engine to generate unique values on-the-fly. This is essential for testing "Create" forms (like User Registration or Driver Onboarding) where unique names or IDs are required.

| Placeholder | logic | Output Example | Best Used For |
| :--- | :--- | :--- | :--- |
| **`{timestamp}`** | Current Time (Numeric) | `30174522` | Unique IDs, Tracking Numbers |
| **`{randomAlpha}`** | Random Letters (A-Z) | `KJHBTX` | **Plumber/Driver Names**, Usernames |
| **`{randomPhone}`** | 10-Digit Numeric | `9876543210` | Mobile Number fields |

---

### 💾 Data Persistence (Save & Reuse)
You can capture a generated value and reuse it in later steps using the `>>` operator:
1. **Save:** `NewUser{randomAlpha} >> myUser` (Generates `NewUserABC` and saves it).
2. **Reuse:** Use `{myUser}` in any following step to type the exact same name.

---
## 🚀 Advanced Execution (CLI)
You can switch environments without modifying any configuration files by using the `-Denv` system property:

| Target Environment | CLI Command |
| :--- | :--- |
| **Default Config** | `mvn exec:java -Denv=config` |
| **ERP System** | `mvn exec:java -Denv=erp` |
| **WE1 Super Admin** | `mvn exec:java -Denv=we1_superadmin` |
| **Hybrid Full Flow** | `mvn exec:java -Denv=we1_hybrid`     |
---

## ✨ Key Features
* **🚀 High Reusability:** Write a keyword once and use it across hundreds of test cases.
* **📊 External Configuration:** Manage test execution flows via Excel or Properties files without changing code.
* **🔍 Robust Logging:** Detailed console logs for every action performed during execution.
* **🛠️ Error Handling:** Built-in try-catch blocks and explicit waits to handle synchronization issues.
* **🎥 Automated Video Logs:** Every test execution is recorded and saved to `test-outputs/videos`. Perfect for reviewing "flaky" tests that pass locally but fail in CI.
* **🗄️ Automated DB Cleanup:** Direct PostgreSQL integration to delete/update test records after execution, keeping environments clean.
* **🌍 Multi-Environment Support:** Run tests against different sites (ERP, Admin, User,Hybrid) instantly using dynamic CLI arguments.

---

## 💻 Tech Stack
| Component | Technology |
| :--- | :--- |
| **Language** | Java 17 |
| **Automation Engine** | Selenium WebDriver |
| **Data Management** | Apache POI / Properties |
| **Video Encoding** | **JCodec 0.2.5** (Native Java) |
| **Build Tool** | Maven |

---

- ## 📚 Documentation
- [Main Framework Guide](README.md)
- [Keywords & Excel Writing Guide](KEYWORDS_REFRENCE.md) <-- Click to go to keyword Refrence file here!

---

## 📧 Contact
- **Developer:** [Faizal](https://github.com/faizal08)
- **Email:** [reachfaizal08@gmail.com](mailto:reachfaizal08@gmail.com)

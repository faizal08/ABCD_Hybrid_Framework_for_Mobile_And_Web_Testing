<div align="center">

# ⚙️ ABCD AUTOMATION FRAMEWORK
### *Keyword-Driven Selenium Testing Engine*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Selenium](https://img.shields.io/badge/Selenium-4.x-43B02A?style=for-the-badge&logo=selenium&logoColor=white)](https://www.selenium.dev/)
[![JCodec](https://img.shields.io/badge/Video-JCodec--0.2.5-blue?style=for-the-badge)](https://jcodec.org/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)

**A Reusable and Scalable Framework for Simplified Web Automation**
</div>

---

## 📖 Overview
The **ABCD Framework** is designed to bridge the gap between manual testers and automation. By utilizing a **Keyword-Driven approach**, it allows test cases to be written in plain language (keywords) within external files, which the engine then translates into automated actions using Selenium WebDriver.

---

## 🏗️ Architecture & Logic
The framework is built on a modular "Plug-and-Play" design:

* **Action Keywords:** A library of reusable functions (e.g., `click`, `type`, `verify`) that handle browser interactions.
* **Execution Engine:** The core logic that reads the test steps and invokes the corresponding action keywords.
* **Data Controller:** Manages the input from Excel/Property files using Apache POI.
* **Locator Repository:** Centralized storage for element locators (XPath, CSS) to ensure easy maintenance.
* **Media Controller:** Handles **Screen Recording (AVI/MP4)** of every test session for audit trails.

---

## 📸 Test Reporting Preview
The framework generates rich and interactive HTML reports with video recordings for each tests. Each test step includes automatic failure screenshots for rapid debugging.

![Test Report Screenshot](report_screenshot.png)

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
---

## ✨ Key Features
* **🚀 High Reusability:** Write a keyword once and use it across hundreds of test cases.
* **📊 External Configuration:** Manage test execution flows via Excel or Properties files without changing code.
* **🔍 Robust Logging:** Detailed console logs for every action performed during execution.
* **🛠️ Error Handling:** Built-in try-catch blocks and explicit waits to handle synchronization issues.
* **🎥 Automated Video Logs:** Every test execution is recorded and saved to `test-outputs/videos`. Perfect for reviewing "flaky" tests that pass locally but fail in CI.
* **🗄️ Automated DB Cleanup:** Direct PostgreSQL integration to delete/update test records after execution, keeping environments clean.
* **🌍 Multi-Environment Support:** Run tests against different sites (ERP, Admin, User) instantly using dynamic CLI arguments.

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

## 🚦 Getting Started

### Prerequisites
* JDK 17+
* Maven 3.x
* Compatible WebDrivers (ChromeDriver/GeckoDriver)

### Installation
1.  **Clone the Repo:**
    ```bash
    git clone [https://github.com/faizal08/ABCD_Framework.git](https://github.com/faizal08/ABCD_Framework.git)
    ```
2.  **Install Dependencies:**
    ```bash
    mvn clean install
    ```
3.  **Run Tests:**
    ```bash
    mvn test
    ```

---

## 📧 Contact
- **Developer:** [Faizal](https://github.com/faizal08)
- **Email:** [reachfaizal08@gmail.com](mailto:reachfaizal08@gmail.com)

# Uber Invoice Downloader (Java + Playwright)

An automated utility built with Java and the Playwright SDK to securely login, navigate, and batch-download historic business trip invoices from the Uber web portal.

## Features

- **Two-Phase Authentication**: Seamlessly handles Uber's anti-bot protections.
  - **Phase 1 (Interactive Setup)**: If no session is found, the program launches a visible browser where you log in manually, complete MFA/CAPTCHAs, and confirm in your terminal. It then serializes your secure session state to an `auth_state.json` file.
  - **Phase 2 (Headless Execution)**: On subsequent runs, the program runs headlessly in the background using the saved session state.
- **Dynamic Infinite Scroll**: Automatically scrolls through the React-based Uber Trips dashboard, dynamically loading historical trips based on a 30-day lookback window.
- **Resilient Downloads**: Intercepts native PDF downloads and gracefully falls back to generating print-to-PDF receipts when native buttons are missing. Implements local try-catch and retry logic for individual trips.

## Prerequisites

- **Java Development Kit (JDK)**: Java 17 or Java 21 (LTS) installed.
- **Maven**: Installed and available on your system `PATH`.
- (Playwright dependencies and browser binaries will be downloaded automatically on the first run).

## Building

Clone the repository and build the project using Maven:

```bash
mvn clean package -DskipTests
```

## Running the Application

Execute the compiled application using Maven:

```bash
mvn exec:java -Dexec.mainClass="com.uber.automation.UberInvoiceDownloader"
```

### First Run (Interactive Mode)
When you run the application for the first time, a Chromium browser window will open.
1. Log into your Uber account.
2. Complete any required Multi-Factor Authentication (MFA).
3. Navigate to the main dashboard.
4. Go back to your terminal and **press ENTER**.
5. The application will save your session to `auth_state.json` and proceed to download your invoices.

### Subsequent Runs (Headless Mode)
As long as `auth_state.json` remains valid and unexpired, running the application again will execute headlessly in the background without requiring manual intervention.

## Output

Downloaded PDF invoices are organized in local date-stamped directories:
```
./downloads/uber_invoices_YYYY_MM_DD/Uber_Invoice_YYYY_MM_DD_[TripID].pdf
```

## Disclaimer
This project is for educational and personal automation purposes only. Web scraping and automation might violate the terms of service of some platforms. Ensure you comply with all applicable policies before running this software.

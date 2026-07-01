# Uber Invoice Downloader (Java + Playwright)

An automated utility built with Java and the Playwright SDK to securely login, navigate, and batch-download historic business trip invoices from the Uber web portal.

## Features

- **Two-Phase Authentication**: Seamlessly handles Uber's anti-bot protections.
  - **Phase 1 (Interactive Setup)**: If no session is found, the program launches a visible browser where you log in manually, complete MFA/CAPTCHAs, and confirm in your terminal. It then serializes your secure session state to an `auth_state.json` file.
  - **Phase 2 (Headless Execution)**: On subsequent runs, the program runs headlessly in the background using the saved session state.
- **Dynamic Pagination & Infinite Scroll**: Automatically detects and clicks the dashboard's "More" button (falling back to scrolling lazy-loaded elements) to cleanly load all historical trips within a 30-day lookback window.
- **Resilient Downloads**: Intercepts native PDF downloads (or prints to PDF if missing) ensuring PDF formats are saved. Implements local try-catch and retry logic for individual trips.

## Prerequisites

- **Java Development Kit (JDK)**: Java 17 or Java 21 (LTS) installed.
- **Maven**: Installed and available on your system `PATH`.
- **Browser & System Dependencies**: 
  - On Windows and macOS, Playwright will automatically download the Chromium browser binaries on the first run.
  - On Linux, additional system-level libraries are required by Chromium. You can install them by running:
    ```bash
    mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"
    ```

## Building

Clone the repository and build the project using Maven:

```bash
mvn clean package -DskipTests
```

## Running the Application

Execute the compiled application using Maven. The following syntax is compatible across Unix-based shells (Bash, Zsh) and Windows Command Prompt / PowerShell:

```bash
mvn exec:java -Dexec.mainClass=com.uber.automation.UberInvoiceDownloader
```

### Running on Headless Linux Servers/Containers

Because Phase 1 (Interactive Setup) requires a graphical interface (non-headless mode) to perform login, MFA, and bypass anti-bot protections, running it directly on a headless remote server will fail. Follow these steps to use this downloader on a headless server:

1. **Run locally first**: Clone the repository and execute the application on your local machine with a graphical environment (Windows, macOS, or Linux Desktop).
2. **Generate Session**: Log in and complete the interactive setup. This will generate the `auth_state.json` file in the project's root folder.
3. **Copy to Server**: Copy `auth_state.json` to the root folder of the application on your remote headless server.
4. **Execute**: Run the application on the headless server. Since the session state exists, it will directly start in headless mode (Phase 2) and download the invoices.

### First Run (Interactive Mode)

When you run the application for the first time, a Chromium browser window will open.

1. Log into your Uber account.
2. Complete any required Multi-Factor Authentication (MFA).
3. Navigate to the main dashboard.
4. Select the account you want to extract (eg. Personal or business)
5. Select trip period to extract (eg "Past 30 days")
6. Go back to your terminal and **press ENTER**.
7. The application will save your session to `auth_state.json` and proceed to download your invoices.

### Subsequent Runs (Headless Mode)

As long as `auth_state.json` remains valid and unexpired, running the application again will execute headlessly in the background without requiring manual intervention.

## Output

Downloaded files (PDF invoices) are organized in local date-stamped directories:

```
./downloads/uber_invoices_RUN_DATE/Uber_Invoice_TRIP_DATE_PRICE.pdf
```

Where:
- `RUN_DATE`: Format `YYYY_MM_DD` (the date when the script was run).
- `TRIP_DATE`: Format `YYYYMMDD` (the date of the actual trip, e.g. `20260630`).
- `PRICE`: Format `CURRENCY_AMOUNT` (the trip price, e.g. `CLP7853`).

## Disclaimer

This project is for educational and personal automation purposes only. Web scraping and automation might violate the terms of service of some platforms. Ensure you comply with all applicable policies before running this software.

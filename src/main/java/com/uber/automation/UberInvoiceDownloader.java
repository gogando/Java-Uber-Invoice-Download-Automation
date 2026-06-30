package com.uber.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UberInvoiceDownloader {
    
    private static final String AUTH_STATE_PATH = "auth_state.json";
    private static final int DAYS_LIMIT = 365;
    
    public static void main(String[] args) {
        System.out.println("[+] Starting Uber Invoice Downloader");
        
        try (Playwright playwright = Playwright.create()) {
            BrowserType chromium = playwright.chromium();
            Path authPath = Paths.get(AUTH_STATE_PATH);
            
            Browser browser = null;
            BrowserContext context = null;
            Page page = null;
            boolean loggedIn = false;
            
            // Try headless Phase 2 first
            if (Files.exists(authPath)) {
                System.out.println("[+] Found auth_state.json. Attempting headless login (Phase 2)...");
                try {
                    browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(true));
                    context = browser.newContext(new Browser.NewContextOptions().setStorageStatePath(authPath));
                    page = context.newPage();
                    
                    System.out.println("[+] Navigating to Trips dashboard...");
                    page.navigate("https://riders.uber.com/trips");
                    page.waitForLoadState(LoadState.LOAD);
                    
                    if (page.url().contains("auth.uber.com") || page.locator("input[type='password']").count() > 0) {
                        System.out.println("[!] Session expired or invalid.");
                        // Clean up headless to start interactive
                        page.close();
                        context.close();
                        browser.close();
                        page = null;
                        context = null;
                        browser = null;
                    } else {
                        System.out.println("[+] Successfully accessed Trips dashboard headlessly.");
                        loggedIn = true;
                    }
                } catch (Exception e) {
                    System.out.println("[!] Headless login failed: " + e.getMessage());
                    if (page != null) page.close();
                    if (context != null) context.close();
                    if (browser != null) browser.close();
                    page = null;
                    context = null;
                    browser = null;
                }
            }
            
            // If not logged in, trigger interactive Phase 1
            if (!loggedIn) {
                System.out.println("[!] Initiating interactive setup (Phase 1)...");
                browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(false));
                context = browser.newContext();
                page = context.newPage();
                
                page.navigate("https://riders.uber.com/trips");
                
                System.out.println("=========================================================================");
                System.out.println("[!] ACTION REQUIRED: Please log in using the opened browser window.");
                System.out.println("[!] Complete MFA or any CAPTCHAs.");
                System.out.println("[!] Once you are fully logged in and on the Trips dashboard,");
                System.out.println("[!] press ENTER in this console to continue.");
                System.out.println("=========================================================================");
                
                Scanner scanner = new Scanner(System.in);
                scanner.nextLine();
                
                System.out.println("[+] Saving session state to " + authPath.toString());
                context.storageState(new BrowserContext.StorageStateOptions().setPath(authPath));
                
                // Ensure we navigate back to trips or that we are on it
                if (!page.url().contains("riders.uber.com/trips")) {
                    page.navigate("https://riders.uber.com/trips");
                    page.waitForLoadState(LoadState.LOAD);
                }
                System.out.println("[+] Successfully accessed Trips dashboard.");
            }
            
            // Download Loop using the active page
            List<String> validTripUrls = extractValidTrips(page);
            System.out.println("[+] Discovered " + validTripUrls.size() + " trips to process.");
            downloadInvoices(page, validTripUrls);
            
            // Clean up
            if (page != null) page.close();
            if (context != null) context.close();
            if (browser != null) browser.close();
            
        } catch (Exception e) {
            System.err.println("[X] Fatal Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<String> extractValidTrips(Page page) throws InterruptedException {
        List<String> extractedUrls = new ArrayList<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(DAYS_LIMIT);
        
        System.out.println("[+] Finding trips since: " + cutoffDate);
        
        int previousCount = 0;
        int retries = 0;
        String selector = "a[href*='/trips/'], a[href*='jobId=']";
        
        while (retries < 3) {
            try {
                page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(15000));
            } catch (PlaywrightException e) {
                System.out.println("[!] No trips found or timeout reached.");
                break;
            }
            
            Locator tripLinks = page.locator(selector);
            int currentCount = tripLinks.count();
            
            System.out.println("[DEBUG] Found " + currentCount + " potential trip links matching '" + selector + "'");
            
            for (int i = 0; i < currentCount; i++) {
                try {
                    String href = tripLinks.nth(i).getAttribute("href");
                    String tripId = extractTripId(href);
                    if (tripId != null && !tripId.isEmpty()) {
                        String fullUrl = "https://riders.uber.com/trips/" + tripId;
                        if (!extractedUrls.contains(fullUrl)) {
                            extractedUrls.add(fullUrl);
                            System.out.println("[+] Discovered trip ID: " + tripId);
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            if (extractedUrls.size() >= DAYS_LIMIT * 2) {
                System.out.println("[+] Reached arbitrary scroll limit (" + extractedUrls.size() + " trips).");
                break;
            }
            
            // Scroll to load more
            if (currentCount > 0) {
                System.out.println("[+] Scrolling last trip element into view to trigger lazy loading...");
                tripLinks.last().scrollIntoViewIfNeeded();
            } else {
                System.out.println("[+] Scrolling window to bottom...");
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            }
            
            Thread.sleep((long) (2500 + Math.random() * 2000));
            
            tripLinks = page.locator(selector);
            if (tripLinks.count() == currentCount) {
                retries++; // No new trips loaded
            } else {
                retries = 0;
                previousCount = currentCount;
            }
        }
        
        return extractedUrls;
    }
    
    private static void downloadInvoices(Page page, List<String> tripUrls) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        Path downloadDir = Paths.get("./downloads/uber_invoices_" + dateStr);
        
        try {
            Files.createDirectories(downloadDir);
        } catch (Exception e) {
            System.err.println("[X] Failed to create download directory: " + e.getMessage());
            return;
        }
        
        for (int i = 0; i < tripUrls.size(); i++) {
            String url = tripUrls.get(i);
            String tripId = extractTripId(url);
            if (tripId == null || tripId.isEmpty()) {
                tripId = "unknown_" + System.currentTimeMillis();
            }
            
            System.out.println("[+] Processing trip " + (i + 1) + "/" + tripUrls.size() + " (" + tripId + ")");
            
            // Retry logic
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    page.navigate(url);
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    
                    String pageText = "";
                    try {
                        pageText = page.locator("body").innerText();
                    } catch (Exception e) {
                        System.err.println("    [!] Warning: Failed to extract page text: " + e.getMessage());
                    }
                    
                    String tripDate = parseTripDate(pageText);
                    String tripPrice = parseTripPrice(pageText);
                    
                    String filename = "Uber_Invoice_" + tripDate + "_" + tripPrice + ".pdf";
                    Path outputPath = downloadDir.resolve(filename);
                    
                    // Look for Download PDF button
                    Locator downloadBtn = page.locator("a:has-text('Download Invoice'), a:has-text('Download PDF'), button:has-text('Download')");
                    
                    if (downloadBtn.count() > 0) {
                        System.out.println("    [-] Found native download button. Intercepting download...");
                        Download download = page.waitForDownload(() -> downloadBtn.first().click());
                        download.saveAs(outputPath);
                        System.out.println("    [+] Successfully downloaded to: " + outputPath);
                    } else {
                        System.out.println("    [-] Native download not found. Falling back to page.pdf()...");
                        page.pdf(new Page.PdfOptions().setPath(outputPath).setPrintBackground(true));
                        System.out.println("    [+] Successfully exported PDF to: " + outputPath);
                    }
                    
                    break; // Success, break retry loop
                    
                } catch (Exception e) {
                    System.err.println("    [X] Attempt " + attempt + " failed for " + tripId + ": " + e.getMessage());
                    if (attempt == maxRetries) {
                        System.err.println("    [X] Skipping trip " + tripId + " after " + maxRetries + " attempts.");
                    } else {
                        try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }
        }
    }
    
    private static String parseTripDate(String pageText) {
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        Pattern datePattern = Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})\\s+(\\d{4})");
        Matcher matcher = datePattern.matcher(pageText);
        if (matcher.find()) {
            String monthName = matcher.group(1);
            String dayStr = matcher.group(2);
            String yearStr = matcher.group(3);
            
            int monthVal = 1;
            for (int i = 0; i < months.length; i++) {
                if (months[i].equalsIgnoreCase(monthName)) {
                    monthVal = i + 1;
                    break;
                }
            }
            
            int dayVal = Integer.parseInt(dayStr);
            return String.format("%s%02d%02d", yearStr, monthVal, dayVal);
        }
        // Fallback to today's date if not found
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    private static String parseTripPrice(String pageText) {
        Pattern pricePattern = Pattern.compile("(?i)\\b(CLP|USD|EUR|GBP|ARS|MXN|BRL|COP|PEN|[$€£])\\s*[^0-9a-zA-Z]*\\s*(\\d{1,3}(?:[,.]\\d{3})*(?:[,.]\\d{2})?)");
        Matcher matcher = pricePattern.matcher(pageText);
        if (matcher.find()) {
            String currency = matcher.group(1);
            String amount = matcher.group(2);
            
            if (currency.equals("$")) currency = "USD";
            else if (currency.equals("€")) currency = "EUR";
            else if (currency.equals("£")) currency = "GBP";
            
            String cleanAmount = amount.replaceAll("[,\\s]", "");
            return currency + cleanAmount;
        }
        return "unknownPrice";
    }
    
    private static String extractTripId(String url) {
        if (url == null) return null;
        
        Pattern pathPattern = Pattern.compile("/trips/([a-zA-Z0-9-]+)");
        Matcher pathMatcher = pathPattern.matcher(url);
        if (pathMatcher.find()) {
            String id = pathMatcher.group(1);
            if (!id.equals("trips")) {
                return id;
            }
        }
        
        Pattern queryPattern = Pattern.compile("jobId=([a-zA-Z0-9-]+)");
        Matcher queryMatcher = queryPattern.matcher(url);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        
        return null;
    }
}

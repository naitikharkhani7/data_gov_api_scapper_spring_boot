package com.datagov.scrapper.controller;

import com.datagov.scrapper.service.DataGovScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class WebViewController {

    private final DataGovScraperService scraperService;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("activeTab", "dashboard");
        model.addAttribute("pageTitle", "Scraper Studio");
        model.addAttribute("pageSubtitle", "Configure parameters, run automated scrapers & monitor real-time telemetry");
        model.addAttribute("sectors", scraperService.getSectors());
        return "dashboard";
    }

    @GetMapping("/catalog")
    public String catalog(Model model) {
        model.addAttribute("activeTab", "catalog");
        model.addAttribute("pageTitle", "Scraped API Catalog");
        model.addAttribute("pageSubtitle", "Search, inspect, copy cURL, and test all APIs stored in local H2 database");
        model.addAttribute("sectors", scraperService.getSectors());
        return "catalog";
    }

    @GetMapping("/tester")
    public String tester(Model model) {
        model.addAttribute("activeTab", "tester");
        model.addAttribute("pageTitle", "Live API Runner & cURL Test Bench");
        model.addAttribute("pageSubtitle", "Execute direct requests against data.gov.in with custom headers & formats");
        return "tester";
    }

    @GetMapping("/export")
    public String export(Model model) {
        model.addAttribute("activeTab", "export");
        model.addAttribute("pageTitle", "Data Export & Utility Scripts");
        model.addAttribute("pageSubtitle", "Download entire datasets in JSON/CSV and access standalone Python scrapers");
        return "export";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("activeTab", "settings");
        model.addAttribute("pageTitle", "Application Settings");
        model.addAttribute("pageSubtitle", "Manage global configuration, API keys, and application preferences");
        return "settings";
    }
}

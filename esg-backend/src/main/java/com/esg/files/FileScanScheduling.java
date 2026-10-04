package com.esg.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Retries virus scans of files left pending (S6). Off in the test profile (tests call runOnce()). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "esg.files.rescan.enabled", havingValue = "true")
public class FileScanScheduling {

    private static final Logger log = LoggerFactory.getLogger(FileScanScheduling.class);

    private final VirusScanService scans;

    public FileScanScheduling(VirusScanService scans) {
        this.scans = scans;
    }

    @Scheduled(fixedDelayString = "${esg.files.rescan.interval-ms}", initialDelayString = "${esg.files.rescan.interval-ms}")
    void rescan() {
        VirusScanService.ScanRun run = scans.runOnce();
        if (run.tenants() > 0) {
            log.info("Virus rescan: {} tenants, {} clean, {} quarantined, {} still pending", run.tenants(), run.clean(),
                    run.infected(), run.stillPending());
        }
    }
}

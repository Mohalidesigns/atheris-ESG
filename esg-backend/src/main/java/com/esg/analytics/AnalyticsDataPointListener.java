package com.esg.analytics;

import com.esg.datahub.DataPointEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Scans a data point once its approval is committed (D34 (3)); a failed scan never undoes the approval. */
@Component
public class AnalyticsDataPointListener {

    private final AnomalyService anomalies;

    public AnalyticsDataPointListener(AnomalyService anomalies) {
        this.anomalies = anomalies;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApproved(DataPointEvents.Approved e) {
        anomalies.scanPoint(e.dataPointId());
    }
}

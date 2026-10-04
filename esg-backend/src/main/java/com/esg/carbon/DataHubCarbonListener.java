package com.esg.carbon;

import com.esg.datahub.DataPointEvents;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** TRD §6.5.1: approved Data Hub activity data becomes an emission record; reopening the point removes it. */
@Component
public class DataHubCarbonListener {

    private final EmissionService emissions;

    public DataHubCarbonListener(EmissionService emissions) {
        this.emissions = emissions;
    }

    @EventListener
    public void onApproved(DataPointEvents.Approved event) {
        emissions.fromApprovedDataPoint(event.dataPointId());
    }

    @EventListener
    public void onReopened(DataPointEvents.Reopened event) {
        emissions.removeForDataPoint(event.dataPointId());
    }
}

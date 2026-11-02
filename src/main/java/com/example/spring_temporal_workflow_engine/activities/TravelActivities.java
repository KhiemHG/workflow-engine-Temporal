package com.example.spring_temporal_workflow_engine.activities;

import com.example.spring_temporal_workflow_engine.dto.TravelRequest;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TravelActivities {
    public void bookFlight(TravelRequest travelRequest);
    public void bookHotel(TravelRequest travelRequest);
    public void arrangeTransport(TravelRequest travelRequest);
    public void cancelTransport(TravelRequest travelRequest);
    public void cancelHotel(TravelRequest travelRequest);
    public void cancelFlight(TravelRequest travelRequest);
    public void confirmBooking(TravelRequest travelRequest);
    public void cancelBooking(TravelRequest travelRequest);
}

package com.example.spring_temporal_workflow_engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TravelRequest {

    private String userId;
    private String destination;
    private String travelDate;
}

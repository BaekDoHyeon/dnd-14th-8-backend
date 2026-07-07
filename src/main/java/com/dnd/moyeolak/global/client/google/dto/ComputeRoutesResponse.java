package com.dnd.moyeolak.global.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ComputeRoutesResponse(List<Route> routes) {

    private static final String TRANSIT_STEP = "TRANSIT";
    private static final String WALK_STEP = "WALK";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            String duration,
            Integer distanceMeters,
            List<Leg> legs,
            TravelAdvisory travelAdvisory
    ) {

        public int durationMinutes() {
            if (duration == null) {
                return 0;
            }
            return (int) Math.round(Double.parseDouble(duration.replace("s", "")) / 60.0);
        }

        public int safeDistanceMeters() {
            return distanceMeters != null ? distanceMeters : 0;
        }

        public int fareWon() {
            if (travelAdvisory == null || travelAdvisory.transitFare() == null
                    || travelAdvisory.transitFare().units() == null) {
                return 0;
            }
            try {
                return Integer.parseInt(travelAdvisory.transitFare().units());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public int transferCount() {
            long transitSteps = allSteps().stream()
                    .filter(step -> TRANSIT_STEP.equals(step.travelMode()))
                    .count();
            return (int) Math.max(0, transitSteps - 1);
        }

        public int walkDistanceMeters() {
            return allSteps().stream()
                    .filter(step -> WALK_STEP.equals(step.travelMode()))
                    .mapToInt(Step::safeDistanceMeters)
                    .sum();
        }

        private List<Step> allSteps() {
            if (legs == null) {
                return List.of();
            }
            return legs.stream()
                    .filter(leg -> leg.steps() != null)
                    .flatMap(leg -> leg.steps().stream())
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String travelMode, Integer distanceMeters) {

        public int safeDistanceMeters() {
            return distanceMeters != null ? distanceMeters : 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TravelAdvisory(TransitFare transitFare) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitFare(String currencyCode, String units) {
    }
}

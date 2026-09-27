package com.chris64233.spectrumcoordination.dto;

public record FrequencyResourceDto(Long id, double bandStartMhz, double bandEndMhz,
                                   double regionLatitude, double regionLongitude,
                                   double regionRadiusKm, int maxCoChannelUsers) {
}

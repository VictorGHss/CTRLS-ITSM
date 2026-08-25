package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeegowLockDto(
    Long id,
    @JsonProperty("lock_type_id") Long lockTypeId,
    @JsonProperty("date_start") String dateStart,
    @JsonProperty("date_end") String dateEnd,
    @JsonProperty("time_start") String timeStart,
    @JsonProperty("time_end") String timeEnd,
    @JsonProperty("holiday_id") Long holidayId,
    @JsonProperty("professional_id") Long professionalId,
    String description,
    @JsonProperty("week_day") List<String> weekDay,
    List<String> units
) {}

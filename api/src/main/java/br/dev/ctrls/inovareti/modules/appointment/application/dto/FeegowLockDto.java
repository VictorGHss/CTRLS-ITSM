package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FeegowLockDto(
    Long id,
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

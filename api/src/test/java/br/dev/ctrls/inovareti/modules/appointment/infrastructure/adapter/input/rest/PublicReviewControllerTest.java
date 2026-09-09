package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class PublicReviewControllerTest {

    private DoctorConfigurationRepository repository;
    private PublicReviewController controller;

    @BeforeEach
    void setUp() {
        this.repository = Mockito.mock(DoctorConfigurationRepository.class);
        this.controller = new PublicReviewController(this.repository);
    }

    @Test
    void redirectToGoogleReviewWithValidHttpsUrl() {
        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(105L)
                .googleReviewUrl("https://g.page/r/example-doctor/review")
                .build();

        when(repository.findById(105L)).thenReturn(Optional.of(config));

        ResponseEntity<Void> response = controller.redirectToGoogleReview("105");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://g.page/r/example-doctor/review", response.getHeaders().getLocation().toString());
    }

    @Test
    void redirectToGoogleReviewRejectsInsecureProtocolAndUsesFallback() {
        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(105L)
                .googleReviewUrl("javascript:alert(1)")
                .build();

        when(repository.findById(105L)).thenReturn(Optional.of(config));

        ResponseEntity<Void> response = controller.redirectToGoogleReview("105");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(PublicReviewController.CLINIC_FALLBACK_URL, response.getHeaders().getLocation().toString());
    }

    @Test
    void redirectToGoogleReviewRejectsHttpPlainAndUsesFallback() {
        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(105L)
                .googleReviewUrl("http://insecure.example.com/review")
                .build();

        when(repository.findById(105L)).thenReturn(Optional.of(config));

        ResponseEntity<Void> response = controller.redirectToGoogleReview("105");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(PublicReviewController.CLINIC_FALLBACK_URL, response.getHeaders().getLocation().toString());
    }

    @Test
    void redirectToGoogleReviewWithUnknownDoctorUsesFallback() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Void> response = controller.redirectToGoogleReview("999");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(PublicReviewController.CLINIC_FALLBACK_URL, response.getHeaders().getLocation().toString());
    }
}

package br.dev.ctrls.itsm.modules.finance.infrastructure.adapter.input;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.dev.ctrls.itsm.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.itsm.modules.finance.application.service.ContaAzulAutomationService;
import br.dev.ctrls.itsm.modules.finance.infrastructure.adapter.output.ContaAzulClient;

/**
 * Testes dos endpoints públicos do controller ContaAzul.
 * Cada teste inicializa localmente os mocks e o controller para evitar
 * métodos de setup globais não utilizados sinalizados pela IDE.
 */
class ContaAzulPublicEndpointsTest {

    private MockMvc mvc;
    private ContaAzulTokenService tokenService;

    @Test
    void authorizeRedirectsToContaAzul() throws Exception {
        // Inicializa mocks e controller localmente para este teste
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var properties = Mockito.mock(br.dev.ctrls.itsm.modules.finance.infrastructure.config.ContaAzulProperties.class);
        when(properties.getRedirectUri()).thenReturn("http://localhost/api/financeiro/callback");
        var frontendProperties = Mockito.mock(br.dev.ctrls.itsm.config.FrontendProperties.class);
        when(frontendProperties.getUrl()).thenReturn("http://localhost:5173/");

        var controller = new ContaAzulController(this.tokenService, client, automation, properties, frontendProperties);
        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(tokenService.buildAuthorizationUrl(eq("http://localhost/api/financeiro/callback"), anyString()))
            .thenReturn("https://contaazul.example/authorize?x=1");

        mvc.perform(get("/financeiro/contaazul/authorize"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://contaazul.example/authorize?x=1"));

        verify(tokenService).buildAuthorizationUrl(eq("http://localhost/api/financeiro/callback"), anyString());
    }

    @Test
    void callbackExchangesCodeAndRedirectsToFrontend() throws Exception {
        // Inicializa mocks e controller localmente para este teste
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var properties = Mockito.mock(br.dev.ctrls.itsm.modules.finance.infrastructure.config.ContaAzulProperties.class);
        when(properties.getRedirectUri()).thenReturn("http://localhost/api/financeiro/callback");
        var frontendProperties = Mockito.mock(br.dev.ctrls.itsm.config.FrontendProperties.class);
        when(frontendProperties.getUrl()).thenReturn("http://localhost:5173/");

        var controller = new ContaAzulController(this.tokenService, client, automation, properties, frontendProperties);
        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/financeiro/contaazul/callback").param("code", "abc"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:5173/financeiro?success=true"));

        verify(tokenService).exchangeAuthorizationCode("abc", "http://localhost/api/financeiro/callback");
    }

    @Test
    void callbackWithProviderErrorRedirectsToFrontendWithFailureParams() throws Exception {
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var properties = Mockito.mock(br.dev.ctrls.itsm.modules.finance.infrastructure.config.ContaAzulProperties.class);
        when(properties.getRedirectUri()).thenReturn("http://localhost/api/financeiro/callback");
        var frontendProperties = Mockito.mock(br.dev.ctrls.itsm.config.FrontendProperties.class);
        when(frontendProperties.getUrl()).thenReturn("http://localhost:5173/");

        var controller = new ContaAzulController(this.tokenService, client, automation, properties, frontendProperties);
        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/financeiro/contaazul/callback")
                .param("error", "access_denied")
                .param("error_description", "usuario negou"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:5173/financeiro?success=false&error=access_denied&error_description=usuario+negou"));

        verify(tokenService, never()).exchangeAuthorizationCode("abc", "http://localhost/api/financeiro/callback");
    }

    @Test
    void callbackWithInvalidStateRedirectsToFrontendWithFailureParams() throws Exception {
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var properties = Mockito.mock(br.dev.ctrls.itsm.modules.finance.infrastructure.config.ContaAzulProperties.class);
        when(properties.getRedirectUri()).thenReturn("http://localhost/api/financeiro/callback");
        var frontendProperties = Mockito.mock(br.dev.ctrls.itsm.config.FrontendProperties.class);
        when(frontendProperties.getUrl()).thenReturn("http://localhost:5173/");

        var controller = new ContaAzulController(this.tokenService, client, automation, properties, frontendProperties);
        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/financeiro/contaazul/callback")
                .param("code", "abc")
                .param("state", "unrecognized_state_token"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:5173/financeiro?success=false&error=invalid_oauth_state&error_description=Sess%C3%A3o+de+autoriza%C3%A7%C3%A3o+expirada+ou+inv%C3%A1lida."));

        verify(tokenService, never()).exchangeAuthorizationCode(anyString(), anyString());
    }
}



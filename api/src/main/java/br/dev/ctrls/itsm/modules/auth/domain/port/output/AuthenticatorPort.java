package br.dev.ctrls.itsm.modules.auth.domain.port.output;

import br.dev.ctrls.itsm.modules.user.domain.model.User;

public interface AuthenticatorPort {
    User authenticate(String email, String password);
}

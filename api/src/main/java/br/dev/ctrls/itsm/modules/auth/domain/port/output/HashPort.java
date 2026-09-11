package br.dev.ctrls.itsm.modules.auth.domain.port.output;

public interface HashPort {
    String encode(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

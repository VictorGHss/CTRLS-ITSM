package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.util.UUID;

/**
 * Representação selada de ações do motor de grupos do Blip (Java 21 Sealed Interface).
 * Permite modelagem fechada de intenções e casamento de padrões exaustivo com Pattern Matching.
 */
public sealed interface BlipGroupAction {

    record ConfirmGroup(UUID groupId) implements BlipGroupAction {}

    record AlterGroup(UUID groupId) implements BlipGroupAction {}

    record ViewSchedule(UUID groupId) implements BlipGroupAction {}

    record GroupView(UUID groupId) implements BlipGroupAction {}

    record GroupViewFallback(UUID groupId) implements BlipGroupAction {}

    record GroupHelp() implements BlipGroupAction {}
}

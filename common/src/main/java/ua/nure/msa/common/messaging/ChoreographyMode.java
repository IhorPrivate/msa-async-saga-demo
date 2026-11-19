package ua.nure.msa.common.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Бін активний у режимі хореографії (режим за замовчуванням). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "saga.mode", havingValue = "choreography", matchIfMissing = true)
public @interface ChoreographyMode {
}

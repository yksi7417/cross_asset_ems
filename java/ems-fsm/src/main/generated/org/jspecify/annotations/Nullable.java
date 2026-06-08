// Minimal @Nullable stub for compilation without a full null-annotations library.
// Replace with org.jspecify or javax.annotation if available.
package org.jspecify.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
public @interface Nullable {}

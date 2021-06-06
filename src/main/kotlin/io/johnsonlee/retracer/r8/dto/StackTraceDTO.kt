package io.johnsonlee.retracer.r8.dto

import com.fasterxml.jackson.annotation.JsonInclude
import javax.validation.constraints.NotBlank

data class StackTraceDTO(
        @field:NotBlank
        val stackTrace: String,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        val fingerprint: String? = null,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        val rootCause: String? = null
)

package io.johnsonlee.retracer.r8.controller

import io.johnsonlee.retracer.REGEXP_SEMVER
import io.johnsonlee.retracer.r8.dto.StackTraceDTO
import io.johnsonlee.retracer.r8.service.ProguardService
import io.johnsonlee.retracer.r8.service.RetraceService
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern

@Api("Retrace")
@RestController
@RequestMapping("/api/retrace/r8")
@Validated
class RetraceController(
        @Autowired private val retraceService: RetraceService,
        @Autowired private val proguardService: ProguardService
) {

    @PostMapping("/{appId}")
    fun retrace(
            @NotBlank
            @PathVariable("appId")
            appId: String,

            @Pattern(regexp = REGEXP_SEMVER)
            @RequestParam("appVersionName")
            appVersionName: String,

            @Min(1L)
            @RequestParam("appVersionCode")
            appVersionCode: Long,

            @Valid
            @RequestBody
            stackTraces: List<StackTraceDTO>
    ): List<StackTraceDTO> = stackTraces.map {
        retraceService.retrace(appId, appVersionName, appVersionCode, it)
    }

}
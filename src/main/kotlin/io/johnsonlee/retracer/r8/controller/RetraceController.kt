package io.johnsonlee.retracer.r8.controller

import com.android.tools.r8.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.johnsonlee.retracer.REGEXP_SEMVER
import io.johnsonlee.retracer.r8.service.ProguardService
import io.johnsonlee.retracer.r8.service.RetraceService
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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

    @GetMapping("/")
    fun version(): Any {
        return mapOf("version" to Version.getVersionString())
    }

    @PostMapping("/{appId}/{appVersionName}/{appVersionCode}")
    fun retrace(
            @NotBlank
            @PathVariable("appId")
            appId: String,

            @Pattern(regexp = REGEXP_SEMVER)
            @PathVariable("appVersionName")
            appVersionName: String,

            @Min(1L)
            @PathVariable("appVersionCode")
            appVersionCode: Long,

            @RequestBody
            body: ObjectNode
    ): JsonNode = retraceService.retrace(appId, appVersionName, appVersionCode, body)

}
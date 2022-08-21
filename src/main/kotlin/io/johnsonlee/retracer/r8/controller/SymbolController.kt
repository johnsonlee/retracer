package io.johnsonlee.retracer.r8.controller

import io.johnsonlee.retracer.REGEXP_SEMVER
import io.johnsonlee.retracer.r8.service.ProguardService
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern

@Api("Symbol Management")
@Controller
@RestController
@RequestMapping("/api/symbol/r8")
@Validated
class SymbolController(
        @Autowired private val proguardService: ProguardService
) {

    @PostMapping("/{appId}/{appVersionName}/{appVersionCode}")
    fun upload(
            @NotBlank
            @PathVariable("appId")
            appId: String,

            @Pattern(regexp = REGEXP_SEMVER)
            @PathVariable("appVersionName")
            appVersionName: String,

            @Min(1L)
            @PathVariable("appVersionCode")
            appVersionCode: Long,

            @RequestParam("file")
            file: MultipartFile
    ): Map<*, *> {
        proguardService.saveMapping(appId, appVersionName, appVersionCode, file::transferTo)
        return mapOf(
                "appId" to appId,
                "appVersionName" to appVersionName,
                "appVersionCode" to appVersionCode,
                "file" to mapOf(
                        "name" to file.name,
                        "size" to file.size
                )
        )
    }

}
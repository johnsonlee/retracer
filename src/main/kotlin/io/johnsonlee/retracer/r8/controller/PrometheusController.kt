package io.johnsonlee.retracer.r8.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/metrics")
class PrometheusController {

    @RequestMapping("")
    fun getMetrics(
            request: HttpServletRequest,
            response: HttpServletResponse
    ) = request.getRequestDispatcher("/actuator/prometheus").forward(request, response)

}

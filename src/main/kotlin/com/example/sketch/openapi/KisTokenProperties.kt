package com.example.sketch.openapi

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.kis.token")
class KisTokenProperties {
    var refreshBeforeExpiry: Duration = Duration.ofMinutes(10)
    var fallbackTtl: Duration = Duration.ofHours(23)
    var persistence: Persistence = Persistence()

    class Persistence {
        var enabled: Boolean = false
        var type: String = "file"
        var file: String = ""
    }
}

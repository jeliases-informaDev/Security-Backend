package com.security.modules.denuncias.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "storage")
class ArchivoStorageProperties {

    var provider: String = "local"

    var local: Local = Local()

    var oracle: Oracle = Oracle()

    class Local {
        var basePath: String = "./storage"
    }

    class Oracle {
        var region: String = ""
        var namespace: String = ""
        var bucket: String = ""

        var authMode: String = "CONFIG_FILE"

        var configFile: String = ""

        var profile: String = "DEFAULT"
    }
}
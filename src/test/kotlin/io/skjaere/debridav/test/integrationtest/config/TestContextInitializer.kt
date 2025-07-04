package io.skjaere.debridav.test.integrationtest.config

import org.apache.commons.io.FileUtils
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.slf4j.event.Level
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.test.util.TestSocketUtils
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

class TestContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        const val BASE_PATH = "/tmp/debridavtests"
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withUsername("postgres")
                .withPassword("postgres")
                .withDatabaseName("debridav")
    }

    init {
        postgreSQLContainer.start()
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val port = TestSocketUtils.findAvailableTcpPort()
        val mockserverConfig = Configuration.configuration().logLevel(Level.ERROR)
        val mockServer: ClientAndServer = startClientAndServer(mockserverConfig, port)
        FileUtils.deleteDirectory(File(BASE_PATH))
        (applicationContext as ConfigurableApplicationContext).beanFactory.registerSingleton("mockServer", mockServer)
        applicationContext.addApplicationListener(
            ApplicationListener<ContextClosedEvent>() {
                mockServer.stop()
                FileUtils.deleteDirectory(File(BASE_PATH))
            }
        )
        TestPropertyValues.of(
            "premiumize.baseurl=http://localhost:$port/premiumize",
            "realdebrid.baseurl=http://localhost:$port/realdebrid",
            "sonarr.host=localhost",
            "sonarr.port=$port",
            "sonarr.api-base-path=/sonarr/api/v3",
            "radarr.host=localhost",
            "radarr.port=$port",
            "radarr.api-base-path=/radarr/api/v3",
            "mockserver.port=$port",
            "spring.datasource.url=${postgreSQLContainer.jdbcUrl}",
            "spring.datasource.username=postgres",
            "spring.datasource.password=postgres",
            "easynews.api-base-url=http://localhost:$port/easynews"
        ).applyTo(applicationContext)
    }
}

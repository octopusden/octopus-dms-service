import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.maven.monitor.logging.DefaultLog
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO
import org.octopusden.octopus.dms.client.validation.ArtifactValidator
import java.nio.file.Files
import kotlin.io.path.toPath

class ArtifactValidatorTest {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun test() {
        val validationProperties = mapper.readValue(
            this::class.java.getResourceAsStream("validation-configuration.json"),
            ValidationPropertiesDTO::class.java,
        )
        val file = this::class.java
            .getResource("distribution.zip")!!
            .toURI()
            .toPath()
        assertEquals(
            Files.readAllLines(
                this::class.java
                    .getResource("validation-result.txt")!!
                    .toURI()
                    .toPath(),
            ),
            ArtifactValidator.validate(DefaultLog(ConsoleLogger()), validationProperties, file.fileName.toString(), file),
        )
    }

    @Test
    fun validateRpmWithCyrillicFilenames() {
        val validationProperties = mapper.readValue(
            this::class.java.getResourceAsStream("validation-configuration.json"),
            ValidationPropertiesDTO::class.java,
        )
        val file = this::class.java
            .getResource("distribution-1.0-1.noarch.rpm")!!
            .toURI()
            .toPath()
        val errors = ArtifactValidator.validate(
            DefaultLog(ConsoleLogger()),
            validationProperties,
            file.fileName.toString(),
            file,
        )
        assertEquals(emptyList<String>(), errors)
    }
}

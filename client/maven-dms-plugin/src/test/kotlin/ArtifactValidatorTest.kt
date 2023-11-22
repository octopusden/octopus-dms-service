import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.octopusden.octopus.dms.client.common.dto.ValidationPropertiesDTO
import org.octopusden.octopus.dms.client.validation.ArtifactValidator
import java.nio.file.Files
import kotlin.io.path.toPath
import org.apache.maven.monitor.logging.DefaultLog
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ArtifactValidatorTest {
    @Test
    fun test() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val validationProperties = mapper.readValue(
            this::class.java.getResourceAsStream("validation-configuration.json"), ValidationPropertiesDTO::class.java
        )
        val file = this::class.java.getResource("distribution.zip")!!.toURI().toPath()
        Assertions.assertEquals(
            Files.readAllLines(this::class.java.getResource("validation-result.txt")!!.toURI().toPath()),
            ArtifactValidator.validate(DefaultLog(ConsoleLogger()), validationProperties, file.fileName.toString(), file)
        )
    }
}
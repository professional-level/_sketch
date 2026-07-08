import com.example.architecture.HexagonalArchitectureModule
import com.example.architecture.HexagonalArchitectureRules
import org.junit.jupiter.api.Test

class ArchUnitTest {

    @Test
    fun `module follows hexagonal architecture rules`() {
        HexagonalArchitectureRules.verify(
            HexagonalArchitectureModule(
                rootPackage = "com.example.streamprocessingservice",
                enforceDomainIsolation = false,
                enforceDomainFrameworkIsolation = false,
            ),
        )
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "_sketch"
include("stock-service")
include("common")
include("stock-search-service")
include("grpc-proficiency")
include("grpc-proficiency-client")
include("grpc-proficiency:grpc-temp")
findProject(":grpc-proficiency:grpc-temp")?.name = "grpc-temp"

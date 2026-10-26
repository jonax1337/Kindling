// The benchmark plugin compiles against the server itself (Mojang names, as at runtime), not against a dev bundle.
val leafServer = evaluationDependsOn(":leaf-server")

dependencies {
    compileOnly(project(":leaf-server"))
    compileOnly(files(leafServer.configurations.named("compileClasspath")))
}

tasks.jar {
    archiveFileName = "kindling-bench.jar"
}

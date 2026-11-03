// Settings for the development server (`./gradlew :webApp:wasmJsBrowserDevelopmentRun` and
// `:webApp:jsBrowserDevelopmentRun`). Kotlin's Gradle plugin applies every file in this directory to
// its generated webpack config. Production builds don't use the dev server.
if (config.devServer) {
    // The Ktor server uses 8080, so the dev server moves aside.
    config.devServer.port = 8081;

    // Answer every unknown path (e.g. /invite/<token>) with index.html, so deep links and page
    // reloads work. The app picks the screen from the path.
    config.devServer.historyApiFallback = true;

    // Forward API calls to the local Ktor server. The browser sees a single origin, so it sends the
    // admin session cookie, and no CORS setup is needed.
    config.devServer.proxy = [
        {
            context: ["/api"],
            target: "http://localhost:8080",
        },
    ];
}

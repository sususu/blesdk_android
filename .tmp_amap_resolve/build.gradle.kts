plugins { id("java") }
repositories { mavenCentral(); maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }; maven { url = uri("https://maven.aliyun.com/repository/public") } }
configurations { amap }
dependencies { amap("com.amap.api:3dmap:9.8.3") }
tasks.register("resolveAmap") { doLast { configurations.amap.resolve().each { println it } } }

# Changelog

## 2.0.0

Breaking change. Public Java types moved from `com.orange.igr.easydebug` to `io.github.abdessattar23.easydebug`, including `starter`, `server`, and `demo`. There is no compatibility shim. Classes compiled against `1.0.0` do not link against `2.0.0`.

Maven coordinates for the starter:

```xml
<groupId>io.github.abdessattar23</groupId>
<artifactId>easydebug-spring-boot-starter</artifactId>
<version>2.0.0</version>
```

`io.github.abdessattar23:easydebug-spring-boot-starter:1.0.0` stays on Maven Central with the old packages. `2.0.0` does not replace that artifact.

The collector listens on `127.0.0.1` unless `EASYDEBUG_SERVER_ADDRESS` is set. The event API has no authentication. A Central upload with the `central` profile waits until validation (`autoPublish` false, `waitUntil` validated) and does not publish by itself.

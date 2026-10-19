package com.orange.igr.easydebug.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DefaultDatabaseStartupTest {

    private static final String FILE = "target/h2-startup/easydebug";

    @DynamicPropertySource
    static void fileDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:./" + FILE + ";DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shippedUrlOmitsTheUnsupportedH2Combination() throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        assertThat(configuration).contains("jdbc:h2:file:./data/easydebug;DB_CLOSE_ON_EXIT=FALSE");
        assertThat(configuration).doesNotContain("AUTO_SERVER");
    }

    @Test
    void contextStartsAndSchemaSurvivesReopen(@TempDir Path directory) throws Exception {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM capture_event", Integer.class);
        assertThat(rows).isNotNull();

        String url = "jdbc:h2:file:" + directory.resolve("easydebug").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE kept (id INT PRIMARY KEY)");
            statement.execute("INSERT INTO kept (id) VALUES (1)");
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id FROM kept")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
        assertThat(Files.exists(directory.resolve("easydebug.mv.db"))).isTrue();
    }

    @Test
    void h2RejectsAutoServerWithCloseOnExitFalse(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("bad").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE";
        assertThatThrownBy(() -> DriverManager.getConnection(url, "sa", ""))
                .hasMessageContaining("AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE");
    }

    @Test
    void defaultStyleUrlOpensThroughSpring() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains("jdbc:h2:file:");
            assertThat(connection.getMetaData().getURL()).doesNotContain("AUTO_SERVER");
            assertThat(connection.isValid(2)).isTrue();
        }
    }
}

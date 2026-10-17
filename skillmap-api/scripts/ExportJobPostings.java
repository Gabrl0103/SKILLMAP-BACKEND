import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Herramienta de un solo uso: exporta SOLO la tabla job_postings de una COPIA de la base H2 a
 * src/main/resources/seed/job-postings.json (lo importa JobPostingSeeder en el primer arranque).
 * Abre la base en solo lectura y nunca exporta usuarios, sesiones, progreso ni ajustes.
 * Se niega a abrir la base real de %APPDATA%\SkillMap.
 *
 * Desde la carpeta skillmap-api (PowerShell; H2 y Jackson son los del jar de la app):
 * <pre>
 * $m2 = "$env:USERPROFILE\.m2\repository"
 * $cp = "$m2\com\h2database\h2\2.2.224\h2-2.2.224.jar;" +
 *       "$m2\com\fasterxml\jackson\core\jackson-databind\2.17.2\jackson-databind-2.17.2.jar;" +
 *       "$m2\com\fasterxml\jackson\core\jackson-core\2.17.2\jackson-core-2.17.2.jar;" +
 *       "$m2\com\fasterxml\jackson\core\jackson-annotations\2.17.2\jackson-annotations-2.17.2.jar"
 * java -cp $cp scripts\ExportJobPostings.java C:\Temp\skillmap-copia\skillmap.mv.db src\main\resources\seed\job-postings.json
 * </pre>
 */
public class ExportJobPostings {

    private static final String QUERY = "select source, external_id, title, company, location, remote, tags, url,"
            + " description, published_at, fetched_at from job_postings order by published_at desc, id desc";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Uso: ExportJobPostings <copia\\skillmap.mv.db> <salida.json>");
            System.exit(2);
        }
        Path dbFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        String appData = System.getenv("APPDATA");
        if (appData != null && dbFile.startsWith(Path.of(appData, "SkillMap").toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Esa es la base real de la app: exporta desde una copia.");
        }
        if (!Files.isRegularFile(dbFile) || !dbFile.toString().endsWith(".mv.db")) {
            throw new IllegalArgumentException("No es un archivo .mv.db: " + dbFile);
        }

        String base = dbFile.toString().substring(0, dbFile.toString().length() - ".mv.db".length());
        // Solo lectura, sin crear la base si no existe y sin archivo .trace.db.
        String url = "jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE;TRACE_LEVEL_FILE=0";
        List<Map<String, Object>> postings = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = connection.createStatement().executeQuery(QUERY)) {
            while (rs.next()) {
                Map<String, Object> posting = new LinkedHashMap<>();
                posting.put("source", rs.getString("source"));
                posting.put("externalId", rs.getString("external_id"));
                posting.put("title", rs.getString("title"));
                posting.put("company", rs.getString("company"));
                posting.put("location", rs.getString("location"));
                posting.put("remote", rs.getBoolean("remote"));
                posting.put("tags", rs.getString("tags"));
                posting.put("url", rs.getString("url"));
                posting.put("description", rs.getString("description"));
                posting.put("publishedAt", instant(rs, "published_at"));
                posting.put("fetchedAt", instant(rs, "fetched_at"));
                postings.add(posting);
            }
        }

        // Saltos de línea \n en cualquier sistema: el archivo se versiona.
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        Files.createDirectories(output.getParent());
        new ObjectMapper().writer(printer).writeValue(output.toFile(), postings);
        System.out.println(postings.size() + " ofertas exportadas a " + output);
    }

    /** ISO-8601 en UTC (ej. 2026-10-02T12:00:00Z), o null. */
    private static String instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant().toString();
    }
}

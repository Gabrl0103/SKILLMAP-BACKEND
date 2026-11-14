package com.skillmap.api.domain.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sugerencias de habilidades por nombre de rol, sin IA: un diccionario de perfiles, cada uno con sus palabras clave
 * (en español y en inglés) y de 5 a 8 habilidades. Cubre los roles comunes de tecnología, datos, diseño, producto,
 * marketing digital y soporte. Para ampliarlo basta con agregar un {@link Profile} a {@link #PROFILES}.
 *
 * Coincidencia: el nombre del rol y las palabras clave se comparan en minúsculas, sin tildes y por palabras completas
 * ("UX" coincide con "Diseñador UX" pero no con "Luxemburgo"). Si coinciden varios perfiles gana el de la palabra
 * clave más larga (la más específica): "Backend Python" usa el perfil de Python y no el de backend genérico.
 *
 * Las habilidades se nombran como en el catálogo cuando existen ("Python", "SQL", "Estadística"): quien use el
 * diccionario las busca ahí por nombre; las que no estén se ofrecen como habilidades propias nuevas.
 */
public final class RoleSkillDictionary {

    public static final int MIN_SKILLS = 5;
    public static final int MAX_SKILLS = 8;

    /** Un perfil de rol: sus palabras clave y sus habilidades, de la más importante a la menos. */
    public record Profile(String id, List<String> keywords, List<String> skills) {
        public Profile {
            keywords = keywords.stream().map(RoleSkillDictionary::normalize).toList();
            skills = List.copyOf(skills);
            if (skills.size() < MIN_SKILLS || skills.size() > MAX_SKILLS) {
                throw new IllegalArgumentException("El perfil " + id + " debe tener entre 5 y 8 habilidades");
            }
        }
    }

    /** Resultado de una búsqueda: el perfil y la palabra clave que coincidió. */
    public record Match(Profile profile, String keyword) {
    }

    static final List<Profile> PROFILES = List.of(
            // ---------- Desarrollo ----------
            new Profile("backend-python",
                    List.of("backend python", "python backend", "desarrollador python", "programador python",
                            "python developer", "django", "fastapi", "flask"),
                    List.of("Python", "SQL", "Docker", "Testing", "APIs REST", "Django", "CI/CD", "AWS")),
            new Profile("backend-java",
                    List.of("backend java", "java backend", "desarrollador java", "programador java", "java developer",
                            "spring"),
                    List.of("Java", "Spring Boot", "SQL", "Docker", "Testing", "APIs REST", "CI/CD")),
            new Profile("backend-node",
                    List.of("node", "nodejs", "node js", "backend javascript", "backend typescript", "express", "nestjs"),
                    List.of("TypeScript", "Node.js", "SQL", "APIs REST", "Docker", "Testing")),
            new Profile("backend",
                    List.of("backend", "back end", "desarrollador backend", "backend developer", "api developer"),
                    List.of("SQL", "APIs REST", "Docker", "Testing", "Java", "Python", "CI/CD", "Git")),
            new Profile("frontend",
                    List.of("frontend", "front end", "desarrollador web", "web developer", "react developer", "angular",
                            "vue", "maquetador"),
                    List.of("React", "TypeScript", "HTML y CSS", "Tailwind CSS", "Next.js", "Testing",
                            "Accesibilidad web")),
            new Profile("fullstack",
                    List.of("fullstack", "full stack", "desarrollador full stack"),
                    List.of("React", "TypeScript", "Node.js", "SQL", "APIs REST", "Docker", "Testing", "CI/CD")),
            new Profile("mobile",
                    List.of("movil", "mobile", "apps moviles", "desarrollador de apps", "app developer", "react native",
                            "flutter"),
                    List.of("React Native", "Kotlin", "Swift", "TypeScript", "Testing", "CI/CD")),
            new Profile("android",
                    List.of("android", "desarrollador android", "android developer"),
                    List.of("Kotlin", "Java", "Jetpack Compose", "Testing", "CI/CD", "Git")),
            new Profile("ios",
                    List.of("ios", "desarrollador ios", "ios developer", "swiftui"),
                    List.of("Swift", "SwiftUI", "Xcode", "Testing", "CI/CD", "Git")),
            new Profile("game",
                    List.of("videojuegos", "desarrollador de videojuegos", "game developer", "game dev", "unity",
                            "unreal"),
                    List.of("C#", "Unity", "Programación orientada a objetos", "Matemáticas para videojuegos", "Git",
                            "Testing")),
            new Profile("qa",
                    List.of("qa", "tester", "quality assurance", "calidad de software", "test automation",
                            "automatizacion de pruebas", "analista de pruebas", "qa engineer"),
                    List.of("Testing", "Automatización de pruebas", "Pruebas de API", "CI/CD", "Python", "TypeScript",
                            "SQL")),
            // ---------- Infraestructura y seguridad ----------
            new Profile("devops",
                    List.of("devops", "sre", "site reliability", "platform engineer", "ingeniero de plataforma",
                            "cloud engineer", "ingeniero cloud", "infraestructura"),
                    List.of("Docker", "Kubernetes", "Terraform", "AWS", "CI/CD", "Linux", "Python", "Monitoreo")),
            new Profile("cloud-architect",
                    List.of("arquitecto cloud", "cloud architect", "arquitecto de soluciones", "solutions architect",
                            "arquitecto de software", "software architect"),
                    List.of("AWS", "Terraform", "Kubernetes", "Docker", "CI/CD", "Arquitectura de software",
                            "Seguridad en la nube")),
            new Profile("security",
                    List.of("ciberseguridad", "seguridad informatica", "seguridad de la informacion", "security",
                            "cybersecurity", "pentester", "hacker etico", "analista soc", "soc analyst"),
                    List.of("Redes", "Linux", "Gestión de vulnerabilidades", "Python", "Seguridad en la nube", "AWS",
                            "Respuesta a incidentes")),
            new Profile("sysadmin",
                    List.of("administrador de sistemas", "sysadmin", "system administrator", "administrador de redes",
                            "network administrator", "ingeniero de redes"),
                    List.of("Linux", "Redes", "Bash", "Docker", "Virtualización", "Seguridad informática")),
            new Profile("dba",
                    List.of("dba", "administrador de bases de datos", "database administrator", "base de datos",
                            "bases de datos"),
                    List.of("SQL", "PostgreSQL", "Modelado de datos", "Optimización de consultas",
                            "Copias de seguridad", "Linux")),
            new Profile("support",
                    List.of("soporte", "soporte tecnico", "help desk", "helpdesk", "mesa de ayuda", "service desk",
                            "it support", "technical support", "tecnico de sistemas", "tecnico informatico"),
                    List.of("Atención al cliente", "Resolución de incidencias", "Redes", "Windows", "Linux", "ITIL",
                            "Hardware")),
            // ---------- Datos ----------
            new Profile("data-scientist",
                    List.of("data scientist", "cientifico de datos", "ciencia de datos", "data science"),
                    List.of("Python", "Machine Learning", "Pandas", "Estadística", "SQL", "Visualización de datos")),
            new Profile("data-analyst",
                    List.of("analista de datos", "data analyst", "business intelligence", "analista bi", "bi analyst",
                            "analytics", "analista de negocio", "business analyst"),
                    List.of("SQL", "Excel", "Power BI", "Estadística", "Visualización de datos", "Python", "Pandas")),
            new Profile("data-engineer",
                    List.of("data engineer", "ingeniero de datos", "etl", "big data"),
                    List.of("Python", "SQL", "Apache Spark", "Airflow", "Modelado de datos", "Docker", "AWS")),
            new Profile("ml-engineer",
                    List.of("machine learning", "ml engineer", "inteligencia artificial", "ia", "ai", "ai engineer",
                            "deep learning", "mlops"),
                    List.of("Python", "Machine Learning", "Deep Learning", "Pandas", "Estadística", "Docker", "MLOps")),
            // ---------- Diseño ----------
            new Profile("ux",
                    List.of("ux", "experiencia de usuario", "user experience", "ux designer", "disenador ux",
                            "ux researcher", "investigador ux", "product designer", "disenador de producto",
                            "disenador ux ui", "ux ui"),
                    List.of("Figma", "Investigación de usuarios", "Prototipado", "Arquitectura de información",
                            "Pruebas de usabilidad", "Diseño de interacción", "Accesibilidad web")),
            new Profile("ui",
                    List.of("ui", "disenador ui", "ui designer", "diseno de interfaces", "visual designer",
                            "disenador web", "web designer"),
                    List.of("Figma", "Diseño visual", "Sistemas de diseño", "Tipografía", "Prototipado", "HTML y CSS",
                            "Accesibilidad web")),
            new Profile("graphic",
                    List.of("disenador grafico", "diseno grafico", "graphic designer", "ilustrador", "illustrator",
                            "branding"),
                    List.of("Adobe Photoshop", "Adobe Illustrator", "Tipografía", "Teoría del color", "Branding",
                            "Diseño editorial")),
            // ---------- Producto y gestión ----------
            new Profile("product",
                    List.of("product manager", "product owner", "gerente de producto", "dueno de producto",
                            "duena de producto", "producto digital", "product lead"),
                    List.of("Roadmap de producto", "Priorización", "Investigación de usuarios", "Análisis de métricas",
                            "Metodologías ágiles", "Comunicación con stakeholders", "SQL")),
            new Profile("project",
                    List.of("project manager", "jefe de proyecto", "gestor de proyectos", "gerente de proyectos",
                            "scrum master", "agile coach", "delivery manager"),
                    List.of("Metodologías ágiles", "Scrum", "Planificación", "Gestión de riesgos", "Jira",
                            "Comunicación con stakeholders")),
            // ---------- Marketing digital ----------
            new Profile("marketing",
                    List.of("marketing", "marketing digital", "digital marketing", "growth", "growth marketer",
                            "growth hacker", "performance marketing", "trafficker", "media buyer"),
                    List.of("SEO", "SEM", "Google Analytics", "Marketing de contenidos", "Email marketing",
                            "Redes sociales", "Análisis de métricas")),
            new Profile("seo",
                    List.of("seo", "especialista seo", "seo specialist", "posicionamiento web"),
                    List.of("SEO", "Investigación de palabras clave", "Google Analytics", "Marketing de contenidos",
                            "HTML y CSS", "Análisis de métricas")),
            new Profile("content",
                    List.of("community manager", "social media", "redes sociales", "creador de contenido",
                            "content creator", "content manager", "copywriter", "redactor"),
                    List.of("Redes sociales", "Marketing de contenidos", "Copywriting", "Planificación de contenidos",
                            "Análisis de métricas", "Canva"))
    );

    private RoleSkillDictionary() {
    }

    /** El perfil que mejor coincide con el nombre del rol; vacío si ninguno. */
    public static Optional<Match> find(String role) {
        String text = " " + normalize(role) + " ";
        if (text.isBlank()) {
            return Optional.empty();
        }
        Match best = null;
        for (Profile profile : PROFILES) {
            for (String keyword : profile.keywords()) {
                if (text.contains(" " + keyword + " ")
                        && (best == null || keyword.length() > best.keyword().length())) {
                    best = new Match(profile, keyword);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Las habilidades sugeridas para el rol (de 5 a 8); vacía si no coincide con ningún perfil. */
    public static List<String> suggest(String role) {
        return find(role).map(match -> match.profile().skills()).orElse(List.of());
    }

    public static List<Profile> profiles() {
        return PROFILES;
    }

    /** Minúsculas, sin tildes y solo letras y números separados por un espacio: "Diseñador UX/UI" → "disenador ux ui". */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}#+]+", " ").strip();
    }
}

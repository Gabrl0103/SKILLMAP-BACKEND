package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.CustomGoalConflictException;
import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.model.CustomGoal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de un objetivo propio:
 * - Nombre de 2 a 40 caracteres, texto plano (sin < > ni caracteres de control), sin espacios de sobra.
 * - No repite el nombre de un objetivo base ni de otro propio del usuario: se compara sin mayúsculas y sin espacios
 *   de sobra (nameKey).
 * - Descripción opcional de hasta 120 caracteres, también texto plano.
 * - De 3 a 8 habilidades sin repetir, sumando las elegidas (del catálogo o propias de este objetivo) y las nuevas
 *   que escribe el usuario (newSkills). Una habilidad nueva: de 2 a 40 caracteres, texto plano, sin espacios de sobra,
 *   y no puede repetir (sin mayúsculas) una del catálogo ni otra de este objetivo.
 * - Como mucho MAX_PER_USER por usuario.
 * Los largos se cuentan en caracteres (un emoji cuenta uno). Los mensajes nunca repiten lo recibido.
 */
public final class CustomGoalFactory {

    public static final int MAX_PER_USER = 3;
    public static final int MIN_NAME = 2;
    public static final int MAX_NAME = 40;
    public static final int MAX_DESCRIPTION = 120;
    public static final int MIN_SKILLS = 3;
    public static final int MAX_SKILLS = 8;

    static final String NAME = "El nombre del objetivo debe tener entre 2 y 40 caracteres.";
    static final String PLAIN = "Usa solo texto: sin los caracteres < ni >.";
    static final String DESCRIPTION = "La descripción puede tener como máximo 120 caracteres.";
    static final String SKILLS = "Elige entre 3 y 8 habilidades.";
    static final String REPEATED = "Cada habilidad puede elegirse una sola vez.";
    static final String UNKNOWN_SKILL = "Una de las habilidades elegidas no existe en el catálogo.";
    static final String DUPLICATE = "Ya tienes un objetivo con ese nombre. Elige otro.";
    static final String LIMIT = "Puedes tener como máximo 3 objetivos propios. Borra uno para crear otro.";
    static final String NEW_SKILL_NAME = "Cada habilidad propia debe tener entre 2 y 40 caracteres.";
    static final String IN_CATALOG = "Esa habilidad ya está en el catálogo: elígela de la lista.";

    public static final int MIN_SKILL_NAME = 2;
    public static final int MAX_SKILL_NAME = 40;

    /**
     * Un objetivo validado y las habilidades nuevas que hay que crear para él (nombres ya limpios, en el orden
     * recibido). goal.skillIds() lleva solo las que ya existían; las nuevas se agregan al final al crearlas.
     */
    public record Draft(CustomGoal goal, List<String> newSkills) {
        public Draft {
            newSkills = List.copyOf(newSkills);
        }
    }

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NOT_PLAIN = Pattern.compile("[<>\\p{Cntrl}]");

    private CustomGoalFactory() {
    }

    /**
     * Nuevo objetivo propio.
     * @param catalogSkillIds ids de todas las habilidades del catálogo
     * @param takenNames      nombres de los objetivos base y de los propios del usuario
     * @param ownCount        cuántos propios tiene ya el usuario
     */
    public static CustomGoal create(Long userId, String name, String description, List<Long> skillIds,
                                    Set<Long> catalogSkillIds, Collection<String> takenNames, int ownCount,
                                    Instant now) {
        return create(userId, name, description, skillIds, List.of(), names(catalogSkillIds), Map.of(), takenNames,
                ownCount, now).goal();
    }

    /**
     * Nuevo objetivo propio con habilidades del catálogo y nuevas escritas por el usuario.
     * @param catalog    id → nombre de todas las habilidades del catálogo
     * @param ownOfGoal  id → nombre de las habilidades propias que ya tiene este objetivo (vacío al crear)
     */
    public static Draft create(Long userId, String name, String description, List<Long> skillIds,
                               List<String> newSkills, Map<Long, String> catalog, Map<Long, String> ownOfGoal,
                               Collection<String> takenNames, int ownCount, Instant now) {
        if (ownCount >= MAX_PER_USER) {
            throw new CustomGoalConflictException(LIMIT);
        }
        String cleanName = name(name, takenNames);
        String cleanDescription = description(description);
        List<Long> ids = skills(skillIds, newSkills, catalog, ownOfGoal);
        List<String> created = newSkills(newSkills, skillIds, catalog, ownOfGoal);
        return new Draft(new CustomGoal(null, Objects.requireNonNull(userId), cleanName, cleanDescription, ids, now,
                now), created);
    }

    /** El mismo objetivo con otros datos. takenNames no debe incluir el nombre actual de este objetivo. */
    public static CustomGoal edit(CustomGoal current, String name, String description, List<Long> skillIds,
                                  Set<Long> catalogSkillIds, Collection<String> takenNames, Instant now) {
        return edit(current, name, description, skillIds, List.of(), names(catalogSkillIds), Map.of(), takenNames,
                now).goal();
    }

    /** Editar con habilidades nuevas; ownOfGoal: las propias que este objetivo tiene hoy (se pueden conservar). */
    public static Draft edit(CustomGoal current, String name, String description, List<Long> skillIds,
                             List<String> newSkills, Map<Long, String> catalog, Map<Long, String> ownOfGoal,
                             Collection<String> takenNames, Instant now) {
        String cleanName = name(name, takenNames);
        String cleanDescription = description(description);
        List<Long> ids = skills(skillIds, newSkills, catalog, ownOfGoal);
        List<String> created = newSkills(newSkills, skillIds, catalog, ownOfGoal);
        return new Draft(current.withChanges(cleanName, cleanDescription, ids, now), created);
    }

    /** Nombre de una habilidad propia limpio y validado (2 a 40 caracteres, texto plano). */
    public static String skillName(String name) {
        String clean = collapse(name == null ? "" : name);
        int length = clean.codePointCount(0, clean.length());
        if (length < MIN_SKILL_NAME || length > MAX_SKILL_NAME) {
            throw new InvalidCustomGoalException(NEW_SKILL_NAME);
        }
        plain(clean);
        return clean;
    }

    /** Clave para comparar nombres: sin mayúsculas y con un solo espacio entre palabras. */
    public static String nameKey(String name) {
        return name == null ? "" : collapse(name).toLowerCase(Locale.ROOT);
    }

    private static String name(String name, Collection<String> takenNames) {
        String clean = collapse(name == null ? "" : name);
        int length = clean.codePointCount(0, clean.length());
        if (length < MIN_NAME || length > MAX_NAME) {
            throw new InvalidCustomGoalException(NAME);
        }
        plain(clean);
        String key = nameKey(clean);
        if (takenNames.stream().map(CustomGoalFactory::nameKey).anyMatch(key::equals)) {
            throw new CustomGoalConflictException(DUPLICATE);
        }
        return clean;
    }

    /** Vacía queda en null. */
    private static String description(String description) {
        String clean = collapse(description == null ? "" : description);
        if (clean.codePointCount(0, clean.length()) > MAX_DESCRIPTION) {
            throw new InvalidCustomGoalException(DESCRIPTION);
        }
        plain(clean);
        return clean.isEmpty() ? null : clean;
    }

    /** Las elegidas: del catálogo o propias de este objetivo, sin repetir; el total con las nuevas, de 3 a 8. */
    private static List<Long> skills(List<Long> skillIds, List<String> newSkills, Map<Long, String> catalog,
                                     Map<Long, String> ownOfGoal) {
        List<Long> chosen = skillIds == null ? List.of() : skillIds;
        int total = chosen.size() + (newSkills == null ? 0 : newSkills.size());
        if (total < MIN_SKILLS || total > MAX_SKILLS) {
            throw new InvalidCustomGoalException(SKILLS);
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : chosen) {
            if (id == null || !(catalog.containsKey(id) || ownOfGoal.containsKey(id))) {
                throw new InvalidCustomGoalException(UNKNOWN_SKILL);
            }
            if (!seen.add(id)) {
                throw new InvalidCustomGoalException(REPEATED);
            }
        }
        return List.copyOf(chosen);
    }

    /**
     * Las nuevas, limpias: no repiten una del catálogo (mejor elegir esa: tiene datos de mercado), ni una propia que
     * se conserva en este objetivo, ni otra nueva.
     */
    private static List<String> newSkills(List<String> newSkills, List<Long> skillIds, Map<Long, String> catalog,
                                          Map<Long, String> ownOfGoal) {
        if (newSkills == null || newSkills.isEmpty()) {
            return List.of();
        }
        Set<String> catalogKeys = new HashSet<>();
        catalog.values().forEach(n -> catalogKeys.add(nameKey(n)));
        Set<String> taken = new HashSet<>();
        if (skillIds != null) {
            skillIds.stream().map(ownOfGoal::get).filter(Objects::nonNull).forEach(n -> taken.add(nameKey(n)));
        }
        List<String> clean = new ArrayList<>();
        for (String raw : newSkills) {
            String name = skillName(raw);
            String key = nameKey(name);
            if (catalogKeys.contains(key)) {
                throw new InvalidCustomGoalException(IN_CATALOG);
            }
            if (!taken.add(key)) {
                throw new InvalidCustomGoalException(REPEATED);
            }
            clean.add(name);
        }
        return clean;
    }

    /** Para las llamadas sin nombres: ids del catálogo con un nombre vacío (no se comparan nombres). */
    private static Map<Long, String> names(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        ids.forEach(id -> map.put(id, ""));
        return map;
    }

    private static void plain(String text) {
        if (NOT_PLAIN.matcher(text).find()) {
            throw new InvalidCustomGoalException(PLAIN);
        }
    }

    private static String collapse(String text) {
        return SPACES.matcher(text.strip()).replaceAll(" ");
    }
}

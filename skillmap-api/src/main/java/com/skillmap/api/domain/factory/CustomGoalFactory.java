package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.CustomGoalConflictException;
import com.skillmap.api.domain.exception.InvalidCustomGoalException;
import com.skillmap.api.domain.model.CustomGoal;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de un objetivo propio:
 * - Nombre de 2 a 40 caracteres, texto plano (sin < > ni caracteres de control), sin espacios de sobra.
 * - No repite el nombre de un objetivo base ni de otro propio del usuario: se compara sin mayúsculas y sin espacios
 *   de sobra (nameKey).
 * - Descripción opcional de hasta 120 caracteres, también texto plano.
 * - De 3 a 8 habilidades del catálogo, sin repetir.
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
        if (ownCount >= MAX_PER_USER) {
            throw new CustomGoalConflictException(LIMIT);
        }
        String cleanName = name(name, takenNames);
        return new CustomGoal(null, Objects.requireNonNull(userId), cleanName, description(description),
                skills(skillIds, catalogSkillIds), now, now);
    }

    /** El mismo objetivo con otros datos. takenNames no debe incluir el nombre actual de este objetivo. */
    public static CustomGoal edit(CustomGoal current, String name, String description, List<Long> skillIds,
                                  Set<Long> catalogSkillIds, Collection<String> takenNames, Instant now) {
        String cleanName = name(name, takenNames);
        return current.withChanges(cleanName, description(description), skills(skillIds, catalogSkillIds), now);
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

    private static List<Long> skills(List<Long> skillIds, Set<Long> catalogSkillIds) {
        if (skillIds == null || skillIds.size() < MIN_SKILLS || skillIds.size() > MAX_SKILLS) {
            throw new InvalidCustomGoalException(SKILLS);
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : skillIds) {
            if (id == null || !catalogSkillIds.contains(id)) {
                throw new InvalidCustomGoalException(UNKNOWN_SKILL);
            }
            if (!seen.add(id)) {
                throw new InvalidCustomGoalException(REPEATED);
            }
        }
        return List.copyOf(skillIds);
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
